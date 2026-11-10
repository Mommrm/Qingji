package com.qingji.ledger

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*

private val Context.settingsStore by preferencesDataStore("settings")
class SettingsStore(private val context: Context) {
    private val updateMutex = Mutex()
    private val theme = stringPreferencesKey("theme")
    private val accent=stringPreferencesKey("accent")
    private val enabled = booleanPreferencesKey("enabled")
    private val time = stringPreferencesKey("time")
    private val days = stringSetPreferencesKey("days")
    private val skip = booleanPreferencesKey("skip")
    private val revision = longPreferencesKey("reminderRevision")
    private val sent = stringPreferencesKey("lastSentDate")
    private val budgetExcludedEntries = stringSetPreferencesKey("budgetExcludedEntries")
    private val repaymentEntries = stringSetPreferencesKey("repaymentEntries")
    val flow: Flow<Settings> = context.settingsStore.data.map { p -> Settings(
        theme = p[theme] ?: "SYSTEM",
        enabled = p[enabled] ?: false,
        time = p[time] ?: "21:30",
        days = (p[days] ?: REMINDER_ALL_DAYS.map { it.toString() }.toSet()).mapNotNull { it.toIntOrNull() }.toSet(),
        skip = p[skip] ?: true,
        reminderRevision = p[revision] ?: 1L,
        lastSentDate = p[sent] ?: "",
        accent = normalizeAccent(p[accent]?:"SUN"),
        budgetExcludedEntries = p[budgetExcludedEntries] ?: emptySet(),
        repaymentEntries = p[repaymentEntries] ?: emptySet()
    ) }
    suspend fun set(settings: Settings) { context.settingsStore.edit { p -> p[theme] = settings.theme; p[accent]=normalizeAccent(settings.accent);p[enabled] = settings.enabled; p[time] = settings.time; p[days] = settings.days.map { it.toString() }.toSet(); p[skip] = settings.skip; p[revision] = settings.reminderRevision; p[sent] = settings.lastSentDate; p[budgetExcludedEntries] = settings.budgetExcludedEntries; p[repaymentEntries] = settings.repaymentEntries } }
    suspend fun update(transform: (Settings) -> Settings) { updateMutex.withLock { set(transform(flow.first())) } }
}

