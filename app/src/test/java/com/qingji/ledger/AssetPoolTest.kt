package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

/**
 * 资产口径：总资产只累加正余额账户，负债单独统计；
 * 账户按流动资金 / 理财资金分池，两池小计之和 = 总资产。
 */
class AssetPoolTest {
    private fun base() = Snapshot(
        categories = defaultCategories(),
        assets = listOf(
            AssetAccount(id = "bank", name = "工资卡", openingBalance = 100_000, pool = POOL_LIQUID),
            AssetAccount(id = "fund", name = "基金账户", kind = "OTHER", openingBalance = 50_000, pool = POOL_INVEST),
            AssetAccount(id = "card", name = "信用卡", kind = "CREDIT_CARD", openingBalance = 0, pool = POOL_LIQUID)
        ),
        entries = listOf(Entry(id = "spend", type = EXPENSE, amount = 11_000, categoryId = "expense-0", assetId = "card", date = "2026-11-09", pool = POOL_LIQUID)),
        ready = true
    )

    @Test fun totalAssetsIgnoreDebtButDebtIsReportedSeparately() {
        val s = base()
        assertEquals(-11_000L, s.assetBalances()["card"])
        assertEquals(150_000L, s.assetTotal())
        assertEquals(11_000L, s.assetDebtTotal())
        assertEquals(listOf("card" to 11_000L), s.debtAccounts().map { it.first.id to it.second })
        // 余额联动仍然如常，只是汇总口径变了
        assertEquals(139_000L, s.assetTotal() - s.assetDebtTotal())
    }

    @Test fun poolSubtotalsAddUpToTotal() {
        val s = base()
        assertEquals(100_000L, s.assetTotal(POOL_LIQUID))
        assertEquals(50_000L, s.assetTotal(POOL_INVEST))
        assertEquals(s.assetTotal(), s.assetTotal(POOL_LIQUID) + s.assetTotal(POOL_INVEST))
        assertEquals(11_000L, s.assetDebtTotal(POOL_LIQUID))
        assertEquals(0L, s.assetDebtTotal(POOL_INVEST))
    }

    @Test fun transferBetweenPoolsKeepsTotalButMovesSubtotals() {
        val s = base().copy(transfers = listOf(AssetTransfer(fromAssetId = "bank", toAssetId = "fund", amount = 20_000, date = "2026-11-09")))
        assertEquals(80_000L, s.assetTotal(POOL_LIQUID))
        assertEquals(70_000L, s.assetTotal(POOL_INVEST))
        assertEquals(150_000L, s.assetTotal())
        assertEquals(0L, totals(s.entries).income)
    }

    @Test fun debtSlicesListEachLiabilityAccountByAbsoluteAmount() {
        val s = base().copy(assets = base().assets + AssetAccount(id = "huabei", name = "花呗", kind = "HUABEI", openingBalance = 0, pool = POOL_LIQUID),
            entries = base().entries + Entry(id = "spend2", type = EXPENSE, amount = 4_000, categoryId = "expense-0", assetId = "huabei", date = "2026-11-09"))
        val slices = s.debtSlices()
        assertEquals(listOf("信用卡", "花呗"), slices.map { it.label })
        assertEquals(listOf(11_000.0, 4_000.0), slices.map { it.value })
        assertEquals(listOf("card", "huabei"), slices.map { it.id })
        assertEquals(15_000L, s.assetDebtTotal())
        assertEquals(150_000L, s.assetTotal())
    }

    @Test fun assetSlicesStillOnlyContainPositiveBalances() {
        val slices = base().assetSlices()
        assertEquals(2, slices.size)
        assertTrue(slices.all { it.value > 0 })
        assertEquals(150_000.0, slices.sumOf { it.value }, 0.0)
    }

    @Test fun archivedAccountsAreExcludedFromEveryTotal() {
        val s = base().copy(assets = base().assets.map { if (it.id == "fund") it.copy(archived = true, openingBalance = 0) else it })
        assertEquals(100_000L, s.assetTotal())
        assertEquals(0L, s.assetTotal(POOL_INVEST)) // 归档账户不参与任何资金池小计
    }

    @Test fun poolNormalizationAndLabels() {
        assertEquals(POOL_INVEST, normalizePool(POOL_INVEST))
        assertEquals(POOL_LIQUID, normalizePool("garbage"))
        assertEquals("流动资金", poolLabel(POOL_LIQUID))
        assertEquals("理财资金", poolLabel(POOL_INVEST))
        assertEquals(listOf("流动资金", "理财资金"), ASSET_POOLS.values.toList())
    }

    @Test fun entryCarriesItsOwnBudgetFlagsByDefault() {
        val e = Entry(id = "e", type = EXPENSE, amount = 100, categoryId = "expense-0")
        assertTrue(e.inBudget)
        assertFalse(e.repayment)
        assertEquals(POOL_LIQUID, e.pool)
    }
}
