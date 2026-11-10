package com.qingji.ledger

import androidx.room.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val POOL_LIQUID = "LIQUID"
const val POOL_INVEST = "INVEST"
val ASSET_POOLS = linkedMapOf(POOL_LIQUID to "流动资金", POOL_INVEST to "理财资金")
fun normalizePool(pool:String):String = if(pool==POOL_INVEST)POOL_INVEST else POOL_LIQUID
fun poolLabel(pool:String):String = ASSET_POOLS[normalizePool(pool)]?:"流动资金"
/** 资产类账户：余额只能为正，可归入流动资金 / 理财资金。 */
val ASSET_KINDS = linkedMapOf("BANK" to "银行卡", "WECHAT" to "微信", "ALIPAY" to "支付宝", "CASH" to "现金", "OTHER" to "其他")
/** 负债类账户：余额只能为负（欠款），不参与资金池。DEBT_OTHER 与资产的「其他」中文同名，靠 key 前缀区分性质。 */
val DEBT_KINDS = linkedMapOf("CREDIT_CARD" to "信用卡", "HUABEI" to "花呗", "CREDIT" to "信用支付", "DEBT_OTHER" to "其他")
val ACCOUNT_KINDS: Map<String,String> = ASSET_KINDS + DEBT_KINDS
fun isDebtKind(kind:String):Boolean = kind in DEBT_KINDS
fun isDebtAccount(a:AssetAccount):Boolean = isDebtKind(a.kind)
/** 账户默认名：负债组的「其他」加后缀，避免和资产组的「其他」撞名。 */
fun accountKindLabel(kind:String):String = if(isDebtKind(kind)&&DEBT_KINDS[kind]=="其他")"其他负债" else ACCOUNT_KINDS[kind]?:"其他"
fun assetIcon(kind:String):String=when(kind){"BANK"->"▤";"WECHAT"->"聊";"ALIPAY"->"支";"CASH"->"¥";"CREDIT_CARD"->"卡";"HUABEI"->"呗";else->"◇"}
fun parseBalance(raw:String):Long? = if(raw.trim().matches(Regex("0(\\.0{1,2})?")))0 else parseMoney(raw)

@Entity(tableName="asset_accounts")
data class AssetAccount(@PrimaryKey val id:String=newId(),val name:String,val kind:String="BANK",val openingBalance:Long=0,val archived:Boolean=false,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),@ColumnInfo(defaultValue = "'LIQUID'") val pool:String=POOL_LIQUID)
@Entity(tableName="asset_transfers",indices=[Index("fromAssetId"),Index("toAssetId"),Index("date")])
data class AssetTransfer(@PrimaryKey val id:String=newId(),val fromAssetId:String,val toAssetId:String,val amount:Long,val date:String=LocalDate.now().toString(),val note:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val deletedAt:Long?=null)
@Entity(tableName="asset_adjustments",indices=[Index("assetId"),Index("date")])
data class AssetAdjustment(@PrimaryKey val id:String=newId(),val assetId:String,val delta:Long,val targetBalance:Long,val date:String=LocalDate.now().toString(),val note:String="",val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis(),val deletedAt:Long?=null)
data class AssetData(val accounts:List<AssetAccount>,val transfers:List<AssetTransfer>,val adjustments:List<AssetAdjustment>)

fun entryEffect(entry:Entry?):Pair<String,Long>? {
    if(entry==null || entry.deletedAt!=null || !entry.affectsBalance || entry.assetId==null)return null
    return entry.assetId to if(entry.type==EXPENSE)-entry.amount else entry.amount
}
fun Snapshot.assetBalances():Map<String,Long>{
    val result=assets.associate{it.id to it.openingBalance}.toMutableMap()
    fun add(id:String,amount:Long){if(id in result)result[id]=Math.addExact(result.getValue(id),amount)}
    entries.forEach{e->entryEffect(e)?.let{(id,value)->add(id,value)}}
    transfers.filter{it.deletedAt==null}.forEach{t->add(t.fromAssetId,-t.amount);add(t.toAssetId,t.amount)}
    adjustments.filter{it.deletedAt==null}.forEach{a->add(a.assetId,a.delta)}
    return result
}
/** 总资产只累加「资产类」账户的正余额；负债类账户即使有正余额（溢缴）也不计入，另见 assetDebtTotal / debtSlices。
 *  传 pool 可只看某个资金池（LIQUID / INVEST），不传则两池合计。 */
