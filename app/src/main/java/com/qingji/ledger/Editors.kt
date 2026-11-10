@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingji.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.time.*

@Composable fun EditorSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit){
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start=22.dp,end=22.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(title,style=MaterialTheme.typography.titleLarge);content()}}
}
@Composable fun SubmitButton(label:String="保存",enabled:Boolean=true,done:()->Unit,task:suspend()->Unit){
    val scope=rememberCoroutineScope();var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    Button(onClick={busy=true;error=null;scope.launch{try{withContext(Dispatchers.IO){task()};done()}catch(e:Exception){if(e is CancellationException)throw e;error=e.message?:"操作失败，请重试"}finally{busy=false}}},enabled=enabled&&!busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(busy)"正在保存…" else label)}
}
@Composable fun EntryEditor(entry:Entry?,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit,onRefund:(Entry)->Unit){
    val context=LocalContext.current
    var type by rememberSaveable(entry?.id){mutableStateOf(entry?.type?:EXPENSE)}
    var amount by rememberSaveable(entry?.id){mutableStateOf(entry?.let{money(it.amount)}?:"")}
    var catId by rememberSaveable(entry?.id){mutableStateOf(entry?.categoryId?:s.categories.filter{it.type==EXPENSE&&!it.archived}.minByOrNull{it.sortOrder}?.id?:"")}
    var assetId by rememberSaveable(entry?.id){mutableStateOf(if(entry!=null)entry.assetId else s.defaultAssetId())}
    var affectsBalance by rememberSaveable(entry?.id){mutableStateOf(entry?.affectsBalance?:true)}
    var date by rememberSaveable(entry?.id){mutableStateOf(entry?.date?:LocalDate.now().toString())}
    var time by rememberSaveable(entry?.id){mutableStateOf(entry?.time?:LocalTime.now().withSecond(0).withNano(0).toString())}
    var note by rememberSaveable(entry?.id){mutableStateOf(entry?.note?:"")}
    var continueAdding by rememberSaveable{mutableStateOf(false)}
    var inBudget by rememberSaveable(entry?.id,key="inBudget-${entry?.id}"){mutableStateOf(entry?.let{it.id !in s.settings.budgetExcludedEntries}?:true)}
    var repayment by rememberSaveable(entry?.id,key="repayment-${entry?.id}"){mutableStateOf(entry?.let{it.id in s.settings.repaymentEntries}?:false)}
    val original=entry?.refundOf?.let{id->s.entries.find{it.id==id}}
    val usage=remember(s.entries){s.live().groupingBy{it.categoryId}.eachCount()}
    val categories=s.categories.filter{(it.type==if(type==INCOME)INCOME else EXPENSE) && (!it.archived||it.id==catId)}.sortedWith(compareByDescending<Category>{usage[it.id]?:0}.thenBy{it.sortOrder})
    var more by rememberSaveable(entry?.id){mutableStateOf(false)}
    val parsed=keypadMoney(amount)
    val after=if(assetId!=null&&parsed!=null&&affectsBalance){val changed=(entry?:Entry(type=type,amount=parsed,categoryId=catId)).copy(type=type,amount=parsed,assetId=assetId,affectsBalance=true);s.projectedBalance(assetId!!,entry?.takeIf{old->s.entries.any{it.id==old.id}},changed)}else null
    FastInputSheet(if(type==REFUND)"退款" else if(entry!=null)"编辑账单" else "记一笔",onDismiss,form={
        if(type!=REFUND)ChoiceRow(listOf("支出","收入"),if(type==INCOME)1 else 0){i->type=if(i==1)INCOME else EXPENSE;catId=s.categories.firstOrNull{it.type==type&&!it.archived}?.id?:""}
        else Row(verticalAlignment=Alignment.CenterVertically){Text(s.category(catId)?.name?:"原分类",Modifier.weight(1f));InfoHint("退款","关联原支出，按退款发生日冲减支出。"+(original?.let{"原支出 ¥"+money(it.amount)}?:""))}
        AmountDisplay(amount){amount=""}
        if(type!=REFUND)CategoryGrid(categories,catId){catId=it}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
            Box(Modifier.weight(1.2f)){CompactAssetChoice(if(type==EXPENSE)"付款账户" else "到账账户",s,assetId,{assetId=it})}
            OutlinedButton(onClick={pickDate(context,LocalDate.parse(date)){date=it.toString()}},modifier=Modifier.weight(1f)){Icon(Icons.Outlined.CalendarToday,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(if(date==LocalDate.now().toString())"今天" else date.substring(5),maxLines=1)}
        }
        if(type==EXPENSE)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
            FilterChip(repayment,onClick={repayment=!repayment;if(repayment)inBudget=false},label={Text("还款")})
            InfoHint("预算口径","还信用卡、还花呗等属于还款，默认不计入预算，避免与消费当期的支出重复计算。也可以只关闭右侧开关，用于报销、代付等非日常消费。账单仍计入本月收支与资产余额。")
            Spacer(Modifier.weight(1f))
            Text("计入预算",style=MaterialTheme.typography.labelMedium)
            Switch(inBudget,{value->inBudget=value;if(value)repayment=false})
        }
        if(type==EXPENSE&&(!inBudget||repayment))Hint(if(repayment)"还款不计入预算，收支统计与资产余额不受影响。" else "本笔不计入预算，收支统计与资产余额不受影响。")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            NoteAction(note){note=it};Spacer(Modifier.weight(1f))
            if(assetId!=null&&!affectsBalance)Text("仅关联",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={more=!more}){Text("更多");Icon(if(more)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null,Modifier.size(17.dp))}
        }
        if(after!=null&&after<0)Text("余额不足 · 保存后 ¥"+money(after),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
        if(more){
            if(assetId!=null){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("同步余额",Modifier.weight(1f));InfoHint("同步余额","旧账若已包含在当前余额中，请保持关闭，避免重复扣减。关闭时仅关联账户。新记账默认开启。");Switch(affectsBalance,{affectsBalance=it})};if(after!=null&&after>=0)Hint("保存后余额 ¥"+money(after))}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={pickTime(context,time){time=it}}){Icon(Icons.Outlined.Schedule,null,Modifier.size(17.dp));Spacer(Modifier.width(6.dp));Text(time)};Spacer(Modifier.weight(1f));if(entry==null){Text("连续记账",style=MaterialTheme.typography.labelMedium);Switch(continueAdding,{continueAdding=it})}}
            if(entry!=null&&s.entries.any{it.id==entry.id}){
                if(entry.type==EXPENSE){val refunded=s.live().filter{it.refundOf==entry.id}.sumOf{it.amount};TextButton(onClick={onRefund(entry)},enabled=refunded<entry.amount){Text(if(refunded==0L)"退款" else "继续退款 · 已退 ¥"+money(refunded))}}
            }
        }
    },footer={
        AmountKeypad{key->amount=keypadInput(amount,key)}
        SubmitButton(enabled=parsed!=null,done={vm.notify("已保存");if(continueAdding&&entry==null){amount="";note="";repayment=false;inBudget=true}else onDismiss()}){
            val value=requireNotNull(keypadMoney(amount)){"请输入有效金额"};require(catId.isNotBlank()){"请先添加分类"}
            val result=(entry?:Entry(type=type,amount=value,categoryId=catId)).copy(type=type,amount=value,categoryId=catId,date=date,time=time,note=note,assetId=assetId,affectsBalance=assetId!=null&&affectsBalance)
            vm.repo.save(result)
            vm.repo.setEntryBudgetFlags(result.id,if(type==EXPENSE)inBudget else true,type==EXPENSE&&repayment)
        }
    })
}

