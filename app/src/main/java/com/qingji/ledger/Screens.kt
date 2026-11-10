package com.qingji.ledger

import android.content.Intent
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlin.math.*

@Composable fun HomeScreen(s: Snapshot, onEntry:(Entry)->Unit, onBudget:(YearMonth)->Unit,onAssets:()->Unit,onDelete:(Entry)->Unit,onNote:(Entry)->Unit) {
    var monthString by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month=YearMonth.parse(monthString)
    var openBillId by rememberSaveable{mutableStateOf<String?>(null)}
    val list=remember(s.entries,monthString) { s.live().filter{it.date.startsWith(monthString) && it.date<=LocalDate.now().toString()}.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.time}.thenByDescending{it.createdAt}) }
    val t=remember(list){totals(list)};val budget=s.budget(monthString);val excluded=s.settings.budgetExcludedEntries;val budgetSpent=remember(list,excluded){budgetExpense(list,excluded)};val excludedCount=remember(list,excluded){s.excludedBudgetEntries(list).size}
    var search by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")};var count by rememberSaveable{mutableIntStateOf(100)}
    val filtered=remember(list,query,s.categories,s.assets){if(query.isBlank())list else list.filter{(it.note+(s.category(it.categoryId)?.name?:"")+(s.assets.find{a->a.id==it.assetId}?.name?:"")).contains(query,true)}}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=20.dp)) {
        item { Surface(color=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer){Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
            MonthPicker(month){monthString=it.toString();count=100}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)){
                Column(Modifier.weight(1f)){Text("本月净支出",fontSize=12.sp);MoneyText(t.expense,size=30.sp,color=LocalContentColor.current)}
                Column(Modifier.weight(1f)){Text("本月收入",fontSize=12.sp);MoneyText(t.income,size=30.sp,color=LocalContentColor.current)}
            }
            Text("本月结余  ¥"+money(t.surplus),fontSize=13.sp)
            HorizontalDivider(color=LocalContentColor.current.copy(alpha=.16f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={onBudget(month)},colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)){Text(if(budget==null)"设置本月预算 ›" else "预算剩余 ¥"+money(budget.limit-budgetSpent)+" ›",fontSize=12.sp)}
                TextButton(onClick=onAssets,colors=ButtonDefaults.textButtonColors(contentColor=LocalContentColor.current)){Text("资产 ¥"+money(s.assetTotal())+" ›",fontSize=12.sp)}
            }
        }}}
        item{Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Text("本月账单",Modifier.weight(1f),fontWeight=FontWeight.Bold);IconButton(onClick={search=!search;if(!search)query=""}){Icon(Icons.Outlined.Search,"搜索账单")}}}
        if(search)item{OutlinedTextField(query,{query=it;count=100;openBillId=null},Modifier.fillMaxWidth().padding(horizontal=20.dp),singleLine=true,label={Text("搜索备注、分类或资产")})}
        if(filtered.isEmpty())item{EmptyState(if(query.isBlank())"这个月还没有记录" else "没有匹配的记录")}
        filtered.take(count).groupBy{it.date}.forEach{(date,dayEntries)->
            item(key="day-"+date){val daily=totals(dayEntries);Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal=20.dp,vertical=9.dp),horizontalArrangement=Arrangement.SpaceBetween){Hint(date.substring(5).replace('-','月')+"日  "+listOf("一","二","三","四","五","六","日")[LocalDate.parse(date).dayOfWeek.value-1]);Hint("收入 "+money(daily.income)+"  支出 "+money(daily.expense))}}
            items(dayEntries,key={it.id}){entry->SwipeBillRow(entry,s,openBillId==entry.id,{openBillId=if(it)entry.id else null},{onEntry(entry)},{openBillId=null;onNote(entry)},{openBillId=null;onDelete(entry)})}
        }
        if(filtered.size>count)item{TextButton(onClick={count+=100},modifier=Modifier.fillMaxWidth()){Text("加载更多 · 共"+filtered.size+"笔")}}
        if(list.isNotEmpty())item{Hint("结余为本月收入减净支出；退款按发生日冲减支出。"+if(excludedCount>0)"预算不计入 $excludedCount 笔还款或已排除账单。" else "",Modifier.padding(20.dp))}
    }
}

