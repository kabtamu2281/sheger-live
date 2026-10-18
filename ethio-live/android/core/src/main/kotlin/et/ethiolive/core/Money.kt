package et.ethiolive.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Formatting for real money (minor units) and virtual currencies. Never uses floating point for amounts. */
object Money {
    private val symbols = DecimalFormatSymbols(Locale.US)

    /** 135000 -> "ETB 1,350.00" (English) or "1,350.00 ብር" (Amharic). Other currencies use their ISO code. */
    fun format(minor: Long, currency: String = "ETB", language: AppLanguage = AppLanguage.ENGLISH): String {
        val major = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
        val number = DecimalFormat("#,##0.00", symbols).format(major)
        return if (currency.equals("ETB", ignoreCase = true) && language == AppLanguage.AMHARIC) "$number ብር"
        else "${currency.uppercase()} $number"
    }

    /** Compact virtual-coin counts for overlays: 950, 12.5K, 3.2M. */
    fun compact(value: Long): String {
        val abs = kotlin.math.abs(value)
        val sign = if (value < 0) "-" else ""
        return when {
            abs < 1_000 -> "$sign$abs"
            abs < 1_000_000 -> sign + trim(abs / 100 / 10.0) + "K"
            abs < 1_000_000_000 -> sign + trim(abs / 100_000 / 10.0) + "M"
            else -> sign + trim(abs / 100_000_000 / 10.0) + "B"
        }
    }

    /** Grouped full number: 1234567 -> "1,234,567". */
    fun grouped(value: Long): String = DecimalFormat("#,##0", symbols).format(value)

    private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    /** Estimated payout (ETB minor units) for beans, mirroring the server's integer maths exactly. */
    fun payoutEstimate(beans: Long, etbMinorPer100Beans: Long, feeBps: Long): PayoutEstimate {
        val gross = beans * etbMinorPer100Beans / 100
        val fee = gross * feeBps / 10_000
        return PayoutEstimate(gross, fee, gross - fee)
    }
}

data class PayoutEstimate(val grossMinor: Long, val feeMinor: Long, val netMinor: Long)
