package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

/**
 * 预算标记自 schema v3 起存于 Entry 本身（Room），不再从 Settings 的 ID 集合读取。
 * 这里验证口径：还款 / 不计入预算不影响收支统计，退款跟随原支出，删除记录不占预算。
 */
class BudgetRulesTest {
    private fun expense(id: String, amount: Long, assetId: String? = "bank", category: String = "expense-0", inBudget: Boolean = true, repayment: Boolean = false) =
        Entry(id = id, type = EXPENSE, amount = amount, categoryId = category, assetId = assetId, date = "2026-11-09", inBudget = inBudget, repayment = repayment)
    private fun refund(id: String, amount: Long, original: String, assetId: String? = "bank") =
        Entry(id = id, type = REFUND, amount = amount, categoryId = "expense-0", refundOf = original, assetId = assetId, date = "2026-11-09")

    @Test fun repaymentIsExcludedFromBudgetButStillCountsInTotals() {
        val entries = listOf(expense("e1", 10000), expense("repay", 20000, inBudget = false, repayment = true))
        val s = Snapshot(entries = entries)
        assertEquals(10000L, budgetExpense(entries))
        assertEquals(30000L, totals(entries).expense)
        assertTrue(s.isRepayment(entries[1]))
        assertFalse(s.isRepayment(entries[0]))
        assertEquals(listOf("repay"), s.excludedBudgetEntries(entries).map { it.id })
    }

    @Test fun noExclusionKeepsLegacyNetExpense() {
        val entries = listOf(expense("e1", 10000), refund("r1", 2500, "e1", "cash"))
        assertEquals(totals(entries).expense, budgetExpense(entries))
        assertEquals(7500L, budgetExpense(entries))
    }

    @Test fun refundFollowsOriginalExpenseFlagNotItsOwn() {
        val onlyFirst = listOf(
            expense("e1", 10000, inBudget = false), refund("r1", 3000, "e1", "cash"),
            expense("e2", 8000), refund("r2", 2000, "e2", "card")
        )
        // e1 被排除 → r1 也冲减不了；剩余 e2 8000 - r2 2000 = 6000
        assertEquals(6000L, budgetExpense(onlyFirst))
        val both = onlyFirst.map { if (it.id == "e2") it.copy(inBudget = false) else it }
        assertEquals(0L, budgetExpense(both))
    }

    @Test fun accountTypeNeverAffectsBudget() {
        val entries = listOf(expense("card", 5000, "credit-account"), expense("cash", 7000, "cash"))
        val s = Snapshot(entries = entries, assets = listOf(AssetAccount(id = "credit-account", name = "信用卡", kind = "CREDIT_CARD")))
        assertEquals(12000L, budgetExpense(entries))
        assertTrue(s.excludedBudgetEntries(entries).isEmpty())
    }

    @Test fun deletedEntriesNeverCountTowardsBudget() {
        val entries = listOf(expense("e1", 10000).copy(deletedAt = 1L), expense("e2", 5000))
        assertEquals(5000L, budgetExpense(entries))
    }

    @Test fun categoryBudgetUsesTheSameExclusionRule() {
        val entries = listOf(
            expense("e1", 10000, category = "expense-1"),
            expense("e2", 7000, category = "expense-1", inBudget = false),
            expense("e3", 2000, category = "expense-2")
        )
        assertEquals(10000L, budgetExpense(entries.filter { it.categoryId == "expense-1" }))
    }

    @Test fun excludedListOnlyReturnsLiveExpenses() {
        val entries = listOf(
            expense("e1", 100, inBudget = false), expense("e2", 200, inBudget = false).copy(deletedAt = 1L),
            Entry(id = "i1", type = INCOME, amount = 300, categoryId = "income-0", inBudget = false),
            refund("r1", 50, "e1").copy(inBudget = false)
        )
        val s = Snapshot(entries = entries)
        assertEquals(listOf("e1"), s.excludedBudgetEntries(entries).map { it.id })
    }

    @Test fun defaultsAreOptIn() {
        val s = Settings()
        assertTrue(s.budgetExcludedEntries.isEmpty())
        assertTrue(s.repaymentEntries.isEmpty())
        assertEquals(1234L, budgetExpense(listOf(expense("e1", 1234))))
    }

    /** 迁移干净的关键回归：旧 Settings 里残留的 ID 集合不能再影响预算。 */
    @Test fun legacySettingsFlagsNoLongerAffectBudget() {
        val entries = listOf(expense("e1", 5000), expense("e2", 7000))
        val stale = Settings(budgetExcludedEntries = setOf("e1", "e2"), repaymentEntries = setOf("e1"))
        val s = Snapshot(entries = entries, settings = stale)
        assertEquals(12000L, budgetExpense(entries))
        assertTrue(s.excludedBudgetEntries(entries).isEmpty())
        assertFalse(s.isRepayment(entries[0]))
    }

    @Test fun accountKindsAreSplitIntoAssetAndDebtGroups() {
        assertTrue(isDebtKind("CREDIT_CARD")); assertTrue(isDebtKind("HUABEI")); assertTrue(isDebtKind("CREDIT")); assertTrue(isDebtKind("DEBT_OTHER"))
        assertFalse(isDebtKind("BANK")); assertFalse(isDebtKind("OTHER")); assertFalse(isDebtKind("CASH"))
        assertEquals(listOf("银行卡", "微信", "支付宝", "现金", "其他"), ASSET_KINDS.values.toList())
        assertEquals(listOf("信用卡", "花呗", "信用支付", "其他"), DEBT_KINDS.values.toList())
        assertEquals("信用卡", ACCOUNT_KINDS["CREDIT_CARD"])
        assertEquals("其他负债", accountKindLabel("DEBT_OTHER"))
    }
}