@Composable fun AnalysisScreen(s: Snapshot,onEntry:(Entry)->Unit,onExport:(DateRange)->Unit) {
    val context=LocalContext.current
    var periodIndex by rememberSaveable{mutableIntStateOf(2)};val period=Period.entries[periodIndex]
    var anchorString by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var customStart by rememberSaveable{mutableStateOf(LocalDate.now().withDayOfMonth(1).toString())};var customEnd by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var selectedType by rememberSaveable{mutableIntStateOf(0)}
    var filterCategory by rememberSaveable { mutableStateOf<String?>(null) };var count by rememberSaveable{mutableIntStateOf(100)}
    val anchor=LocalDate.parse(anchorString)
    val range=periodRange(period,anchor,custom=DateRange(LocalDate.parse(customStart),LocalDate.parse(customEnd)))
    val previous=comparisonRange(period,range)
    val list=remember(s.entries,range){s.live().filter{range.contains(it.date) && it.date<=LocalDate.now().toString()}}
    val old=remember(s.entries,previous){s.live().filter{previous.contains(it.date)}}
    val t=remember(list){totals(list)};val pt=remember(old){totals(old)}
    val spending=selectedType==0
    val rank=remember(list,selectedType){list.filter{if(spending)it.type!=INCOME else it.type==INCOME}.groupBy{it.categoryId}.mapValues{(_,v)->if(spending)totals(v).expense else totals(v).income}.toList().sortedByDescending{it.second}}
    val totalRank=if(spending)t.expense else t.income
    val compareValue=if(spending)pt.expense else pt.income
    val selectedValue=if(spending)t.expense else t.income
    val delta=if(compareValue>0 && selectedValue>=0)(selectedValue-compareValue).toDouble()/compareValue*100 else null
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,5.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item { ChoiceRow(Period.entries.map{it.title},periodIndex){periodIndex=it;filterCategory=null;count=100} }
        item { Panel {
            if(period==Period.CUSTOM){TextButton(onClick={pickDate(context,LocalDate.parse(customStart)){d->customStart=d.toString();if(d>LocalDate.parse(customEnd))customEnd=customStart}}){Text("开始：$customStart")};TextButton(onClick={pickDate(context,LocalDate.parse(customEnd)){d->customEnd=d.toString();if(d<LocalDate.parse(customStart))customStart=customEnd}}){Text("结束：$customEnd")}}
            else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick={anchorString=when(period){Period.DAY->anchor.minusDays(1);Period.WEEK->anchor.minusWeeks(1);Period.YEAR->anchor.minusYears(1);else->anchor.minusMonths(1)}.toString();filterCategory=null}){Text("‹",fontSize=23.sp)};TextButton(onClick={pickDate(context,anchor){anchorString=it.toString();filterCategory=null}},modifier=Modifier.weight(1f)){Text(if(period==Period.MONTH)"${anchor.year}年${anchor.monthValue}月" else if(period==Period.YEAR)"${anchor.year}年" else anchorString)};TextButton(onClick={val next=when(period){Period.DAY->anchor.plusDays(1);Period.WEEK->anchor.plusWeeks(1);Period.YEAR->anchor.plusYears(1);else->anchor.plusMonths(1)};anchorString=minOf(next,LocalDate.now()).toString();filterCategory=null},enabled=range.end<LocalDate.now()){Text("›",fontSize=23.sp)}}
            Hint("${range.start} 至 ${range.end}")
        } }
        item { Panel { ChoiceRow(listOf("支出分析","收入分析"),selectedType){selectedType=it;filterCategory=null};Hint(if(spending)"期间净支出" else "期间收入");MoneyText(selectedValue,size=34.sp);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Column(Modifier.weight(1f)){Hint(if(spending)"日均净支出" else "日均收入");MoneyText(selectedValue/range.days,size=19.sp)};Column(Modifier.weight(1f)){Hint("与上一同期比较");Text(delta?.let{(if(it>0)"+" else "")+String.format(java.util.Locale.CHINA,"%.1f%%",it)}?:"无可比数据",fontSize=18.sp)}};Hint("对比：${previous.start} 至 ${previous.end}");if(t.refunds>0)Hint("支出 ¥${money(t.gross)} − 退款 ¥${money(t.refunds)}");Hint("期间结余 ¥${money(t.surplus)} · ${list.size}笔记录") } }
        item { Panel { Heading(if(spending)"净支出趋势" else "收入趋势");TrendChart(list,range,spending);Hint("横轴：日期 · 纵轴：元；超过31天时按连续日期分组。") } }
        item { Heading("分类排行") }
        if(rank.isEmpty())item { EmptyState("这段时间没有${if(spending)"支出" else "收入"}","切换日期，或先记下一笔。") }
        items(rank,key={it.first}){(catId,value)->Panel(Modifier.clickable{filterCategory=if(filterCategory==catId)null else catId;count=100}){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(s.category(catId)?.name?:"未知分类",fontWeight=FontWeight.Medium);Text("¥${money(value)}")};if(totalRank>0 && value>=0){Progress(value.toFloat()/totalRank);Hint(String.format(java.util.Locale.CHINA,"占比 %.1f%% · 点击查看明细",value.toDouble()/totalRank*100))}else Hint("包含跨期退款 · 点击查看明细")} }
        item { Heading(filterCategory?.let{(s.category(it)?.name?:"")+" · 明细"}?:"期间明细",if(filterCategory!=null)"清除筛选" else null){filterCategory=null} }
        val details=list.filter{(filterCategory==null || it.categoryId==filterCategory) && (if(spending)it.type!=INCOME else it.type==INCOME)}
        items(details.take(count),key={"detail-"+it.id}){e->EntryRow(e,s){onEntry(e)}}
        if(details.size>count)item{TextButton(onClick={count+=100}){Text("加载更多 · 共${details.size}笔")}}
        item{val largest=list.filter{it.type==EXPENSE}.maxOfOrNull{it.amount}?:0;Hint("最大单笔支出 ¥${money(largest)}");OutlinedButton(onClick={onExport(range)},modifier=Modifier.fillMaxWidth()){Text("导出本期间全部收支 · CSV")}}
    }
}
@Composable private fun TrendChart(entries:List<Entry>,range:DateRange,spending:Boolean){
    val bins=min(31L,range.days).toInt().coerceAtLeast(1);val bucket=ceil(range.days.toDouble()/bins).toLong()
    val values=List(bins){i->val start=range.start.plusDays(i*bucket);if(start>range.end)0L else {val end=minOf(start.plusDays(bucket-1),range.end);val t=totals(entries.filter{it.date>=start.toString()&&it.date<=end.toString()});if(spending)t.expense else t.income}}
    val high=max(1L,values.maxOrNull()?:0L).toFloat();val low=min(0L,values.minOrNull()?:0L).toFloat();val color=MaterialTheme.colorScheme.primary;val refund=MaterialTheme.colorScheme.error;val line=MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(140.dp).semantics{contentDescription="${range.start}至${range.end}趋势，${values.size}组，最高${money(values.maxOrNull()?:0)}元"}){val h=size.height-14.dp.toPx();val zero=high/(high-low)*h;drawLine(line,Offset(0f,zero),Offset(size.width,zero));val step=size.width/values.size;values.forEachIndexed{i,v->val value=v.toFloat()/(high-low)*h;drawRect(if(v<0)refund else color,Offset(i*step+step*.18f,if(v>=0)zero-value else zero),androidx.compose.ui.geometry.Size(step*.64f,max(1f,abs(value))))}}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Hint(range.start.toString().substring(5));Hint(range.end.toString().substring(5))}
}