class Repository(val context: Context, val db: LedgerDatabase = LedgerDatabase.create(context)) {
    val dao = db.dao()
    val settings = SettingsStore(context)
    private val assetData=combine(dao.observeAssets(),dao.observeTransfers(),dao.observeAdjustments()){a,t,adjustments->AssetData(a,t,adjustments)}
    val snapshots: Flow<Snapshot> = combine(dao.observeEntries(), dao.observeCategories(), dao.observeBudgets(), dao.observeGoals(), dao.observeEvents()) { e, c, b, g, events -> Snapshot(e, c, b, g, events, ready = c.isNotEmpty()) }.combine(settings.flow) { s, p -> s.copy(settings = p) }.combine(assetData){s,a->s.copy(assets=a.accounts,transfers=a.transfers,adjustments=a.adjustments)}.flowOn(Dispatchers.Default)
    suspend fun initialize() { db.withTransaction { if (dao.categories().isEmpty()) dao.putCategories(defaultCategories()) } }
    suspend fun snapshot(): Snapshot = db.withTransaction { Snapshot(dao.entries(), dao.categories(), dao.budgets(), dao.goals(), dao.events(), settings.flow.first(), true,dao.assets(),dao.transfers(),dao.adjustments()) }
    suspend fun save(entry: Entry) = db.withTransaction {
        require(entry.type in listOf(EXPENSE, INCOME, REFUND)) { "记录类型无效" }
        require(entry.amount in 1..MAX_MONEY) { "金额超出范围" }
        val date = LocalDate.parse(entry.date)
        require(!date.isAfter(LocalDate.now())) { "不能记录未来日期" }
        LocalTime.parse(entry.time); ZoneId.of(entry.zoneId)
        require(entry.note.length <= 500) { "备注最多500字" }
        val cat = requireNotNull(dao.category(entry.categoryId)) { "分类不存在" }
        require(cat.type == if (entry.type == INCOME) INCOME else EXPENSE) { "分类类型不匹配" }
        val existing = dao.entry(entry.id)
        entry.assetId?.let{require(dao.asset(it)!=null){"所选资产不存在"}}
        validateAssetEffects(effectMap(existing),effectMap(entry))
        if (existing?.type == EXPENSE && dao.refunded(entry.id) > 0) {
            require(entry.type == EXPENSE && entry.amount >= dao.refunded(entry.id)) { "原支出不能低于已退款金额" }
            val refunds = dao.entries().filter { it.refundOf == entry.id && it.deletedAt == null }
            require(refunds.all { it.date >= entry.date }) { "原支出日期不能晚于退款日期" }
            refunds.forEach { dao.putEntry(it.copy(categoryId = entry.categoryId, updatedAt = System.currentTimeMillis(), revision = it.revision + 1)) }
        }
        if (entry.type == REFUND) {
            val original = requireNotNull(entry.refundOf?.let { dao.entry(it) }) { "请从原支出记录发起退款" }
            require(original.type == EXPENSE && original.deletedAt == null) { "原支出已删除或无效" }
            require(entry.categoryId == original.categoryId && entry.date >= original.date) { "退款分类或日期无效" }
            require(dao.refunded(original.id, entry.id) + entry.amount <= original.amount) { "累计退款不可超过原支出" }
        } else require(entry.refundOf == null) { "普通收支不能关联退款" }
        dao.putEntry(entry.copy(updatedAt = System.currentTimeMillis(), revision = (existing?.revision ?: 0) + 1, syncState = "LOCAL"))
    }
    suspend fun delete(id: String): Entry = db.withTransaction {
        val deletion=prepareEntryDeletion(dao.entry(id),dao.refunded(id),System.currentTimeMillis())
        validateAssetEffects(effectMap(deletion.before),emptyMap())
        dao.putEntry(deletion.after)
        deletion.before
    }
    suspend fun updateNote(id:String,note:String)=db.withTransaction {
        dao.putEntry(entryWithNote(dao.entry(id),note,System.currentTimeMillis()))
    }
    /** 记账表单保存「还款 / 计入预算」两个标记，与账单本身同属一次保存动作。 */
    suspend fun setEntryBudgetFlags(id: String, inBudget: Boolean, repayment: Boolean) {
        settings.update { st -> st.copy(
            budgetExcludedEntries = if (inBudget) st.budgetExcludedEntries - id else st.budgetExcludedEntries + id,
            repaymentEntries = if (repayment) st.repaymentEntries + id else st.repaymentEntries - id
        ) }
    }
    /** 预算设置里把某笔账单恢复计入预算；同时清掉还款标签，避免标签与口径矛盾。 */
    suspend fun setEntryInBudget(id: String, inBudget: Boolean) {
        settings.update { st -> st.copy(
            budgetExcludedEntries = if (inBudget) st.budgetExcludedEntries - id else st.budgetExcludedEntries + id,
            repaymentEntries = if (inBudget) st.repaymentEntries - id else st.repaymentEntries
        ) }
    }
    suspend fun undo(entry: Entry) { save(entry.copy(deletedAt = null)) }
    suspend fun saveBudget(period: String, scope: String, limit: Long, useDefault: Boolean) = db.withTransaction {
        require(limit in 1..MAX_MONEY)
        YearMonth.parse(period)
        if (scope.isNotEmpty()) require(dao.category(scope)?.type == EXPENSE)
        dao.putBudget(Budget(period, scope, limit))
        if (useDefault) dao.putBudget(Budget("*", scope, limit, YearMonth.now().toString()))
    }
    suspend fun saveCategory(category: Category) = db.withTransaction {
        require(category.name.trim().length in 1..20) { "分类名称需为1–20字" }
        require(dao.categories().none { it.id != category.id && !it.archived && it.name == category.name && it.type == category.type }) { "同类型分类名称已存在" }
        dao.putCategory(category)
    }
    suspend fun saveGoal(goal: Goal) {
        require(goal.title.trim().length in 1..30 && goal.target in 1..MAX_MONEY) { "请填写目标名称和有效金额" }
        goal.due?.let { LocalDate.parse(it) }
        dao.putGoal(goal)
    }
    suspend fun saveEvent(event: GoalEvent) = db.withTransaction {
        require(dao.goal(event.goalId) != null) { "目标不存在" }
        require(event.amount != 0L && event.amount in -MAX_MONEY..MAX_MONEY) { "金额无效" }
        require(!LocalDate.parse(event.date).isAfter(LocalDate.now())) { "不能记录未来日期" }
        require(dao.progress(event.goalId) + event.amount in 0..MAX_MONEY) { "取出超过已攒金额，或累计金额超限" }
        dao.putEvent(event)
    }
    suspend fun removeGoal(id: String): Pair<Goal, List<GoalEvent>> = db.withTransaction {
        val g = requireNotNull(dao.goal(id)); val events = dao.events().filter { it.goalId == id }
        dao.deleteGoalEvents(id); dao.deleteGoal(id); g to events
    }
    suspend fun undoGoal(saved: Pair<Goal, List<GoalEvent>>) = db.withTransaction { dao.putGoal(saved.first); dao.putEvents(saved.second) }
    suspend fun restore(snapshot: Snapshot) {
        val oldSettings = settings.flow.first()
        try { db.withTransaction {
            dao.clearEvents(); dao.clearGoals(); dao.clearEntries(); dao.clearBudgets(); dao.clearCategories();dao.clearTransfers();dao.clearAdjustments();dao.clearAssets()
            dao.putCategories(snapshot.categories);dao.putAssets(snapshot.assets); dao.putEntries(snapshot.entries); dao.putBudgets(snapshot.budgets); dao.putGoals(snapshot.goals); dao.putEvents(snapshot.events);dao.putTransfers(snapshot.transfers);dao.putAdjustments(snapshot.adjustments)
            settings.set(snapshot.settings.copy(enabled = false, reminderRevision = System.currentTimeMillis(), lastSentDate = ""))
        } } catch (e: Exception) { settings.set(oldSettings); throw e }
    }
}