fun Snapshot.assetTotal(pool:String?=null):Long{
    val balances=assetBalances()
    return assets.filter{!it.archived&&!isDebtKind(it.kind)&&(pool==null||normalizePool(it.pool)==pool)}.fold(0L){sum,a->Math.addExact(sum,(balances[a.id]?:0L).coerceAtLeast(0L))}
}
/** 负债合计：未归档「负债类」账户中负余额的绝对值之和（返回正数）。资产类账户的负余额不算负债。 */
fun Snapshot.assetDebtTotal(pool:String?=null):Long{
    val balances=assetBalances()
    return assets.filter{!it.archived&&isDebtKind(it.kind)&&(pool==null||normalizePool(it.pool)==pool)}.fold(0L){sum,a->Math.addExact(sum,(-(balances[a.id]?:0L)).coerceAtLeast(0L))}
}
/** 未归档的负债类账户（余额为负），按欠款金额从大到小。 */
fun Snapshot.debtAccounts():List<Pair<AssetAccount,Long>>{
    val balances=assetBalances()
    return assets.filter{!it.archived&&isDebtKind(it.kind)&&(balances[it.id]?:0L)<0}.map{it to -(balances.getValue(it.id))}.sortedByDescending{it.second}
}
/** 新记账默认只落在资产类账户上；负债账户由用户主动选择（如信用卡消费）。 */
fun Snapshot.defaultAssetId():String?{val ids=assets.filter{!it.archived&&!isDebtKind(it.kind)}.map{it.id}.toSet();return live().filter{it.assetId in ids}.maxByOrNull{it.createdAt}?.assetId?:assets.firstOrNull{!it.archived&&!isDebtKind(it.kind)}?.id}
fun Snapshot.projectedBalance(assetId:String,before:Entry?,after:Entry):Long{
    var result=assetBalances()[assetId]?:0L
    entryEffect(before)?.takeIf{it.first==assetId}?.let{result=Math.subtractExact(result,it.second)}
    entryEffect(after)?.takeIf{it.first==assetId}?.let{result=Math.addExact(result,it.second)}
    return result
}

