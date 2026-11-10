package com.qingji.ledger

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.*

object BackupCodec {
    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply { pairs.forEach { (k,v) -> put(k,v ?: JSONObject.NULL) } }
    private fun array(list: List<JSONObject>) = JSONArray(list)
    fun encode(s: Snapshot): String = obj(
        "app" to "QingJi", "schemaVersion" to 2, "currency" to "CNY", "exportedAt" to Instant.now().toString(),
        "entries" to array(s.entries.map { e -> obj("id" to e.id,"type" to e.type,"amount" to e.amount,"categoryId" to e.categoryId,"date" to e.date,"time" to e.time,"note" to e.note,"refundOf" to e.refundOf,"zoneId" to e.zoneId,"createdAt" to e.createdAt,"updatedAt" to e.updatedAt,"deletedAt" to e.deletedAt,"revision" to e.revision,"syncState" to e.syncState,"assetId" to e.assetId,"affectsBalance" to e.affectsBalance) }),
        "categories" to array(s.categories.map { c -> obj("id" to c.id,"name" to c.name,"type" to c.type,"icon" to c.icon,"sortOrder" to c.sortOrder,"archived" to c.archived) }),
        "budgets" to array(s.budgets.map { b -> obj("period" to b.period,"scope" to b.scope,"limit" to b.limit,"effectiveFrom" to b.effectiveFrom) }),
        "goals" to array(s.goals.map { g -> obj("id" to g.id,"title" to g.title,"target" to g.target,"due" to g.due,"createdAt" to g.createdAt) }),
        "events" to array(s.events.map { e -> obj("id" to e.id,"goalId" to e.goalId,"amount" to e.amount,"date" to e.date,"note" to e.note,"createdAt" to e.createdAt) }),
        "assets" to array(s.assets.map{a->obj("id" to a.id,"name" to a.name,"kind" to a.kind,"openingBalance" to a.openingBalance,"archived" to a.archived,"createdAt" to a.createdAt,"updatedAt" to a.updatedAt)}),
        "transfers" to array(s.transfers.map{t->obj("id" to t.id,"fromAssetId" to t.fromAssetId,"toAssetId" to t.toAssetId,"amount" to t.amount,"date" to t.date,"note" to t.note,"createdAt" to t.createdAt,"updatedAt" to t.updatedAt,"deletedAt" to t.deletedAt)}),
        "adjustments" to array(s.adjustments.map{a->obj("id" to a.id,"assetId" to a.assetId,"delta" to a.delta,"targetBalance" to a.targetBalance,"date" to a.date,"note" to a.note,"createdAt" to a.createdAt,"updatedAt" to a.updatedAt,"deletedAt" to a.deletedAt)}),
        "settings" to obj("theme" to s.settings.theme,"accent" to s.settings.accent,"enabled" to s.settings.enabled,"time" to s.settings.time,"days" to JSONArray(s.settings.days.sorted()),"skip" to s.settings.skip,"budgetExcludedEntries" to JSONArray(s.settings.budgetExcludedEntries.sorted()),"repaymentEntries" to JSONArray(s.settings.repaymentEntries.sorted()))
    ).toString(2)
    private fun list(root: JSONObject, key: String, maximum: Int = 200_000): List<JSONObject> { val a = root.getJSONArray(key); require(a.length() <= maximum) { "备份记录数量超限" }; return (0 until a.length()).map { a.getJSONObject(it) } }
    private fun text(o: JSONObject, key: String, max: Int = 200): String = o.getString(key).also { require(it.length <= max) { "备份字段过长：$key" } }
    private fun optional(o: JSONObject, key: String): String? = if(o.isNull(key)) null else text(o,key)
    private fun long(o: JSONObject, key: String): Long { val raw = o.get(key).toString(); require(raw.matches(Regex("-?[0-9]{1,18}"))) { "备份金额/整数格式错误" }; return raw.toLong() }
    private fun <T> unique(list: List<T>, key: (T) -> String) { val ids=list.map(key); require(ids.size==ids.toSet().size && ids.none { it.isBlank() || it.length>200 }) { "备份存在重复或无效ID" } }
    fun decode(raw: String): Snapshot {
        require(raw.toByteArray(Charsets.UTF_8).size <= 50*1024*1024) { "备份不能超过50MB" }
        val root = JSONObject(raw)
        val version=root.getInt("schemaVersion")
        require(root.getString("app") == "QingJi" && version in 1..2 && root.getString("currency") == "CNY") { "不支持此备份格式或版本" }
        val cats = list(root,"categories",1000).map { c -> Category(text(c,"id"),text(c,"name",20),text(c,"type"),text(c,"icon",10),c.getInt("sortOrder"),c.getBoolean("archived")) }
        unique(cats) { it.id }; require(cats.isNotEmpty() && cats.all { it.name.isNotBlank() && it.type in listOf(EXPENSE,INCOME) }) { "备份分类无效" }
        val catMap = cats.associateBy { it.id }
        val entries = list(root,"entries").map { e -> Entry(text(e,"id"),text(e,"type"),long(e,"amount"),text(e,"categoryId"),text(e,"date"),text(e,"time"),text(e,"note",500),optional(e,"refundOf"),text(e,"zoneId"),long(e,"createdAt"),long(e,"updatedAt"),if(e.isNull("deletedAt")) null else long(e,"deletedAt"),long(e,"revision"),text(e,"syncState"),if(version>=2)optional(e,"assetId")else null,if(version>=2)e.getBoolean("affectsBalance")else false) }
        unique(entries) { it.id }
        entries.forEach { e ->
            require(e.amount in 1..MAX_MONEY && e.type in listOf(EXPENSE,INCOME,REFUND) && e.revision>0) { "备份收支金额或类型无效" }
            LocalDate.parse(e.date); LocalTime.parse(e.time); ZoneId.of(e.zoneId)
            require(catMap[e.categoryId]?.type == if(e.type==INCOME) INCOME else EXPENSE) { "备份分类引用无效" }
            require(e.createdAt>=0 && e.updatedAt>=0) { "备份时间戳无效" }
        }
        val entryMap = entries.associateBy { it.id }
        val refunds = entries.filter { it.type == REFUND }
        refunds.forEach { e -> val original = entryMap[e.refundOf]; require(original!=null && original.type==EXPENSE && original.categoryId==e.categoryId && original.date<=e.date && (e.deletedAt!=null || original.deletedAt==null)) { "备份退款关联无效" } }
        entries.filter { it.type!=REFUND }.forEach { require(it.refundOf==null) }
        refunds.filter { it.deletedAt==null }.groupBy { it.refundOf }.forEach { (id, list) -> require(list.sumOf { it.amount } <= entryMap.getValue(id!!).amount) { "备份退款超过原支出" } }
        val budgets=list(root,"budgets",20000).map { b -> Budget(text(b,"period"),text(b,"scope"),long(b,"limit"),b.optString("effectiveFrom","")) }
        unique(budgets) { "${it.period}|${it.scope}" }; budgets.forEach { if(it.period!="*")YearMonth.parse(it.period);if(it.effectiveFrom.isNotEmpty())YearMonth.parse(it.effectiveFrom);require(it.limit in 1..MAX_MONEY && (it.scope.isEmpty() || catMap[it.scope]?.type==EXPENSE)) { "备份预算无效" } }
        val goals=list(root,"goals",5000).map { g -> Goal(text(g,"id"),text(g,"title",30),long(g,"target"),optional(g,"due"),long(g,"createdAt")) }
        unique(goals) { it.id };goals.forEach { require(it.title.isNotBlank() && it.target in 1..MAX_MONEY);it.due?.let(LocalDate::parse) }
        val goalIds=goals.map { it.id }.toSet()
        val events=list(root,"events").map { e -> GoalEvent(text(e,"id"),text(e,"goalId"),long(e,"amount"),text(e,"date"),text(e,"note",500),long(e,"createdAt")) }
        unique(events) { it.id };events.forEach { require(it.goalId in goalIds && it.amount!=0L && it.amount in -MAX_MONEY..MAX_MONEY);LocalDate.parse(it.date) }
        events.groupBy { it.goalId }.values.forEach { list -> var progress=0L;list.sortedBy { it.createdAt }.forEach { progress=Math.addExact(progress,it.amount);require(progress in 0..MAX_MONEY) { "备份攒钱进度无效" } } }
        val p=root.getJSONObject("settings");val dayArray=p.getJSONArray("days");val days=(0 until dayArray.length()).map { dayArray.getInt(it) }.toSet();val theme=text(p,"theme")
        fun idSet(key: String): Set<String> {
            val a=p.optJSONArray(key)?:JSONArray()
            require(a.length()<=200_000){"备份预算标记数量超限：$key"}
            val ids=(0 until a.length()).map{a.getString(it)}.toSet()
            require(ids.all{it.isNotBlank()&&it.length<=200}){"备份预算标记无效：$key"}
            return ids
        }
        val excludedEntries=idSet("budgetExcludedEntries")
        val repayments=idSet("repaymentEntries")
        require(excludedEntries.all{it in entryMap}&&repayments.all{it in entryMap}){"备份预算标记引用了不存在的账单"}
        require(days.isNotEmpty() && days.all { it in 1..7 } && theme in listOf("SYSTEM","LIGHT","DARK")) { "备份偏好设置无效" }
        val time=text(p,"time");val t=LocalTime.parse(time);require(t<LocalTime.of(23,0)) { "提醒时间需早于23:00" }
        val accent=p.optString("accent","SUN");require(validAccent(accent)){"备份主题色格式无效"}
        val assets=if(version<2)emptyList()else list(root,"assets",1000).map{a->AssetAccount(text(a,"id"),text(a,"name",30),text(a,"kind"),long(a,"openingBalance"),a.getBoolean("archived"),long(a,"createdAt"),long(a,"updatedAt"))}
        unique(assets){it.id};assets.forEach{require(it.name.isNotBlank()&&it.kind in ASSET_KINDS&&it.openingBalance in 0..MAX_MONEY&&it.createdAt>=0&&it.updatedAt>=0){"备份资产信息无效"}}
        val assetIds=assets.map{it.id}.toSet()
        entries.forEach{require(it.assetId==null||it.assetId in assetIds){"备份记账引用的资产不存在"}}
        val transfers=if(version<2)emptyList()else list(root,"transfers").map{a->AssetTransfer(text(a,"id"),text(a,"fromAssetId"),text(a,"toAssetId"),long(a,"amount"),text(a,"date"),text(a,"note",500),long(a,"createdAt"),long(a,"updatedAt"),if(a.isNull("deletedAt"))null else long(a,"deletedAt"))}
        unique(transfers){it.id};transfers.forEach{require(it.fromAssetId in assetIds&&it.toAssetId in assetIds&&it.fromAssetId!=it.toAssetId&&it.amount in 1..MAX_MONEY){"备份转账信息无效"};LocalDate.parse(it.date)}
        val adjustments=if(version<2)emptyList()else list(root,"adjustments").map{a->AssetAdjustment(text(a,"id"),text(a,"assetId"),long(a,"delta"),long(a,"targetBalance"),text(a,"date"),text(a,"note",500),long(a,"createdAt"),long(a,"updatedAt"),if(a.isNull("deletedAt"))null else long(a,"deletedAt"))}
        unique(adjustments){it.id};adjustments.forEach{require(it.assetId in assetIds&&it.delta!=0L&&it.delta!=Long.MIN_VALUE&&it.targetBalance in 0..MAX_MONEY){"备份余额校准无效"};LocalDate.parse(it.date)}
        val snapshot=Snapshot(entries,cats,budgets,goals,events,Settings(theme=theme,enabled=p.getBoolean("enabled"),time=time,days=days,skip=p.getBoolean("skip"),accent=normalizeAccent(accent),budgetExcludedEntries=excludedEntries,repaymentEntries=repayments),true,assets,transfers,adjustments)
        val balances=snapshot.assetBalances();snapshot.assetTotal();require(assets.filter{it.archived}.all{balances[it.id]==0L}){"已归档资产的余额应为0"}
        return snapshot
    }
    fun csv(s: Snapshot, range: DateRange): String {
        fun cell(s: String): String { val safe=if(s.firstOrNull() in listOf('=','+','-','@','\t','\r')) "'"+s else s;return "\""+safe.replace("\"","\"\"")+"\"" }
        return "\uFEFF日期,时间,类型,分类,金额(元),备注,原支出ID,资产账户,影响资产余额\r\n" + s.live().filter { range.contains(it.date) }.joinToString("\r\n") { e -> listOf(e.date,e.time,when(e.type){INCOME->"收入";REFUND->"退款";else->"支出"},s.category(e.categoryId)?.name?:"未知",money(e.amount),e.note,e.refundOf?:"",s.assets.find{it.id==e.assetId}?.name?:"未关联",if(e.assetId!=null&&e.affectsBalance)"是" else "否").joinToString(",",transform=::cell) }
    }
}
object BackupFiles {
    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri,"wt")) { "无法打开保存位置" }.bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
    suspend fun read(context: Context, uri: Uri): Snapshot = withContext(Dispatchers.IO) {
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取文件" }.use { input -> val result=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=input.read(buffer);while(count!=-1){require(result.size()+count<=50*1024*1024){"备份不能超过50MB"};result.write(buffer,0,count);count=input.read(buffer)};result.toByteArray() }
        BackupCodec.decode(bytes.toString(Charsets.UTF_8))
    }
    suspend fun recovery(context: Context, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val file=AtomicFile(File(context.noBackupFilesDir,"before-restore.json"));val stream=file.startWrite();try { stream.write(BackupCodec.encode(snapshot).toByteArray(Charsets.UTF_8));file.finishWrite(stream) }catch(e:Exception){file.failWrite(stream);throw e}
    }
    suspend fun readRecovery(context: Context): Snapshot = withContext(Dispatchers.IO) { val file=File(context.noBackupFilesDir,"before-restore.json");require(file.exists()) { "还没有恢复快照" };BackupCodec.decode(file.readText(Charsets.UTF_8)) }
}
