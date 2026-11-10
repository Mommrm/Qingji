package com.qingji.ledger

import androidx.room.*
import java.math.BigDecimal
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.min

const val MAX_MONEY = 9_999_999_999L
const val EXPENSE = "EXPENSE"
const val INCOME = "INCOME"
const val REFUND = "REFUND"
fun newId(): String = UUID.randomUUID().toString()
fun money(value: Long): String = BigDecimal.valueOf(value, 2).setScale(2).toPlainString()
fun parseMoney(raw: String): Long? {
    val s = raw.trim()
    if (!s.matches(Regex("[0-9]{1,8}(\\.[0-9]{1,2})?"))) return null
    return runCatching { BigDecimal(s).movePointRight(2).longValueExact().takeIf { it in 1..MAX_MONEY } }.getOrNull()
}

@Entity(tableName = "categories")
data class Category(@PrimaryKey val id: String = newId(), val name: String, val type: String, val icon: String = "○", val sortOrder: Int = 0, val archived: Boolean = false)
@Entity(tableName = "entries", indices = [Index("date"), Index("categoryId"), Index("refundOf"), Index("assetId")])
data class Entry(
    @PrimaryKey val id: String = newId(), val type: String, val amount: Long, val categoryId: String,
    val date: String = LocalDate.now().toString(), val time: String = LocalTime.now().withSecond(0).withNano(0).toString(),
    val note: String = "", val refundOf: String? = null, val zoneId: String = ZoneId.systemDefault().id,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null, val revision: Long = 1, val syncState: String = "LOCAL",
    @ColumnInfo(defaultValue = "NULL") val assetId: String? = null,
    @ColumnInfo(defaultValue = "0") val affectsBalance: Boolean = true
)
@Entity(tableName = "budgets", primaryKeys = ["period", "scope"])
data class Budget(val period: String, val scope: String = "", val limit: Long, val effectiveFrom: String = "")
@Entity(tableName = "goals")
data class Goal(@PrimaryKey val id: String = newId(), val title: String, val target: Long, val due: String? = null, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "goal_events", indices = [Index("goalId")])
data class GoalEvent(@PrimaryKey val id: String = newId(), val goalId: String, val amount: Long, val date: String = LocalDate.now().toString(), val note: String = "", val createdAt: Long = System.currentTimeMillis())

