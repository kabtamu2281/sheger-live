package et.bizassistant.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

enum class RecordType { SALE, EXPENSE }

/** A sale or expense the user entered. Summaries are computed only from these records. */
data class BusinessRecord(
    val id: Long = 0,
    val type: RecordType,
    val date: LocalDate,
    val description: String,
    val category: String = "",
    val quantity: BigDecimal = BigDecimal.ONE,
    val unitAmount: Money,
    val customerOrSupplier: String = "",
    val note: String = "",
) {
    init {
        require(description.isNotBlank()) { "Description is required" }
        require(quantity.signum() > 0) { "Quantity must be positive" }
        require(!unitAmount.isNegative) { "Amount cannot be negative" }
    }

    val amount: Money get() = Money.fromDecimal(quantity.multiply(unitAmount.toDecimal()))
}

data class CategoryTotal(val category: String, val amount: Money, val count: Int)

data class PeriodSummary(
    val label: String,
    val from: LocalDate,
    val to: LocalDate,
    val totalSales: Money,
    val totalExpenses: Money,
    val saleCount: Int,
    val expenseCount: Int,
    val salesByItem: List<CategoryTotal>,
    val expensesByCategory: List<CategoryTotal>,
) {
    val net: Money get() = totalSales - totalExpenses
    val isEmpty: Boolean get() = saleCount == 0 && expenseCount == 0

    /** Every number in this summary as a string, for checking AI-written narrative. */
    fun factNumbers(): Set<String> = buildSet {
        listOf(totalSales, totalExpenses, net).forEach { addAll(NumberGuard.variants(it)) }
        add(saleCount.toString()); add(expenseCount.toString())
        (salesByItem + expensesByCategory).forEach {
            addAll(NumberGuard.variants(it.amount)); add(it.count.toString())
        }
        add(from.toString()); add(to.toString())
        add(from.dayOfMonth.toString()); add(from.year.toString()); add(from.monthValue.toString())
        add(to.dayOfMonth.toString()); add(to.year.toString()); add(to.monthValue.toString())
    }
}

object SummaryCalculator {

    fun daily(records: List<BusinessRecord>, day: LocalDate): PeriodSummary =
        summarize(records, day, day, day.toString())

    fun monthly(records: List<BusinessRecord>, month: YearMonth): PeriodSummary =
        summarize(records, month.atDay(1), month.atEndOfMonth(), month.toString())

    fun summarize(records: List<BusinessRecord>, from: LocalDate, to: LocalDate, label: String): PeriodSummary {
        require(!to.isBefore(from)) { "End date is before start date" }
        val inRange = records.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val sales = inRange.filter { it.type == RecordType.SALE }
        val expenses = inRange.filter { it.type == RecordType.EXPENSE }
        return PeriodSummary(
            label = label,
            from = from,
            to = to,
            totalSales = sales.map { it.amount }.sum(),
            totalExpenses = expenses.map { it.amount }.sum(),
            saleCount = sales.size,
            expenseCount = expenses.size,
            salesByItem = group(sales) { it.description },
            expensesByCategory = group(expenses) { it.category.ifBlank { it.description } },
        )
    }

    private fun group(records: List<BusinessRecord>, key: (BusinessRecord) -> String): List<CategoryTotal> =
        records.groupBy { key(it).trim().lowercase() }
            .map { (_, rs) -> CategoryTotal(key(rs.first()).trim(), rs.map { it.amount }.sum(), rs.size) }
            .sortedWith(compareByDescending<CategoryTotal> { it.amount }.thenBy { it.category })
}
