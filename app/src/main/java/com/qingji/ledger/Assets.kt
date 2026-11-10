package com.qingji.ledger

import androidx.room.*
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val ASSET_KINDS = linkedMapOf("BANK" to "银行卡", "WECHAT" to "微信", "ALIPAY" to "支付宝", "CASH" to "现金", "OTHER" to "其他", "CREDIT_CARD" to "信用卡", "HUABEI" to "花呗", "CREDIT" to "信用支付")
val CREDIT_ASSET_KINDS = setOf("CREDIT_CARD", "HUABEI", "CREDIT")
fun isCreditAsset(kind:String):Boolean = kind in CREDIT_ASSET_KINDS
fun assetIcon(kind:String):String=when(kind){"BANK"->"▤";"WECHAT"->"聊";"ALIPAY"->"支";"CASH"->"¥";"CREDIT_CARD"->"卡";"HUABEI"->"呗";else->"◇"}
fun parseBalance(raw:String):Long? = if(raw.trim().matches(Regex("0(\\.0{1,2})?")))0 else parseMoney(raw)

@Entity(tableName="asset_accounts")
data class AssetAccount(@PrimaryKey val id:String=newId(),val name:String,val kind:String="BANK",val openingBalance:Long=0,val archived:Boolean=false,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=System.currentTimeMillis())
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
fun Snapshot.assetTotal():Long{val balances=assetBalances();return assets.filter{!it.archived}.fold(0L){sum,a->Math.addExact(sum,balances.getValue(a.id))}}
fun Snapshot.defaultAssetId():String?{val ids=assets.filter{!it.archived}.map{it.id}.toSet();return live().filter{it.assetId in ids}.maxByOrNull{it.createdAt}?.assetId?:assets.firstOrNull{!it.archived}?.id}
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
    require(asset.name.trim().length in 1..30){"资产名称需为1–30字"};require(asset.kind in ASSET_KINDS){"资产类型无效"};require(asset.openingBalance in 0..MAX_MONEY){"请输入有效的初始余额"}
    val old=dao.asset(asset.id)
    require(old==null||old.openingBalance==asset.openingBalance){"期初余额不能直接修改，请使用校准余额"}
    require(dao.assets().none{it.id!=asset.id&&!it.archived&&it.name==asset.name}){"资产名称已存在，请用备注或卡号末四位区分"}
    dao.putAsset(asset.copy(updatedAt=System.currentTimeMillis()))
}
suspend fun Repository.archiveAsset(id:String,archive:Boolean)=db.withTransaction{
    val asset=requireNotNull(dao.asset(id)){"资产不存在"}
    if(archive)require(snapshot().assetBalances()[id]==0L){"账户仍有余额，请先转出或校准至0，再归档"}
    dao.putAsset(asset.copy(archived=archive,updatedAt=System.currentTimeMillis()))
}
suspend fun Repository.saveTransfer(transfer:AssetTransfer)=db.withTransaction{
    require(transfer.fromAssetId!=transfer.toAssetId){"转出和转入账户不能相同"};require(transfer.amount in 1..MAX_MONEY){"请输入有效转账金额"};require(!LocalDate.parse(transfer.date).isAfter(LocalDate.now())){"不能记录未来日期"};require(transfer.note.length<=500)
    val old=dao.transfer(transfer.id)
    fun effects(t:AssetTransfer?):Map<String,Long> = if(t==null||t.deletedAt!=null)emptyMap()else mapOf(t.fromAssetId to -t.amount,t.toAssetId to t.amount)
    validateAssetEffects(effects(old),effects(transfer))
    dao.putTransfer(transfer.copy(updatedAt=System.currentTimeMillis()))
}
suspend fun Repository.deleteTransfer(id:String):AssetTransfer=db.withTransaction{
    val old=requireNotNull(dao.transfer(id));validateAssetEffects(mapOf(old.fromAssetId to -old.amount,old.toAssetId to old.amount),emptyMap());dao.putTransfer(old.copy(deletedAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()));old
}
suspend fun Repository.calibrateAsset(id:String,target:Long,note:String)=db.withTransaction{
    require(target in 0..MAX_MONEY){"实际余额需为0或正数，最多两位小数"};require(note.length<=500)
    val asset=requireNotNull(dao.asset(id));require(!asset.archived){"请先恢复该资产"}
    val current=snapshot().assetBalances().getValue(id);val delta=Math.subtractExact(target,current)
    require(delta!=0L){"账面余额已与输入金额一致，无需校准"}
    dao.putAdjustment(AssetAdjustment(assetId=id,delta=delta,targetBalance=target,note=note))
}
suspend fun Repository.deleteAdjustment(id:String):AssetAdjustment=db.withTransaction{
    val old=requireNotNull(dao.adjustment(id));validateAssetEffects(mapOf(old.assetId to old.delta),emptyMap());dao.putAdjustment(old.copy(deletedAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()));old
}