@Composable fun PlansScreen(s:Snapshot,onBudget:(YearMonth,String)->Unit,onGoal:(Goal?)->Unit,onProgress:(Goal,Boolean)->Unit,onHistory:(Goal)->Unit,onDelete:(Goal)->Unit,initialBudget:Boolean=false){
    var segment by rememberSaveable{mutableIntStateOf(if(initialBudget)0 else 0)};var monthString by rememberSaveable{mutableStateOf(YearMonth.now().toString())};val month=YearMonth.parse(monthString)
    val monthEntries=remember(s.entries,monthString){s.live().filter{it.date.startsWith(monthString)&&it.date<=LocalDate.now().toString()}}
    val excluded=s.settings.budgetExcludedEntries
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,5.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{ChoiceRow(listOf("月度预算","攒钱目标"),segment){segment=it}}
        if(segment==0){
            item{MonthPicker(month){monthString=it.toString()}}
            item{BudgetCard("本月总预算",s.budget(monthString),budgetExpense(monthEntries,excluded)){onBudget(month,"")}}
            item{Heading("分类预算","添加"){onBudget(month,"select")}}
            val scopes=s.budgets.filter{it.scope.isNotEmpty()&&(it.period==monthString || it.period=="*")}.map{it.scope}.distinct()
            items(scopes,key={it}){scope->BudgetCard(s.category(scope)?.name?:"已归档分类",s.budget(monthString,scope),budgetExpense(monthEntries.filter{it.categoryId==scope},excluded)){onBudget(month,scope)}}
            if(scopes.isEmpty())item{Hint("可以为餐饮、购物等分类分别设定预算。分类预算不要求与总预算相等。")}
            item{Hint("达到80%时提示预算偏紧，达到100%时显示超支。设置默认预算后，未单独设置的月份自动沿用；可随时单独修改。记账时标记「还款」或关闭「计入预算」，该笔即不占用预算；可在预算设置里查看并恢复。")}
        }else{
            item{Heading("给未来留一点期待","新建"){onGoal(null)}}
            if(s.goals.isEmpty())item{EmptyState("还没有攒钱目标","从一次旅行、一件心仪的小物开始。","创建目标"){onGoal(null)}}
            items(s.goals,key={it.id}){g->val progress=s.progress(g.id);Panel{Heading(g.title,"编辑"){onGoal(g)};Hint(if(progress>=g.target)"目标已达成！" else g.due?.let{"目标日期 $it"}?:"慢慢来，不设期限");MoneyText(progress,size=29.sp);Hint("目标 ¥${money(g.target)} · ${((progress.toDouble()/g.target)*100).toInt()}%");Progress(progress.toFloat()/g.target);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Button(onClick={onProgress(g,true)},modifier=Modifier.weight(1f)){Text("存入")};OutlinedButton(onClick={onProgress(g,false)},enabled=progress>0,modifier=Modifier.weight(1f)){Text("取出")}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick={onHistory(g)}){Text("进度历史")};TextButton(onClick={onDelete(g)}){Text("删除目标",color=MaterialTheme.colorScheme.error)}}}}
            item{Hint("存入与取出只记录目标进度，不生成收支、不计算账户余额。")}
        }
    }
}
@Composable private fun BudgetCard(title:String,budget:Budget?,spent:Long,onEdit:()->Unit){Panel{Heading(title,if(budget==null)"设置" else "调整",onEdit);if(budget==null){Hint("暂未设置预算") }else{val remaining=budget.limit-spent;MoneyText(if(remaining<0)-remaining else remaining,color=if(remaining<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface);Hint(if(remaining<0)"已超支" else "剩余预算");Progress(spent.toFloat()/budget.limit,remaining<0);Hint("净支出 ¥${money(spent)} / 预算 ¥${money(budget.limit)}");if(spent>=budget.limit)Text("本月预算已用完",color=MaterialTheme.colorScheme.error)else if(spent>=budget.limit*.8)Hint("预算偏紧，已使用超过80%。")}}}

@Composable fun SettingsScreen(s:Snapshot,onSettings:((Settings)->Settings)->Unit,onEnable:(Boolean)->Unit,onCategories:()->Unit,onExport:()->Unit,onImport:()->Unit,onRecovery:()->Unit,onPlans:()->Unit){
    val context=LocalContext.current
    var repeatExpanded by rememberSaveable{mutableStateOf(false)}
    val settings=s.settings
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,5.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{Panel{
            Heading("每天一点点")
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text("记账提醒");Hint(if(!settings.enabled)"按需开启，允许系统延迟" else if(!ReminderScheduler.allowed(context))"系统通知未开启，提醒暂不可用" else "${repeatSummary(settings.days)} · ${settings.time}")}
                Switch(settings.enabled,onEnable)
            }
            if(settings.enabled){
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))
                Row(Modifier.fillMaxWidth().clickable{pickTime(context,settings.time){time->if(LocalTime.parse(time)>=LocalTime.of(23,0))android.widget.Toast.makeText(context,"请选择23:00之前的提醒时间",android.widget.Toast.LENGTH_LONG).show() else onSettings{it.copy(time=time)}}},verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("提醒时间");Hint("点击设置，23:00 前有效")}
                    Text(settings.time,fontSize=25.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().clickable{repeatExpanded=!repeatExpanded},verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text("重复");Hint(repeatSummary(settings.days))}
                    Icon(if(repeatExpanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"展开重复设置")
                }
                if(repeatExpanded){
                    ChoiceRow(REMINDER_PRESETS.map{it.first},REMINDER_PRESETS.indexOfFirst{it.second==settings.days}){index->if(index in REMINDER_PRESETS.indices)onSettings{it.copy(days=REMINDER_PRESETS[index].second)}}
                    Hint("自定义 · 选择星期")
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)){
                        WEEKDAY_LABELS.forEachIndexed{i,label->
                            val day=i+1
                            val next=if(day in settings.days)settings.days-day else settings.days+day
                            FilterChip(day in settings.days,onClick={if(next.isNotEmpty())onSettings{it.copy(days=next)}},label={Text(label)},modifier=Modifier.weight(1f),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        }
                    }
                }
                Hint(nextReminderText(settings))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("今天记过就不提醒");Hint("减少打扰")};Switch(settings.skip,{value->onSettings{it.copy(skip=value)}})}
                Hint("系统省电、厂商限制或强制停止可能导致延迟或不送达。超过23:00不补发；再次打开会检查调度。")
                TextButton(onClick={context.startActivity(Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(SystemSettings.EXTRA_APP_PACKAGE,context.packageName))}){Text("系统通知设置")}
            }
        }}
        item{AppearancePicker(s.settings,onSettings)}
        item{Panel{Heading("账本工具");OutlinedButton(onClick=onPlans,modifier=Modifier.fillMaxWidth()){Text("预算与攒钱目标")};OutlinedButton(onClick=onCategories,modifier=Modifier.fillMaxWidth()){Text("管理收支分类")}}}
        item{Panel{Heading("数据在自己手里");Hint("无需注册，账本保存在当前设备。卸载前请导出备份；恢复会替换当前账本，并保留恢复前快照。");Button(onClick=onExport,modifier=Modifier.fillMaxWidth()){Text("导出完整备份 · JSON")};OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()){Text("从备份恢复")};TextButton(onClick=onRecovery,modifier=Modifier.fillMaxWidth()){Text("恢复上次导入前的本地快照")};Hint("备份包含收支和备注，请自行保管。恢复后提醒默认为关闭，需要重新开启。")}}
        item{Panel{Heading("关于轻记");Text("${BuildConfig.VERSION_NAME} · 本地记账");Hint("手动收支、资产、分析、预算、攒钱与备份均免费。无广告、无注册、无网络权限。分享安装包后，朋友会拥有自己的独立账本。");Hint("资产余额由你手动维护；记账只影响本地账本，不会实际扣款。云同步留待后续版本。")}}
    }
}