@Composable fun BudgetEditor(month:YearMonth,scopeId:String,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var scope by rememberSaveable{mutableStateOf(if(scopeId=="select")s.categories.firstOrNull{it.type==EXPENSE&&!it.archived}?.id?:"" else scopeId)}
    var amount by rememberSaveable{mutableStateOf(s.budget(month.toString(),scope)?.let{money(it.limit)}?:"")};var asDefault by rememberSaveable{mutableStateOf(false)}
    val excluded=remember(s.entries,s.settings,month){s.excludedBudgetEntries(s.live().filter{it.date.startsWith(month.toString())})}
    EditorSheet("${month.monthValue}月预算",onDismiss){
        if(scopeId=="select")FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){s.categories.filter{it.type==EXPENSE&&!it.archived}.forEach{c->FilterChip(c.id==scope,onClick={scope=c.id;amount=s.budget(month.toString(),scope)?.let{money(it.limit)}?:""},label={Text(c.name)})}}
        else Hint(if(scope.isEmpty())"总预算" else s.category(scope)?.name?:"分类预算")
        OutlinedTextField(amount,{if(it.length<=12)amount=it},Modifier.fillMaxWidth(),label={Text("预算金额（元）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        Row{Checkbox(asDefault,{asDefault=it});TextButton(onClick={asDefault=!asDefault}){Text("同时作为以后月份的默认预算")}}
        Hint("已有单独设置的月份不受默认预算影响。总预算与分类预算分别统计。")
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
        Heading("不计入预算的账单")
        Hint("在「记一笔」里标记还款，或关闭「计入预算」，即可让该笔不占用预算。账单仍计入本月收支与资产余额；总预算与分类预算使用同一口径。下面可把已排除的账单恢复计入，无需保存预算。")
        if(excluded.isEmpty())Hint("本月没有不计入预算的账单。")
        excluded.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.time}).take(20).forEach{e->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("${s.category(e.categoryId)?.name?:"其他"}  ¥${money(e.amount)}");Hint("${e.date.substring(5)} · ${if(s.isRepayment(e))"还款" else "手动排除"}${e.note.takeIf{it.isNotBlank()}?.let{" · $it"}?:""}")}
                TextButton(onClick={vm.action("已恢复计入预算"){vm.repo.setEntryInBudget(e.id,true)}}){Text("恢复计入")}
            }
        }
        if(excluded.size>20)Hint("仅显示最近 20 笔，本月共 ${excluded.size} 笔不计入预算。")
        SubmitButton(done={onDismiss();vm.notify("预算已保存")}){require(scopeId!="select"||scope.isNotBlank()){ "请选择分类" };vm.repo.saveBudget(month.toString(),scope,requireNotNull(parseMoney(amount)){"预算金额需大于0，最多两位小数"},asDefault)}
        if(s.budgets.any{it.period==month.toString()&&it.scope==scope})TextButton(onClick={vm.action("本月设置已移除"){vm.repo.dao.deleteBudget(month.toString(),scope);withContext(Dispatchers.Main){onDismiss()}}},modifier=Modifier.fillMaxWidth()){Text("移除本月单独预算（如有默认则沿用）")}
        if(s.budgets.any{it.period=="*"&&it.scope==scope})TextButton(onClick={vm.action("默认预算已移除"){vm.repo.dao.deleteBudget("*",scope);withContext(Dispatchers.Main){onDismiss()}}},modifier=Modifier.fillMaxWidth()){Text("移除该项默认预算")}
    }
}
@Composable fun GoalEditor(goal:Goal?,vm:LedgerViewModel,onDismiss:()->Unit){
    val context=LocalContext.current;var name by rememberSaveable{mutableStateOf(goal?.title?:"")};var target by rememberSaveable{mutableStateOf(goal?.let{money(it.target)}?:"")};var due by rememberSaveable{mutableStateOf(goal?.due)}
    EditorSheet(if(goal==null)"新建攒钱目标" else "编辑攒钱目标",onDismiss){
        OutlinedTextField(name,{if(it.length<=30)name=it},Modifier.fillMaxWidth(),label={Text("目标名称")},singleLine=true)
        OutlinedTextField(target,{if(it.length<=12)target=it},Modifier.fillMaxWidth(),label={Text("目标金额（元）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        OutlinedButton(onClick={pickDate(context,due?.let(LocalDate::parse)?:LocalDate.now().plusMonths(3),false){due=it.toString()}},modifier=Modifier.fillMaxWidth()){Text(due?.let{"目标日期 $it"}?:"设置目标日期（选填）")}
        if(due!=null)TextButton(onClick={due=null}){Text("不设截止日期")}
        SubmitButton(done={onDismiss();vm.notify("目标已保存")}){vm.repo.saveGoal((goal?:Goal(title=name.trim(),target=1)).copy(title=name.trim(),target=requireNotNull(parseMoney(target)){"请输入有效目标金额"},due=due))}
    }
}
@Composable fun ProgressEditor(goal:Goal,deposit:Boolean,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var amount by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")};var date by rememberSaveable{mutableStateOf(LocalDate.now().toString())};val context=LocalContext.current
    EditorSheet(if(deposit)"为目标存入一笔" else "从目标取出一笔",onDismiss){
        Text(goal.title);Hint("当前已攒 ¥${money(s.progress(goal.id))}")
        OutlinedTextField(amount,{if(it.length<=12)amount=it},Modifier.fillMaxWidth(),label={Text("金额（元）")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
        OutlinedButton(onClick={pickDate(context,LocalDate.parse(date)){date=it.toString()}}){Text(date)}
        OutlinedTextField(note,{if(it.length<=500)note=it},Modifier.fillMaxWidth(),label={Text("备注（选填）")},maxLines=3)
        Hint("此操作只改变攒钱进度，不重复计入收入或支出。")
        SubmitButton(done={onDismiss();vm.notify("进度已更新")}){val value=requireNotNull(parseMoney(amount)){"请输入大于0的有效金额"};vm.repo.saveEvent(GoalEvent(goalId=goal.id,amount=if(deposit)value else -value,date=date,note=note))}
    }
}
@Composable fun GoalHistory(goal:Goal,s:Snapshot,onDismiss:()->Unit){
    var count by rememberSaveable{mutableIntStateOf(50)};val events=s.events.filter{it.goalId==goal.id}
    EditorSheet("${goal.title} · 进度历史",onDismiss){
        MoneyText(s.progress(goal.id));Hint("目标 ¥${money(goal.target)} · 共${events.size}笔进度")
        if(events.isEmpty())Hint("还没有进度记录。")
        events.take(count).forEach{e->Panel{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(if(e.amount>0)"存入" else "取出");Text((if(e.amount>0)"+" else "−")+money(kotlin.math.abs(e.amount)))};Hint("${e.date} · ${e.note.ifBlank{"无备注"}}")}}
        if(events.size>count)TextButton(onClick={count+=50}){Text("加载更多")}
        Hint("进度有误可通过相反方向的存入或取出进行调整，保留记录便于回看。")
    }
}
@Composable fun CategoryManager(s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var type by rememberSaveable{mutableIntStateOf(0)};var editing by remember{mutableStateOf<Category?>(null)};var new by remember{mutableStateOf(false)};var name by remember{mutableStateOf("")}
    EditorSheet("管理收支分类",onDismiss){
        ChoiceRow(listOf("支出","收入"),type){type=it;editing=null;new=false;name=""}
        Hint("点击名称可修改。归档分类会从新记账选择中隐藏，历史记录仍然保留。")
        s.categories.filter{it.type==if(type==0)EXPENSE else INCOME}.forEach{c->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={editing=c;name=c.name;new=false},modifier=Modifier.weight(1f)){Text("${c.icon} ${c.name}${if(c.archived)"（已归档）" else ""}")};TextButton(onClick={vm.action(if(c.archived)"分类已恢复" else "分类已归档"){vm.repo.saveCategory(c.copy(archived=!c.archived))}}){Text(if(c.archived)"恢复" else "归档")}}}
        OutlinedButton(onClick={editing=null;new=true;name=""},modifier=Modifier.fillMaxWidth()){Text("＋ 新建分类")}
        if(new||editing!=null){OutlinedTextField(name,{if(it.length<=20)name=it},Modifier.fillMaxWidth(),label={Text("分类名称（最多20字）")},singleLine=true);SubmitButton(done={editing=null;new=false;name="";vm.notify("分类已保存")}){vm.repo.saveCategory(editing?.copy(name=name.trim())?:Category(name=name.trim(),type=if(type==0)EXPENSE else INCOME,sortOrder=s.categories.size))}}
    }
}
