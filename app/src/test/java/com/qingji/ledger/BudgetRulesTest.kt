package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

class BudgetRulesTest {
    private fun expense(id: String, amount: Long, assetId: String? = "bank", category: String = "expense-0") =
        Entry(id = id, type = EXPENSE, amount = amount, categoryId = category, assetId = assetId, date = "2026-11-09")
    private fun refund(id: String, amount: Long, original: String, assetId: String? = "bank") =
        Entry(id = id, type = REFUND, amount = amount, categoryId = "expense-0", refundOf = original, assetId = assetId, date = "2026-11-09")

    @Test fun repaymentIsExcludedFromBudgetButStillCountsInTotals() {
        val entries = listOf(expense("e1", 10000), expense("repay", 20000))
        val s = Snapshot(entries = entries, settings = Settings(budgetExcludedEntries = setOf("repay"), repaymentEntries = setOf("repay")))
        assertEquals(10000L, budgetExpense(entries, s.settings.budgetExcludedEntries))
        assertEquals(30000L, totals(entries).expense)
        assertTrue(s.isRepayment(entries[1]))
        assertFalse(s.isRepayment(entries[0]))
        assertEquals(listOf("repay"), s.excludedBudgetEntries(entries).map { it.id })
    }

    @Test fun emptyExclusionKeepsLegacyNetExpense() {
        val entries = listOf(expense("e1", 10000), refund("r1", 2500, "e1", "cash"))
        assertEquals(totals(entries).expense, budgetExpense(entries, emptySet()))
        assertEquals(7500L, budgetExpense(entries, emptySet()))
    }

    @Test fun refundFollowsOriginalExpenseFlagNotItsOwn() {
        val entries = listOf(
            expense("e1", 10000), refund("r1", 3000, "e1", "cash"),
            expense("e2", 8000), refund("r2", 2000, "e2", "card")
        )
        assertEquals(6000L, budgetExpense(entries, setOf("e1")))
        assertEquals(0L, budgetExpense(entries, setOf("e1", "e2")))
    }

    @Test fun accountTypeNeverAffectsBudgetAnymore() {
        val entries = listOf(expense("card", 5000, "credit-account"), expense("cash", 7000, "cash"))
        val s = Snapshot(entries = entries, assets = listOf(AssetAccount(id = "credit-account", name = "信用卡", kind = "CREDIT_CARD")))
        assertTrue(s.settings.budgetExcludedEntries.isEmpty())
        assertEquals(12000L, budgetExpense(entries, s.settings.budgetExcludedEntries))
    }

    @Test fun deletedEntriesNeverCountTowardsBudget() {
        val entries = listOf(expense("e1", 10000).copy(deletedAt = 1L), expense("e2", 5000))
        assertEquals(5000L, budgetExpense(entries, setOf("e1")))
    }

    @Test fun categoryBudgetUsesTheSameExclusionRule() {
        val entries = listOf(
            expense("e1", 10000, category = "expense-1"),
            expense("e2", 7000, category = "expense-1"),
            expense("e3", 2000, category = "expense-2")
        )
        assertEquals(10000L, budgetExpense(entries.filter { it.categoryId == "expense-1" }, setOf("e2")))
    }

    @Test fun excludedListOnlyReturnsLiveExpenses() {
        val entries = listOf(
            expense("e1", 100), expense("e2", 200).copy(deletedAt = 1L),
            Entry(id = "i1", type = INCOME, amount = 300, categoryId = "income-0"),
            refund("r1", 50, "e1")
        )
        val s = Snapshot(entries = entries, settings = Settings(budgetExcludedEntries = setOf("e1", "e2", "i1", "r1")))
        assertEquals(listOf("e1"), s.excludedBudgetEntries(entries).map { it.id })
    }

    @Test fun defaultsAreOptIn() {
        val s = Settings()
        assertTrue(s.budgetExcludedEntries.isEmpty())
        assertTrue(s.repaymentEntries.isEmpty())
        assertEquals(1234L, budgetExpense(listOf(expense("e1", 1234)), s.budgetExcludedEntries))
    }

    @Test fun creditAssetKindsRemainAvailableAsAccountLabels() {
        assertTrue(isCreditAsset("CREDIT_CARD")); assertTrue(isCreditAsset("HUABEI")); assertTrue(isCreditAsset("CREDIT"))
        assertFalse(isCreditAsset("BANK")); assertFalse(isCreditAsset("OTHER"))
        assertEquals("信用卡", ASSET_KINDS["CREDIT_CARD"]); assertEquals("花呗", ASSET_KINDS["HUABEI"])
    }
}
