@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.qingji.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import java.time.*
import kotlin.math.abs

@Composable fun AssetChoice(title:String,s:Snapshot,selected:String?,onSelect:(String?)->Unit,includeNone:Boolean=true,pool:String?=null){
    CompactAssetChoice(title,s,selected,onSelect,includeNone,pool)
}
@Composable private fun AccountGroupHeader(title:String,total:Long,debt:Boolean){
    Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.Bold)
        Text((if(debt)"欠款合计 ¥" else "合计 ¥")+money(total),color=if(debt)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
    }
}
@Composable private fun AccountRow(asset:AssetAccount,balance:Long,onDetails:(AssetAccount)->Unit){
    val debt=isDebtKind(asset.kind)
    val amountText=when{debt&&balance<0->"−¥"+money(-balance);debt->"+¥"+money(balance);else->"¥"+money(balance)}
    Panel(Modifier.clickable{onDetails(asset)}){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            AssetBadge(asset.kind)
            Column(Modifier.weight(1f)){Text(asset.name,fontWeight=FontWeight.Medium,maxLines=1);Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                if(debt)Text(if(balance<0)"负债 · 欠款" else "负债 · 已结清 / 溢缴",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall,maxLines=1)
                else{Text(poolLabel(asset.pool),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.labelSmall,maxLines=1);if(balance<0)Text("余额为负",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}
            }}
            Text(amountText,fontSize=19.sp,fontWeight=FontWeight.Medium,color=if(debt||balance<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun AssetsScreen(s:Snapshot,onCreate:()->Unit,onDetails:(AssetAccount)->Unit,onTransfer:()->Unit){
    var archived by rememberSaveable{mutableStateOf(false)}
    val balances=remember(s.assets,s.entries,s.transfers,s.adjustments){s.assetBalances()};val active=s.assets.filter{!it.archived}
    val hasDebt=active.any{isDebtKind(it.kind)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,5.dp,16.dp,24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Panel{
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("总资产",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium);InfoHint("资产说明","总资产只累加资产类账户（银行卡、微信、支付宝、现金、其他）的正余额，分为流动资金与理财资金；信用卡、花呗、信用支付等负债类账户只记欠款，红色显示且不冲减总资产，另在下方单独列出。手动记录本地余额，不连接支付平台。旧账默认不扣减新增余额；转账、还款、期初和校准不计收支。")}
            MoneyText(s.assetTotal(),size=34.sp)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Column(Modifier.weight(1f)){Hint(poolLabel(POOL_LIQUID));Text("¥"+money(s.assetTotal(POOL_LIQUID)),fontSize=17.sp,fontWeight=FontWeight.Medium)}
                Column(Modifier.weight(1f)){Hint(poolLabel(POOL_INVEST));Text("¥"+money(s.assetTotal(POOL_INVEST)),fontSize=17.sp,fontWeight=FontWeight.Medium)}
            }
            if(active.isNotEmpty())AssetDistribution(s)
            OutlinedButton(onClick=onTransfer,enabled=active.size>=2,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.SwapHoriz,null,Modifier.size(19.dp));Spacer(Modifier.width(8.dp));Text(if(hasDebt)"转账 / 还款" else "转账")}
        }}
        item{Heading("账户","添加",onCreate);ChoiceRow(listOf("使用中","已归档"),if(archived)1 else 0){archived=it==1}}
        val shown=s.assets.filter{it.archived==archived}
        val assetList=shown.filter{!isDebtKind(it.kind)}
        val debtList=shown.filter{isDebtKind(it.kind)}
        if(shown.isEmpty())item{EmptyState(if(archived)"暂无归档账户" else "添加一项账户",if(archived)"" else "填写当前余额或当前欠款",if(archived)null else "添加账户",onCreate)}
        if(assetList.isNotEmpty()){
            item{AccountGroupHeader("资产账户",assetList.sumOf{(balances[it.id]?:0L).coerceAtLeast(0L)},false)}
            items(assetList,key={it.id}){asset->AccountRow(asset,balances[asset.id]?:0L,onDetails)}
        }
        if(debtList.isNotEmpty()){
            item{AccountGroupHeader("负债账户",debtList.sumOf{(-(balances[it.id]?:0L)).coerceAtLeast(0L)},true)}
            items(debtList,key={it.id}){asset->AccountRow(asset,balances[asset.id]?:0L,onDetails)}
        }
    }
}
@Composable fun AssetEditor(asset:AssetAccount?,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var name by rememberSaveable(asset?.id){mutableStateOf(asset?.name?:"")}
    var debt by rememberSaveable(asset?.id,key="debt-${asset?.id}"){mutableStateOf(asset?.let{isDebtKind(it.kind)}?:false)}
    var kind by rememberSaveable(asset?.id){mutableStateOf(asset?.kind?:"BANK")}
    var amount by rememberSaveable(asset?.id){mutableStateOf("")};var rename by rememberSaveable{mutableStateOf(false)}
    var pool by rememberSaveable(asset?.id){mutableStateOf(normalizePool(asset?.pool?:POOL_LIQUID))}
    val displayName=name.ifBlank{suggestedAssetName(kind,s.assets)}
    val value=parseBalance(amount.ifBlank{"0"}.trimEnd('.'))
    val kinds=if(debt)DEBT_KINDS else ASSET_KINDS
    val form:@Composable ColumnScope.()->Unit={
        if(asset==null)ChoiceRow(listOf("资产账户","负债账户"),if(debt)1 else 0){i->
            val next=i==1
            if(next!=debt){debt=next;kind=if(next)DEBT_KINDS.keys.first() else ASSET_KINDS.keys.first();name=""}
        }
        else Hint(if(debt)"负债账户：只记欠款，不计入总资产；账户性质创建后不可修改。" else "资产账户：计入总资产，可归入流动资金或理财资金；账户性质创建后不可修改。")
        AssetKindPicker(kind,kinds){kind=it}
        if(!debt){
            ChoiceRow(ASSET_POOLS.values.toList(),ASSET_POOLS.keys.indexOf(pool)){i->pool=ASSET_POOLS.keys.elementAt(i)}
            Hint(if(pool==POOL_INVEST)"归入理财资金：只有理财账单能关联此账户；换池会同步更新该账户已有账单。" else "归入流动资金：只有流动账单能关联此账户；换池会同步更新该账户已有账单。")
        }else Hint("负债账户不参与流动资金 / 理财资金分池；消费请记账，还款请用「转账 / 还款」。")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(displayName,Modifier.weight(1f),fontWeight=FontWeight.Medium);IconButton(onClick={rename=true}){Icon(Icons.Outlined.Edit,"修改账户名称",Modifier.size(18.dp))};InfoHint(if(debt)"负债账户" else "资产账户",if(debt)"名称自动填写，可点击铅笔修改。填写当前欠款即可，无需输入负号；保存后按红色欠款显示。这里只记录账面欠款，不实际扣款。" else "名称自动填写，可点击铅笔修改。当前余额作为期初，不算收入。旧账若已包含在余额中，请勿再次开启余额联动。这里只记录账面余额，不实际扣款。")}
        if(asset==null){
            Hint(if(debt)"当前欠款" else "当前余额");AmountDisplay(amount){amount=""}
            if(debt)Hint("输入正数表示欠款，不需要负号；保存后余额记为负数并显示为红色。")
        }else{
            Hint(if(debt)"期初欠款" else "期初余额");MoneyText(abs(asset.openingBalance),size=28.sp,color=if(debt)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
    if(asset==null)FastInputSheet(if(debt)"添加负债账户" else "添加资产账户",onDismiss,form=form,footer={
        AmountKeypad{amount=keypadInput(amount,it)}
        SubmitButton(enabled=value!=null,label=if(debt)"保存欠款" else "保存",done={onDismiss();vm.notify("已添加")}){
            val v=requireNotNull(value)
            vm.repo.saveAsset(AssetAccount(name=displayName.trim(),kind=kind,openingBalance=if(debt)-v else v,pool=if(debt)POOL_LIQUID else pool))
        }
    })else EditorSheet("编辑账户",onDismiss){form();SubmitButton(done={onDismiss();vm.notify("已保存")}){vm.repo.saveAsset(asset.copy(name=displayName.trim(),kind=kind,pool=pool))}}
    if(rename)TextInputDialog("账户名称",displayName,30,{rename=false},{name=it.trim()})
}
@Composable fun CalibrationEditor(asset:AssetAccount,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    var amount by rememberSaveable{mutableStateOf("")};var note by rememberSaveable{mutableStateOf("")}
    val current=s.assetBalances()[asset.id]?:0;val debt=isDebtKind(asset.kind)
    val typed=parseBalance(amount.trimEnd('.'));val target=typed?.let{if(debt)-it else it}
    val label=if(debt)"欠款 ¥" else "¥"
    FastInputSheet(if(debt)"校准欠款" else "校准余额",onDismiss,form={
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){AssetBadge(asset.kind,32.dp);Text(asset.name,Modifier.weight(1f));InfoHint("余额校准",if(debt)"填写当前实际欠款，不需要负号。只记录差额，不计收支；后续修改旧流水仍会影响欠款。" else "填写实际余额。只记录差额，不计收支；后续修改旧流水仍会影响余额。")}
        Hint(if(debt)"实际欠款" else "实际余额");AmountDisplay(amount){amount=""}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(label+money(abs(current)),color=MaterialTheme.colorScheme.onSurfaceVariant);Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp));Text(if(target==null)"—" else label+money(abs(target)),fontWeight=FontWeight.Medium)}
        NoteAction(note){note=it}
    },footer={AmountKeypad{amount=keypadInput(amount,it)};SubmitButton(enabled=target!=null,done={onDismiss();vm.notify("已校准")}){vm.repo.calibrateAsset(asset.id,requireNotNull(target),note)}})
}
@Composable fun TransferEditor(transfer:AssetTransfer?,preferredSource:String?,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit){
    val active=s.assets.filter{!it.archived};val first=preferredSource?.takeIf{id->active.any{it.id==id}}?:s.defaultAssetId()
    // 从资产页直接进来时，若有负债账户则默认摆好「资产 → 负债」的还款方向
    val debtTarget=if(preferredSource==null)active.firstOrNull{isDebtKind(it.kind)&&it.id!=first}?.id else null
    var from by rememberSaveable(transfer?.id){mutableStateOf(transfer?.fromAssetId?:first)};var to by rememberSaveable(transfer?.id){mutableStateOf(transfer?.toAssetId?:debtTarget?:active.firstOrNull{it.id!=first}?.id)}
    var amount by rememberSaveable(transfer?.id){mutableStateOf(transfer?.let{money(it.amount)}?:"")};var date by rememberSaveable(transfer?.id){mutableStateOf(transfer?.date?:LocalDate.now().toString())};var note by rememberSaveable(transfer?.id){mutableStateOf(transfer?.note?:"")};val context=LocalContext.current
    val fromAccount=from?.let{id->s.assets.find{it.id==id}};val toAccount=to?.let{id->s.assets.find{it.id==id}}
    val repayment=fromAccount!=null&&toAccount!=null&&!isDebtKind(fromAccount.kind)&&isDebtKind(toAccount.kind)
    val borrowing=fromAccount!=null&&toAccount!=null&&isDebtKind(fromAccount.kind)&&!isDebtKind(toAccount.kind)
    val flowLabel=when{repayment->"还款";borrowing->"借款";else->"转账"}
    val value=keypadMoney(amount);val current=from?.let{s.assetBalances()[it]}?:0L;val oldSourceRestore=if(transfer!=null&&transfer.fromAssetId==from)transfer.amount else if(transfer!=null&&transfer.toAssetId==from)-transfer.amount else 0L
    FastInputSheet(if(transfer==null)flowLabel else "编辑$flowLabel",onDismiss,form={
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Hint(if(repayment)"从资产账户还款" else "转出",Modifier.weight(1f));InfoHint(flowLabel,when{repayment->"资产 → 负债账户：资产减少、欠款同步减少，不计收支。还款金额不能超过当前欠款。";borrowing->"负债 → 资产账户：欠款与资产同时增加，不计收支。";else->"同时更新两个账户，不计收支。手续费请另记支出。"})}
        CompactAssetChoice(if(repayment)"还款账户" else "转出账户",s,from,{from=it},false)
        Icon(Icons.Outlined.South,if(repayment)"还入" else "转入",Modifier.align(Alignment.CenterHorizontally).size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        CompactAssetChoice(if(repayment)"欠款账户" else "转入账户",s,to,{to=it},false)
        AmountDisplay(amount){amount=""}
        if(value!=null&&fromAccount!=null&&!isDebtKind(fromAccount.kind)&&current+oldSourceRestore-value<0)Text("转出账户余额不足",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
        if(transfer==null&&repayment&&value!=null&&toAccount!=null){val owe=-(s.assetBalances()[toAccount.id]?:0L);if(owe>0&&value>owe)Text("还款金额超过当前欠款 ¥"+money(owe),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){NoteAction(note){note=it};TextButton(onClick={pickDate(context,LocalDate.parse(date)){date=it.toString()}}){Icon(Icons.Outlined.CalendarToday,null,Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(if(date==LocalDate.now().toString())"今天" else date.substring(5))}}
        if(transfer!=null)TextButton(onClick={vm.action{val old=vm.repo.deleteTransfer(transfer.id);withContext(Dispatchers.Main){onDismiss()};vm.notices.emit(Notice("记录已删除"){vm.repo.saveTransfer(old.copy(deletedAt=null))})}}){Text("删除",color=MaterialTheme.colorScheme.error)}
    },footer={
        AmountKeypad{amount=keypadInput(amount,it)}
        SubmitButton(label=if(repayment)"确认还款" else "保存",enabled=value!=null&&from!=null&&to!=null&&from!=to,done={onDismiss();vm.notify(if(repayment)"还款已记录" else "已保存")}){val a=requireNotNull(from){"请选择转出账户"};val b=requireNotNull(to){"请选择转入账户"};val v=requireNotNull(keypadMoney(amount)){"请输入有效金额"};vm.repo.saveTransfer((transfer?:AssetTransfer(fromAssetId=a,toAssetId=b,amount=v)).copy(fromAssetId=a,toAssetId=b,amount=v,date=date,note=note))}
    })
}
private data class AssetHistory(val id:String,val date:String,val title:String,val amount:Long,val note:String,val createdAt:Long,val entry:Entry?=null,val transfer:AssetTransfer?=null,val adjustment:AssetAdjustment?=null,val linkedOnly:Boolean=false)
@Composable fun AssetDetails(asset:AssetAccount,s:Snapshot,vm:LedgerViewModel,onDismiss:()->Unit,onEdit:()->Unit,onCalibrate:()->Unit,onTransfer:(String?)->Unit,onEntry:(Entry)->Unit,onTransferEdit:(AssetTransfer)->Unit){
    var count by rememberSaveable(asset.id){mutableIntStateOf(50)}
    val balance=s.assetBalances()[asset.id]?:0L;val debt=isDebtKind(asset.kind)
    val history=remember(s.entries,s.transfers,s.adjustments,s.assets,s.categories,asset.id){
        val entries=s.live().filter{it.assetId==asset.id}.map{e->AssetHistory(e.id,e.date,(s.category(e.categoryId)?.name?:"收支")+when(e.type){REFUND->" · 退款";INCOME->" · 收入";else->" · 支出"},entryEffect(e)?.second?:e.amount,e.note,e.createdAt,entry=e,linkedOnly=!e.affectsBalance)}
        val transfers=s.transfers.filter{it.deletedAt==null&&(it.fromAssetId==asset.id||it.toAssetId==asset.id)}.map{t->val outgoing=t.fromAssetId==asset.id;val other=s.assets.find{it.id==if(outgoing)t.toAssetId else t.fromAssetId}?.name?:"另一账户";AssetHistory(t.id,t.date,(if(outgoing)"转出 → " else "转入 ← ")+other,if(outgoing)-t.amount else t.amount,t.note,t.createdAt,transfer=t)}
        val adjustments=s.adjustments.filter{it.deletedAt==null&&it.assetId==asset.id}.map{a->AssetHistory(a.id,a.date,"余额校准（当时设为 "+(if(debt)"欠款 ¥" else "¥")+money(abs(a.targetBalance))+"）",a.delta,a.note,a.createdAt,adjustment=a)}
        (entries+transfers+adjustments).sortedWith(compareByDescending<AssetHistory>{it.date}.thenByDescending{it.createdAt})
    }
    EditorSheet(asset.name,onDismiss){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){AssetBadge(asset.kind);MoneyText(abs(balance),size=30.sp,color=if(debt||balance<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface);Text(if(debt)"当前欠款" else "当前余额",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        when{
            debt&&balance>0->Text("负债账户出现正余额（溢缴），可用「校准欠款」改为 0。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            !debt&&balance<0->Text("资产账户余额为负，请核对流水或校准余额。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            debt->Text("欠款不计入总资产，还款请用下方「记录一笔还款」。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }
        Hint((if(debt)"期初欠款 ¥" else "期初余额 ¥")+money(abs(asset.openingBalance))+" · ${if(asset.archived)"已归档" else "使用中"}")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick=onEdit,modifier=Modifier.weight(1f)){Text("编辑账户")};OutlinedButton(onClick=onCalibrate,enabled=!asset.archived,modifier=Modifier.weight(1f)){Text(if(debt)"校准欠款" else "校准余额")}}
        if(!asset.archived)OutlinedButton(onClick={onTransfer(if(debt)null else asset.id)},enabled=s.assets.count{!it.archived}>=2,modifier=Modifier.fillMaxWidth()){Text(if(debt)"记录一笔还款" else "从此账户转账")}
        TextButton(onClick={vm.action(if(asset.archived)"账户已恢复" else "账户已归档"){vm.repo.archiveAsset(asset.id,!asset.archived);withContext(Dispatchers.Main){onDismiss()}}},modifier=Modifier.fillMaxWidth()){Text(if(asset.archived)"恢复此账户" else if(debt)"归档此账户（欠款需为0）" else "归档此账户（余额需为0）")}
        Row(verticalAlignment=Alignment.CenterVertically){Text("流水",Modifier.weight(1f),fontWeight=FontWeight.Bold);InfoHint("流水口径","转账、还款、期初和校准不计收支；仅关联记录不改变余额。")}
        if(history.isEmpty())Hint("暂无流水")
        history.take(count).forEach{h->Panel(Modifier.clickable{h.entry?.let(onEntry);h.transfer?.let(onTransferEdit)}){
            Text(h.title,fontWeight=FontWeight.Medium);Text((if(h.linkedOnly)"¥ " else if(h.amount>=0)"+" else "−")+money(abs(h.amount))+(if(h.linkedOnly)" · 仅关联" else ""),color=if(h.linkedOnly)MaterialTheme.colorScheme.onSurfaceVariant else if(h.amount>=0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface);Hint(h.date+if(h.note.isBlank())"" else " · "+h.note)
            h.adjustment?.let{adjustment->TextButton(onClick={vm.action{val old=vm.repo.deleteAdjustment(adjustment.id);withContext(Dispatchers.Main){onDismiss()};vm.notices.emit(Notice("校准已撤销"){vm.repo.db.withTransaction{vm.repo.validateAssetEffects(emptyMap(),mapOf(old.assetId to old.delta));vm.repo.dao.putAdjustment(old.copy(deletedAt=null))}})}}){Text("撤销这次校准")}}
        }}
        if(history.size>count)TextButton(onClick={count+=50}){Text("加载更多 · 共${history.size}条")}
    }
}