data class Settings(val theme: String = "SYSTEM", val enabled: Boolean = false, val time: String = "21:30", val days: Set<Int> = (1..7).toSet(), val skip: Boolean = true, val reminderRevision: Long = 1, val lastSentDate: String = "",val accent:String="SUN", val budgetExcludedEntries:Set<String> = emptySet(), val repaymentEntries:Set<String> = emptySet())
data class Snapshot(val entries: List<Entry> = emptyList(), val categories: List<Category> = emptyList(), val budgets: List<Budget> = emptyList(), val goals: List<Goal> = emptyList(), val events: List<GoalEvent> = emptyList(), val settings: Settings = Settings(), val ready: Boolean = false, val assets: List<AssetAccount> = emptyList(), val transfers: List<AssetTransfer> = emptyList(), val adjustments: List<AssetAdjustment> = emptyList()) {
    fun category(id: String) = categories.find { it.id == id }
    fun progress(id: String): Long = events.filter { it.goalId == id }.sumOf { it.amount }
    fun budget(period: String, scope: String = ""): Budget? = budgets.find { it.period == period && it.scope == scope } ?: budgets.find { it.period == "*" && it.scope == scope && (it.effectiveFrom.isEmpty() || period >= it.effectiveFrom) }?.copy(period = period)
    fun live() = entries.filter { it.deletedAt == null }
}
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) { "结束日期不能早于开始日期" } }
    fun contains(date: String) = date >= start.toString() && date <= end.toString()
    val days: Long get() = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
}
enum class Period(val title: String) { DAY("日"), WEEK("周"), MONTH("月"), YEAR("年"), CUSTOM("自选") }
fun periodRange(period: Period, anchor: LocalDate, today: LocalDate = LocalDate.now(), custom: DateRange? = null): DateRange {
    if (period == Period.CUSTOM) return requireNotNull(custom)
    val start = when (period) {
        Period.DAY -> anchor
        Period.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Period.MONTH -> anchor.withDayOfMonth(1)
        Period.YEAR -> anchor.withDayOfYear(1)
        else -> anchor
    }
    val fullEnd = when (period) {
        Period.DAY -> anchor
        Period.WEEK -> start.plusDays(6)
        Period.MONTH -> start.plusMonths(1).minusDays(1)
        Period.YEAR -> start.plusYears(1).minusDays(1)
        else -> anchor
    }
    val end = if (today in start..fullEnd) today else fullEnd
    return DateRange(start, end)
}
fun comparisonRange(period: Period, range: DateRange): DateRange {
    val start = when (period) { Period.MONTH -> range.start.minusMonths(1); Period.YEAR -> range.start.minusYears(1); else -> range.start.minusDays(range.days) }
    val maxEnd = when (period) { Period.MONTH -> start.plusMonths(1).minusDays(1); Period.YEAR -> start.plusYears(1).minusDays(1); else -> start.plusDays(range.days - 1) }
    val fullPeriod = (period == Period.MONTH && range.end == range.start.plusMonths(1).minusDays(1)) || (period == Period.YEAR && range.end == range.start.plusYears(1).minusDays(1))
    return DateRange(start, if(fullPeriod) maxEnd else minOf(maxEnd, start.plusDays(range.days - 1)))
}
data class Totals(val income: Long, val expense: Long, val gross: Long, val refunds: Long) { val surplus get() = income - expense }
fun totals(entries: List<Entry>): Totals {
    val live = entries.filter { it.deletedAt == null }
    val income = live.filter { it.type == INCOME }.sumOf { it.amount }
    val gross = live.filter { it.type == EXPENSE }.sumOf { it.amount }
    val refunds = live.filter { it.type == REFUND }.sumOf { it.amount }
    return Totals(income, gross - refunds, gross, refunds)
}
fun nextReminder(settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
    if (!settings.enabled || settings.days.isEmpty()) return null
    val time = LocalTime.parse(settings.time)
    for (offset in 0L..7L) {
        val day = now.toLocalDate().plusDays(offset)
        val candidate = day.atTime(time).atZone(now.zone)
        if (day.dayOfWeek.value in settings.days && candidate.isAfter(now)) return candidate
    }
    return null
}
fun defaultCategories(): List<Category> {
    val expense = listOf("餐饮" to "◒", "购物" to "◇", "交通" to "↗", "居住" to "⌂", "娱乐" to "♫", "日用" to "▧", "医疗" to "✚", "学习" to "▤", "人情" to "♡", "其他" to "○")
    val income = listOf("工资" to "▤", "奖金" to "☆", "兼职" to "↗", "理财收益" to "◇", "其他" to "○")
    return expense.mapIndexed { i, p -> Category("expense-$i", p.first, EXPENSE, p.second, i) } + income.mapIndexed { i, p -> Category("income-$i", p.first, INCOME, p.second, i) }
}

val WEEKDAY_LABELS = listOf("一","二","三","四","五","六","日")
val REMINDER_ALL_DAYS = (1..7).toSet()
val REMINDER_PRESETS = listOf("每天" to REMINDER_ALL_DAYS, "工作日" to (1..5).toSet(), "周末" to setOf(6,7))
fun normalizeReminderDays(days: Set<Int>): Set<Int> = days.filter { it in 1..7 }.toSet().ifEmpty { REMINDER_ALL_DAYS }
fun repeatSummary(days: Set<Int>): String {
    val valid = days.filter { it in 1..7 }.toSet()
    if (valid.isEmpty()) return "未选择提醒日"
    REMINDER_PRESETS.firstOrNull { it.second == valid }?.let { return it.first }
    return "每周" + valid.sorted().joinToString("、") { WEEKDAY_LABELS[it - 1] }
}
fun nextReminderText(settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): String {
    val next = nextReminder(settings, now) ?: return "暂无下次提醒"
    return "下次提醒：" + next.format(java.time.format.DateTimeFormatter.ofPattern("M月d日 E HH:mm", java.util.Locale.CHINA))
}
/** 预算排除以「账单」为单位：记账时标记还款，或手动关闭「计入预算」。 */
fun Snapshot.excludedBudgetEntries(entries: List<Entry>): List<Entry> =
    entries.filter { it.deletedAt == null && it.type == EXPENSE && it.id in settings.budgetExcludedEntries }
fun Snapshot.isRepayment(entry: Entry): Boolean = entry.id in settings.repaymentEntries
fun budgetExpense(entries: List<Entry>, excluded: Set<String>): Long {
    if (excluded.isEmpty()) return totals(entries).expense
    val byId = entries.associateBy { it.id }
    var gross = 0L
    var refunds = 0L
    entries.filter { it.deletedAt == null }.forEach { e ->
        when (e.type) {
            EXPENSE -> if (e.id !in excluded) gross += e.amount
            REFUND -> {
                val origin = e.refundOf?.let { byId[it] }
                if (origin == null || origin.id !in excluded) refunds += e.amount
            }
        }
    }
    return gross - refunds
}