class LedgerApp : Application() {
    val repository by lazy { Repository(this) }
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() { super.onCreate(); ReminderScheduler.createChannel(this); applicationScope.launch { repository.initialize(); ReminderScheduler.schedule(this@LedgerApp, repository.settings.flow.first()) } }
}
data class Notice(val message: String, val undo: (suspend () -> Unit)? = null)
class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as LedgerApp).repository
    val state = repo.snapshots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Snapshot())
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    fun action(success: String? = null, task: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) { try { task(); if(success != null) notices.emit(Notice(success)) } catch (e: Exception) { if (e is CancellationException) throw e; notices.emit(Notice(e.message ?: "操作失败，请重试")) } } }
    fun notify(message: String) { notices.tryEmit(Notice(message)) }
    fun delete(entry:Entry){viewModelScope.launch(Dispatchers.IO){
        try{val old=repo.delete(entry.id);notices.emit(Notice("账单已删除"){repo.undo(old)})}
        catch(e:Exception){if(e is CancellationException)throw e;notices.emit(Notice("未删除"))}
    }}
    fun updateNote(id:String,note:String,done:(Boolean)->Unit){viewModelScope.launch(Dispatchers.IO){
        val saved=try{repo.updateNote(id,note);notices.emit(Notice("备注已保存"));true}catch(e:Exception){if(e is CancellationException)throw e;notices.emit(Notice("未保存"));false}
        withContext(Dispatchers.Main){done(saved)}
    }}
    fun removeGoal(goal: Goal) { action { val old = repo.removeGoal(goal.id); notices.emit(Notice("目标已删除") { repo.undoGoal(old) }) } }
    fun updateSettings(transform: (Settings) -> Settings) { action { repo.settings.update { transform(it).copy(reminderRevision = System.currentTimeMillis()) }; ReminderScheduler.schedule(getApplication(), repo.settings.flow.first()) } }
    fun updatePreferences(transform: (Settings) -> Settings) { action { repo.settings.update(transform) } }
}