suspend fun Repository.validateAssetEffects(before:Map<String,Long>,after:Map<String,Long>){
    (before.keys+after.keys).forEach{id->val asset=requireNotNull(dao.asset(id)){"关联资产不存在"};val delta=Math.subtractExact(after[id]?:0L,before[id]?:0L);require(!asset.archived||delta==0L){"该资产已归档，请先在资产页恢复后再修改余额流水"}}
}
fun effectMap(entry:Entry?):Map<String,Long> = entryEffect(entry)?.let{mapOf(it)}?:emptyMap()
suspend fun Repository.saveAsset(asset:AssetAccount)=db.withTransaction{
    require(asset.name.trim().length in 1..30){"账户名称需为1–30字"};require(asset.kind in ACCOUNT_KINDS){"账户类型无效"}
    val debt=isDebtKind(asset.kind)
    // 负债账户录入的是「欠款正数」，库内存负期初；资产账户期初只能是正数或 0
    require(if(debt)asset.openingBalance in -MAX_MONEY..0 else asset.openingBalance in 0..MAX_MONEY){if(debt)"欠款金额无效" else "请输入有效的初始余额"}
    val old=dao.asset(asset.id)
    require(old==null||old.openingBalance==asset.openingBalance){"期初余额不能直接修改，请使用校准余额"}
    require(old==null||isDebtKind(old.kind)==debt){"账户性质不能修改，请另建账户"}
    require(dao.assets().none{it.id!=asset.id&&!it.archived&&it.name==asset.name}){"账户名称已存在，请用备注或卡号末四位区分"}
    // 负债账户不参与资金池分池，固定归流动资金，保证记账表单的池校验可用
    val normalized=asset.copy(pool=if(debt)POOL_LIQUID else normalizePool(asset.pool),updatedAt=System.currentTimeMillis())
    dao.putAsset(normalized)
    // 账户换资金池时，挂在该账户上的账单一起换，避免账单与账户口径不一致
    if(old!=null&&normalizePool(old.pool)!=normalizePool(normalized.pool)){
        dao.entries().filter{it.assetId==asset.id&&normalizePool(it.pool)!=normalizePool(normalized.pool)}.forEach{
            dao.putEntry(it.copy(pool=normalizePool(normalized.pool),updatedAt=System.currentTimeMillis()))
        }
    }
}
suspend fun Repository.archiveAsset(id:String,archive:Boolean)=db.withTransaction{
    val asset=requireNotNull(dao.asset(id)){"资产不存在"}
    if(archive)require(snapshot().assetBalances()[id]==0L){"账户仍有余额，请先转出或校准至0，再归档"}
    dao.putAsset(asset.copy(archived=archive,updatedAt=System.currentTimeMillis()))
}
suspend fun Repository.saveTransfer(transfer:AssetTransfer)=db.withTransaction{
    require(transfer.fromAssetId!=transfer.toAssetId){"转出和转入账户不能相同"};require(transfer.amount in 1..MAX_MONEY){"请输入有效转账金额"};require(!LocalDate.parse(transfer.date).isAfter(LocalDate.now())){"不能记录未来日期"};require(transfer.note.length<=500)
    val from=requireNotNull(dao.asset(transfer.fromAssetId)){"转出账户不存在"}
    val to=requireNotNull(dao.asset(transfer.toAssetId)){"转入账户不存在"}
    require(!from.archived&&!to.archived){"已归档账户不能参与转账"}
    val old=dao.transfer(transfer.id)
    fun effects(t:AssetTransfer?):Map<String,Long> = if(t==null||t.deletedAt!=null)emptyMap()else mapOf(t.fromAssetId to -t.amount,t.toAssetId to t.amount)
    val before=effects(old);val after=effects(transfer)
    // 资产 → 负债即「还款」：欠款只能减少到 0，不能还成正余额（溢缴）
    if(isDebtKind(to.kind)){
        val delta=Math.subtractExact(after[to.id]?:0L,before[to.id]?:0L)
        val projected=Math.addExact(snapshot().assetBalances().getValue(to.id),delta)
        require(projected<=0L){"还款金额超过当前欠款，请核对金额"}
    }
    validateAssetEffects(before,after)
    dao.putTransfer(transfer.copy(updatedAt=System.currentTimeMillis()))
}
suspend fun Repository.deleteTransfer(id:String):AssetTransfer=db.withTransaction{
    val old=requireNotNull(dao.transfer(id));validateAssetEffects(mapOf(old.fromAssetId to -old.amount,old.toAssetId to old.amount),emptyMap());dao.putTransfer(old.copy(deletedAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()));old
}
suspend fun Repository.calibrateAsset(id:String,target:Long,note:String)=db.withTransaction{
    require(target in -MAX_MONEY..MAX_MONEY){"金额超出范围"};require(note.length<=500)
    val asset=requireNotNull(dao.asset(id));require(!asset.archived){"请先恢复该账户"}
    val debt=isDebtKind(asset.kind)
    // 负债账户传入 -欠款额，资产账户传入余额
    require(if(debt)target<=0 else target>=0){if(debt)"请输入当前欠款" else "实际余额需为0或正数，最多两位小数"}
    val current=snapshot().assetBalances().getValue(id);val delta=Math.subtractExact(target,current)
    require(delta!=0L){"账面余额已与输入金额一致，无需校准"}
    dao.putAdjustment(AssetAdjustment(assetId=id,delta=delta,targetBalance=target,note=note))
}
suspend fun Repository.deleteAdjustment(id:String):AssetAdjustment=db.withTransaction{
    val old=requireNotNull(dao.adjustment(id));validateAssetEffects(mapOf(old.assetId to old.delta),emptyMap());dao.putAdjustment(old.copy(deletedAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()));old
}
