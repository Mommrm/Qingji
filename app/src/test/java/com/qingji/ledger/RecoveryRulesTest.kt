package com.qingji.ledger

import org.junit.Assert.*
import org.junit.Test

class RecoveryRulesTest {
    private fun expense(id: String, deleted: Long? = null) = Entry(id = id, type = EXPENSE, amount = 1000, categoryId = "expense-0", date = "2026-11-01", deletedAt = deleted)
    private fun refund(id: String, origin: String, deleted: Long? = null) = Entry(id = id, type = REFUND, amount = 500, categoryId = "expense-0", date = "2026-11-02", refundOf = origin, deletedAt = deleted)

    @Test fun plainEntryRestoresAlone() {
        val entries = listOf(expense("e", 1L), refund("r", "e", 2L))
        assertEquals(listOf("e"), restorationOrder(entries, "e"))
    }

    @Test fun refundWithLiveOriginRestoresAlone() {
        val entries = listOf(expense("e"), refund("r", "e", 2L))
        assertEquals(listOf("r"), restorationOrder(entries, "r"))
    }

    @Test fun refundWithDeletedOriginRestoresOriginFirst() {
        val entries = listOf(expense("e", 1L), refund("r", "e", 2L))
        assertEquals(listOf("e", "r"), restorationOrder(entries, "r"))
    }

    @Test fun missingIdReturnsEmpty() {
        assertTrue(restorationOrder(listOf(expense("e", 1L)), "missing").isEmpty())
    }

    @Test fun restoredEntriesRejoinTotals() {
        val entries = listOf(expense("e", 1L), refund("r", "e", 2L))
        assertEquals(0L, totals(entries).expense)
        val restored = entries.map { it.copy(deletedAt = null) }
        assertEquals(500L, totals(restored).expense)
    }

    @Test fun searchSpansAllMonthsAndIgnoresCase() {
        val a = Entry(id = "a", type = EXPENSE, amount = 1000, categoryId = "expense-0", date = "2026-10-05", note = "coffee")
        val b = Entry(id = "b", type = EXPENSE, amount = 2000, categoryId = "expense-0", date = "2026-11-09", note = "COFFEE")
        val c = Entry(id = "c", type = EXPENSE, amount = 3000, categoryId = "expense-0", date = "2026-11-09", note = "无备注")
        val s = Snapshot(entries = listOf(a, b, c), categories = listOf(Category(id = "expense-0", name = "餐饮", type = EXPENSE)))
        val october = s.live().filter { it.date.startsWith("2026-10") }
        assertEquals(1, october.size)
        assertEquals(listOf("a", "b"), s.filterByQuery(s.live(), "cOfFeE").map { it.id })
        assertEquals(listOf("a", "b", "c"), s.filterByQuery(s.live(), "餐饮").map { it.id })
    }
}
