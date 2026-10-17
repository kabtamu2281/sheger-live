package et.bizassistant.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryTest {
    private val d1 = LocalDate.of(2026, 10, 1)
    private val d2 = LocalDate.of(2026, 10, 15)
    private val other = LocalDate.of(2026, 9, 30)

    private val records = listOf(
        BusinessRecord(type = RecordType.SALE, date = d1, description = "Coffee", quantity = BigDecimal("4"), unitAmount = Money.parse("35")!!),
        BusinessRecord(type = RecordType.SALE, date = d1, description = "coffee ", quantity = BigDecimal("2"), unitAmount = Money.parse("35")!!),
        BusinessRecord(type = RecordType.SALE, date = d2, description = "Tea", unitAmount = Money.parse("20.50")!!),
        BusinessRecord(type = RecordType.EXPENSE, date = d1, description = "Milk", category = "Supplies", unitAmount = Money.parse("80")!!),
        BusinessRecord(type = RecordType.EXPENSE, date = d2, description = "Rent", category = "Rent", unitAmount = Money.parse("3,000")!!),
        BusinessRecord(type = RecordType.SALE, date = other, description = "Coffee", unitAmount = Money.parse("999")!!),
    )

    @Test
    fun dailySummary() {
        // Sales on 1 Oct: 4×35 + 2×35 = 210.00 ; expenses 80.00 ; net 130.00
        val s = SummaryCalculator.daily(records, d1)
        assertEquals(21000L, s.totalSales.minor)
        assertEquals(8000L, s.totalExpenses.minor)
        assertEquals(13000L, s.net.minor)
        assertEquals(2, s.saleCount)
        assertEquals(1, s.salesByItem.size) // "Coffee" and "coffee " grouped
        assertEquals(21000L, s.salesByItem.single().amount.minor)
    }

    @Test
    fun monthlySummaryExcludesOtherMonths() {
        // Oct sales: 210.00 + 20.50 = 230.50 ; expenses 80 + 3,000 = 3,080.00 ; net -2,849.50
        val s = SummaryCalculator.monthly(records, YearMonth.of(2026, 10))
        assertEquals(23050L, s.totalSales.minor)
        assertEquals(308000L, s.totalExpenses.minor)
        assertEquals(-284950L, s.net.minor)
        assertEquals(listOf("Rent", "Supplies"), s.expensesByCategory.map { it.category })
    }

    @Test
    fun emptyPeriodSaysSoInsteadOfEstimating() {
        val s = SummaryCalculator.daily(records, LocalDate.of(2026, 1, 1))
        assertTrue(s.isEmpty)
        val text = DocumentRenderer.renderSummary(s, BusinessProfile("Abebe Shop"), AppLanguage.ENGLISH, Str.TASK_DAILY_SUMMARY)
        assertTrue(text.contains(Str.NO_RECORDS_FOR_PERIOD.en))
        assertTrue(!text.contains("ETB"))
    }
}
