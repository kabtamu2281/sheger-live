package et.bizassistant.core

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Expected values below were worked out by hand (independently of the code under test):
 * every figure is written as a literal, never recomputed with the production formulas.
 */
class CalculationsTest {

    private fun item(desc: String, qty: String, price: String) =
        LineItem(desc, BigDecimal(qty), Money.parse(price)!!)

    @Test
    fun parsesAndFormatsAmounts() {
        assertEquals(125050L, Money.parse("1,250.50")!!.minor)
        assertEquals(125050L, Money.parse("ETB 1 250.5")!!.minor)
        assertEquals(3000L, Money.parse("30 ብር")!!.minor)
        assertEquals(7L, Money.parse("0.07")!!.minor)
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
        assertEquals("ETB 1,234,567.08", Money(123456708).format())
        assertEquals("0.05", Money(5).formatPlain())
        assertEquals("-12.00", Money(-1200).formatPlain())
    }

    @Test
    fun invoiceWithDiscountTaxAndPartialPayment() {
        // 3 × 250.00 = 750.00 ; 2.5 × 120.40 = 301.00 ; 1 × 99.99 = 99.99  → subtotal 1,150.99
        // 10% discount = 115.099 → 115.10 (half-up) ; taxable = 1,035.89
        // 15% tax on 1,035.89 = 155.3835 → 155.38 ; total = 1,191.27
        // paid 500.00 → balance 691.27
        val totals = DocumentCalculator.calculate(
            items = listOf(item("Coffee 1kg", "3", "250"), item("Sugar", "2.5", "120.40"), item("Cups", "1", "99.99")),
            discount = Discount.Percent(1000),
            taxes = listOf(TaxRate("VAT", 1500)),
            payments = listOf(RecordedPayment(Money.parse("500")!!)),
        )
        assertEquals(115099L, totals.subtotal.minor)
        assertEquals(11510L, totals.discount.minor)
        assertEquals(103589L, totals.taxableAmount.minor)
        assertEquals(15538L, totals.taxes.single().amount.minor)
        assertEquals(119127L, totals.total.minor)
        assertEquals(69127L, totals.balanceDue.minor)
        assertEquals(PaymentStatus.PARTIALLY_PAID, totals.status)
    }

    @Test
    fun noTaxUnlessUserAddsOne() {
        val totals = DocumentCalculator.calculate(listOf(item("Injera", "10", "15")))
        assertEquals(15000L, totals.total.minor)
        assertEquals(0, totals.taxes.size)
        assertEquals(PaymentStatus.UNPAID, totals.status)
    }

    @Test
    fun fixedDiscountIsCappedAtSubtotalAndPaidRequiresRecordedPayment() {
        val totals = DocumentCalculator.calculate(
            listOf(item("Bread", "2", "10")),
            discount = Discount.Fixed(Money.parse("50")!!),
        )
        assertEquals(2000L, totals.discount.minor)
        assertEquals(0L, totals.total.minor)

        val paid = DocumentCalculator.calculate(
            listOf(item("Bread", "2", "10")), payments = listOf(RecordedPayment(Money.parse("20")!!)),
        )
        assertEquals(PaymentStatus.PAID, paid.status)
        val over = DocumentCalculator.calculate(
            listOf(item("Bread", "2", "10")), payments = listOf(RecordedPayment(Money.parse("25")!!)),
        )
        assertEquals(PaymentStatus.OVERPAID, over.status)
        assertEquals(-500L, over.balanceDue.minor)
    }

    @Test
    fun multipleTaxLinesAndHalfUpRounding() {
        // 0.005 rounds up: 1 × 0.33 at 50% = 0.165 → 0.17 ; 2% of 0.33 = 0.0066 → 0.01
        val totals = DocumentCalculator.calculate(
            listOf(item("Candy", "1", "0.33")),
            taxes = listOf(TaxRate("A", 5000), TaxRate("B", 200)),
        )
        assertEquals(listOf(17L, 1L), totals.taxes.map { it.amount.minor })
        assertEquals(51L, totals.total.minor)
    }

    @Test
    fun percentAndQuantityParsing() {
        assertEquals(1500, parsePercentToBasisPoints("15"))
        assertEquals(750, parsePercentToBasisPoints("7.5%"))
        assertNull(parsePercentToBasisPoints("101"))
        assertNull(parsePercentToBasisPoints("-2"))
        assertEquals(BigDecimal("2.5"), parseQuantity("2,5"))
        assertNull(parseQuantity("0"))
        assertEquals("7.5%", formatBasisPoints(750))
    }

    @Test
    fun rejectsInvalidLineItems() {
        assertFailsWith<IllegalArgumentException> { LineItem("", BigDecimal.ONE, Money(100)) }
        assertFailsWith<IllegalArgumentException> { LineItem("x", BigDecimal.ZERO, Money(100)) }
        assertFailsWith<IllegalArgumentException> { TaxRate("VAT", 20_000) }
    }
}
