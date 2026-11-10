package com.qingji.ledger

data class ChartSlice(val id:String,val label:String,val value:Double)
fun suggestedAssetName(kind:String,existing:List<AssetAccount>):String{
    val base=accountKindLabel(kind)
    val names=existing.filter{!it.archived}.map{it.name}.toSet()
    if(base !in names)return base
    var index=2
    while("$base $index" in names)index++
    return "$base $index"
}
fun chartShares(slices:List<ChartSlice>):List<Pair<ChartSlice,Float>>{
    val valid=slices.filter{it.value.isFinite()&&it.value>0}
    val total=valid.sumOf{it.value}
    if(total<=0||!total.isFinite())return emptyList()
    return valid.map{it to (it.value/total).toFloat()}
}
fun Snapshot.assetSlices():List<ChartSlice> = assetSlices(assetBalances())
fun Snapshot.assetSlices(balances:Map<String,Long>):List<ChartSlice>{
    return assets.filter{!it.archived&&!isDebtKind(it.kind)&&(balances[it.id]?:0L)>0}.groupBy{it.kind}.map{(kind,accounts)->
        ChartSlice(kind,ACCOUNT_KINDS[kind]?:"其他",accounts.sumOf{(balances[it.id]?:0L).toDouble()})
    }.sortedByDescending{it.value}
}
/** 负债构成：每个负债类欠款账户一个切片，value 为欠款金额的绝对值（分）。 */
fun Snapshot.debtSlices():List<ChartSlice> = debtSlices(assetBalances())
fun Snapshot.debtSlices(balances:Map<String,Long>):List<ChartSlice> =
    assets.filter{!it.archived&&isDebtKind(it.kind)&&(balances[it.id]?:0L)<0}
        .map{ChartSlice(it.id,it.name,(-(balances.getValue(it.id))).toDouble())}
        .sortedByDescending{it.value}
