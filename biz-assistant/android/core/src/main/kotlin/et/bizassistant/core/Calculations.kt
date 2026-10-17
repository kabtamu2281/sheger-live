package et.bizassistant.core

import java.math.BigDecimal
import java.math.RoundingMode

data class LineItem(
    val description: String,
    val quantity: BigDecimal,
    val unitPrice: Money,
) {
    init {
        require(description.isNotBlank()) { "Line item needs a description" }
        require(quantity.signum() > 0) { "Quantity must be positive" }
        require(!unitPrice.isNegative) { "Unit price cannot be negative" }
    }

    /** quantity × unit price, rounded half-up to the nearest santim. */
    val lineTotal: Money
        get() = Money.fromDecimal(quantity.multiply(unitPrice.toDecimal()))
}

sealed interface Discount {
    data object None : Discount
    data class Fixed(val amount: Money) : Discount
    data class Percent(val basisPoints: Int) : Discount
}

/** A tax line entered by the user. The app does not know or suggest tax rates. */
data class TaxRate(val label: String, val basisPoints: Int) {
    init {
        require(label.isNotBlank()) { "Tax needs a label" }
        require(basisPoints in 0..10_000) { "Tax rate must be between 0% and 100%" }
    }
}

data class TaxAmount(val rate: TaxRate, val amount: Money)

/** A payment that the user has recorded themselves. The app never assumes a payment happened. */
data class RecordedPayment(val amount: Money, val note: String = "") {
    init { require(amount.minor > 0) { "Payment must be positive" } }
}

enum class PaymentStatus { UNPAID, PARTIALLY_PAID, PAID, OVERPAID }

data class DocumentTotals(
    val subtotal: Money,
    val discount: Money,
    val taxableAmount: Money,
    val taxes: List<TaxAmount>,
    val total: Money,
    val amountPaid: Money,
    val balanceDue: Money,
    val status: PaymentStatus,
) {
    val taxTotal: Money get() = taxes.map { it.amount }.sum()
}

/** Deterministic invoice/quotation arithmetic. All results are derived from structured inputs. */
object DocumentCalculator {

    fun calculate(
        items: List<LineItem>,
        discount: Discount = Discount.None,
        taxes: List<TaxRate> = emptyList(),
        payments: List<RecordedPayment> = emptyList(),
    ): DocumentTotals {
        val subtotal = items.map { it.lineTotal }.sum()
        val discountAmount = when (discount) {
            Discount.None -> Money.ZERO
            is Discount.Fixed -> {
                require(!discount.amount.isNegative) { "Discount cannot be negative" }
                minOf(discount.amount, subtotal)
            }
            is Discount.Percent -> {
                require(discount.basisPoints in 0..10_000) { "Discount must be 0–100%" }
                percentOf(subtotal, discount.basisPoints)
            }
        }
        val taxable = subtotal - discountAmount
        val taxAmounts = taxes.map { TaxAmount(it, percentOf(taxable, it.basisPoints)) }
        val total = taxable + taxAmounts.map { it.amount }.sum()
        val paid = payments.map { it.amount }.sum()
        val balance = total - paid
        val status = when {
            paid.isZero -> if (total.isZero) PaymentStatus.PAID else PaymentStatus.UNPAID
            paid < total -> PaymentStatus.PARTIALLY_PAID
            paid == total -> PaymentStatus.PAID
            else -> PaymentStatus.OVERPAID
        }
        return DocumentTotals(subtotal, discountAmount, taxable, taxAmounts, total, paid, balance, status)
    }

    /** amount × bp / 10000, rounded half-up to the nearest santim. */
    fun percentOf(amount: Money, basisPoints: Int): Money =
        Money(
            BigDecimal.valueOf(amount.minor)
                .multiply(BigDecimal.valueOf(basisPoints.toLong()))
                .divide(BigDecimal.valueOf(10_000L), 0, RoundingMode.HALF_UP)
                .longValueExact()
        )
}
