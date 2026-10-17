package et.bizassistant.core

/**
 * Checks AI-written text for numbers that were not supplied by the user or computed by the app.
 * Language models must never invent sales figures, prices, tax rates or dates; any number in a
 * draft that cannot be traced back to the inputs is reported so the app can reject or flag it.
 */
object NumberGuard {

    private val NUMBER = Regex("""\d[\d,]*(?:\.\d+)?""")

    /** Canonical form: no grouping commas, no trailing fractional zeros. "1,250.50" → "1250.5". */
    fun normalize(raw: String): String {
        var s = raw.replace(",", "")
        if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
        s = s.trimStart('0').ifEmpty { "0" }
        if (s.startsWith('.')) s = "0$s"
        return s
    }

    fun extract(text: String): Set<String> =
        NUMBER.findAll(text).map { normalize(it.value.trimEnd(',')) }.toSet()

    fun variants(money: Money): Set<String> = setOf(normalize(money.toDecimal().toPlainString()))

    /** Numbers found in [output] that do not appear in [allowed] (already normalized or raw). */
    fun unsupportedNumbers(output: String, allowed: Collection<String>): Set<String> {
        val allowedNorm = allowed.flatMap { extract(it) + normalize(it) }.toSet()
        return extract(output) - allowedNorm
    }
}
