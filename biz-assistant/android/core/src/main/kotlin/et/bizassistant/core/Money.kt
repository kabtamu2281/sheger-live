package et.bizassistant.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money is always held as a whole number of minor units (santim for ETB: 100 santim = 1 birr).
 * No floating point is used anywhere in financial arithmetic.
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {
    operator fun plus(other: Money) = Money(Math.addExact(minor, other.minor))
    operator fun minus(other: Money) = Money(Math.subtractExact(minor, other.minor))
    override fun compareTo(other: Money) = minor.compareTo(other.minor)
    val isNegative get() = minor < 0
    val isZero get() = minor == 0L

    fun toDecimal(): BigDecimal = BigDecimal.valueOf(minor, 2)

    /** "1,250.50" – grouping with commas, always two decimals. */
    fun formatPlain(): String {
        val abs = toDecimal().abs().setScale(2, RoundingMode.UNNECESSARY)
        val whole = abs.toBigInteger().toString()
        val frac = abs.toPlainString().substringAfter('.')
        val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
        return (if (minor < 0) "-" else "") + grouped + "." + frac
    }

    /** Formats with the currency code, e.g. "ETB 1,250.50". */
    fun format(currency: String = DEFAULT_CURRENCY): String = "$currency ${formatPlain()}"

    companion object {
        const val DEFAULT_CURRENCY = "ETB"
        val ZERO = Money(0)

        fun ofMajor(major: Long) = Money(Math.multiplyExact(major, 100L))

        fun fromDecimal(value: BigDecimal): Money =
            Money(value.setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact())

        /**
         * Parses user-typed amounts such as "1250", "1,250.5", "1 250.50" or "ETB 30".
         * Returns null for anything ambiguous: negative values, more than two decimals,
         * letters other than a currency prefix/suffix, or empty input.
         */
        fun parse(input: String): Money? {
            var s = input.trim()
            for (token in listOf("ETB", "Br", "birr", "ብር")) {
                s = s.removePrefix(token).removeSuffix(token).trim()
            }
            s = s.replace(",", "").replace(" ", "").replace(" ", "")
            if (s.isEmpty() || !AMOUNT_REGEX.matches(s)) return null
            return try {
                fromDecimal(BigDecimal(s))
            } catch (e: ArithmeticException) {
                null
            } catch (e: NumberFormatException) {
                null
            }
        }

        private val AMOUNT_REGEX = Regex("""\d{1,13}(\.\d{1,2})?""")
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

/** Parses a quantity like "3", "2.5" or "0.25". Up to 3 decimal places, must be positive. */
fun parseQuantity(input: String): BigDecimal? {
    val s = input.trim().replace(",", ".")
    if (!Regex("""\d{1,7}(\.\d{1,3})?""").matches(s)) return null
    val q = BigDecimal(s)
    return if (q.signum() > 0) q else null
}

/**
 * Parses a percentage such as "15" or "7.5" into basis points (1% = 100 bp).
 * The app never supplies a default tax rate: the user must type the rate they are required to use.
 */
fun parsePercentToBasisPoints(input: String): Int? {
    val s = input.trim().removeSuffix("%").trim()
    if (!Regex("""\d{1,3}(\.\d{1,2})?""").matches(s)) return null
    val bp = BigDecimal(s).movePointRight(2).intValueExact()
    return if (bp in 0..10_000) bp else null
}

fun formatBasisPoints(bp: Int): String =
    BigDecimal.valueOf(bp.toLong(), 2).stripTrailingZeros().toPlainString() + "%"
