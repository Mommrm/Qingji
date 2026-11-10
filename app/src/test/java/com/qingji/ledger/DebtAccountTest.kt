package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * 资产 / 负债账户分型：总资产只算资产类正余额，负债类只记欠款；
 * 资产 → 负债的转账是「还款」，反向是「借款」，都不计收支。
 */
class DebtAccountTest {
    private fun base() = Snapshot(
        categories = defaultCategories(),
        assets = listOf(
            AssetAccount(id = "bank", name = "工资卡", kind = "BANK", openingBalance = 100_000, pool = POOL_LIQUID),
            AssetAccount(id = "fund", name = "基金账户", kind = "OTHER", openingBalance = 50_000, pool = POOL_INVEST),
            AssetAccount(id = "card", name = "信用卡", kind = "CREDIT_CARD", openingBalance = -30_000, pool = POOL_LIQUID),
            AssetAccount(id = "huabei", name = "花呗", kind = "HUABEI", openingBalance = -5_000, pool = POOL_LIQUID)
        ),
        ready = true
    )

    @Test fun accountKindsArePartitioned() {
        assertEquals(5, ASSET_KINDS.size)
        assertEquals(4, DEBT_KINDS.size)
        assertEquals(9, ACCOUNT_KINDS.size)
        assertTrue(ACCOUNT_KINDS.keys.containsAll(ASSET_KINDS.keys + DEBT_KINDS.keys))
        assertTrue(ASSET_KINDS.keys.none(::isDebtKind))
        assertTrue(DEBT_KINDS.keys.all(::isDebtKind))
    }

    @Test fun totalsSeparateAssetAndDebtByKind() {
        val s = base()
        assertEquals(-30_000L, s.assetBalances()["card"])
        assertEquals(150_000L, s.assetTotal())
        assertEquals(35_000L, s.assetDebtTotal())
        assertEquals(listOf("card" to 30_000L, "huabei" to 5_000L), s.debtAccounts().map { it.first.id to it.second })
    }

    @Test fun debtAccountPositiveBalanceNeverCountsAsAsset() {
        // 意外溢缴：负债账户余额为正，既不计入总资产，也不计入负债
        val s = base().copy(assets = base().assets.map { if (it.id == "card") it.copy(openingBalance = 2_000) else it })
        assertEquals(150_000L, s.assetTotal())
        assertEquals(5_000L, s.assetDebtTotal())
        assertTrue(s.debtAccounts().none { it.first.id == "card" })
    }

    @Test fun assetAccountNegativeBalanceNeverCountsAsDebt() {
        val s = base().copy(assets = base().assets.map { if (it.id == "bank") it.copy(openingBalance = -1_000) else it })
        assertEquals(50_000L, s.assetTotal())
        assertEquals(35_000L, s.assetDebtTotal())
        assertEquals(2, s.debtAccounts().size)
    }

    @Test fun slicesOnlyContainTheirOwnKind() {
        val s = base()
        assertEquals(listOf("BANK", "OTHER"), s.assetSlices().map { it.id }.sorted())
        assertEquals(listOf("card", "huabei"), s.debtSlices().map { it.id })
        assertEquals(150_000.0, s.assetSlices().sumOf { it.value }, 0.0)
        assertEquals(35_000.0, s.debtSlices().sumOf { it.value }, 0.0)
    }

    @Test fun repaymentTransferReducesBothAssetAndDebt() {
        val s = base().copy(transfers = listOf(AssetTransfer(id = "pay", fromAssetId = "bank", toAssetId = "card", amount = 10_000, date = LocalDate.now().toString())))
        assertEquals(90_000L, s.assetBalances()["bank"])
        assertEquals(-20_000L, s.assetBalances()["card"])
        assertEquals(140_000L, s.assetTotal())
        assertEquals(25_000L, s.assetDebtTotal())
        assertEquals(0L, totals(s.entries).expense) // 还款不计收支
    }

    @Test fun borrowingFromDebtAccountIncreasesDebt() {
        val s = base().copy(transfers = listOf(AssetTransfer(id = "cash", fromAssetId = "card", toAssetId = "bank", amount = 10_000, date = LocalDate.now().toString())))
        assertEquals(-40_000L, s.assetBalances()["card"])
        assertEquals(110_000L, s.assetBalances()["bank"])
        assertEquals(160_000L, s.assetTotal())
        assertEquals(45_000L, s.assetDebtTotal())
    }

    @Test fun poolSubtotalsIgnoreDebtAccounts() {
        val s = base()
        assertEquals(100_000L, s.assetTotal(POOL_LIQUID))
        assertEquals(50_000L, s.assetTotal(POOL_INVEST))
        assertEquals(35_000L, s.assetDebtTotal(POOL_LIQUID))
        assertEquals(0L, s.assetDebtTotal(POOL_INVEST))
        assertEquals(s.assetTotal(), s.assetTotal(POOL_LIQUID) + s.assetTotal(POOL_INVEST))
    }

    @Test fun defaultAssetNeverPicksDebtAccount() {
        val s = base()
        assertFalse(isDebtKind(s.assets.first { it.id == s.defaultAssetId() }.kind))
    }

    @Test fun debtOtherHasDistinctDefaultName() {
        assertEquals("其他", suggestedAssetName("OTHER", emptyList()))
        assertEquals("其他负债", suggestedAssetName("DEBT_OTHER", emptyList()))
        assertEquals("其他负债", suggestedAssetName("DEBT_OTHER", listOf(AssetAccount(name = "其他", kind = "OTHER"))))
    }
}
