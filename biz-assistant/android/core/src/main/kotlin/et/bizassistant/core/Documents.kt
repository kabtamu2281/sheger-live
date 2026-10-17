package et.bizassistant.core

import java.time.LocalDate

data class BusinessProfile(
    val businessName: String,
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val tin: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
)

enum class DocKind { INVOICE, QUOTATION }

/** Structured invoice or quotation. The text form is always rendered from this, never written by AI. */
data class SalesDocument(
    val kind: DocKind,
    val number: String,
    val date: LocalDate,
    val customerName: String,
    val customerPhone: String = "",
    val items: List<LineItem>,
    val discount: Discount = Discount.None,
    val taxes: List<TaxRate> = emptyList(),
    val payments: List<RecordedPayment> = emptyList(),
    val dueOrValidUntil: LocalDate? = null,
    val notes: String = "",
) {
    val totals: DocumentTotals
        get() = DocumentCalculator.calculate(items, discount, taxes, if (kind == DocKind.INVOICE) payments else emptyList())
}

/** Renders structured data into plain-text documents in the chosen language. */
object DocumentRenderer {

    fun render(doc: SalesDocument, profile: BusinessProfile, lang: AppLanguage): String {
        val t = doc.totals
        val cur = profile.currency
        val sb = StringBuilder()
        sb.appendLine(profile.businessName)
        listOf(profile.address, profile.phone).filter { it.isNotBlank() }.forEach { sb.appendLine(it) }
        if (profile.tin.isNotBlank()) sb.appendLine("TIN: ${profile.tin}")
        sb.appendLine()
        sb.appendLine((if (doc.kind == DocKind.INVOICE) Str.DOC_INVOICE else Str.DOC_QUOTATION).get(lang) + "  #" + doc.number)
        sb.appendLine("${Str.DATE.get(lang)}: ${doc.date}")
        doc.dueOrValidUntil?.let {
            val label = if (doc.kind == DocKind.INVOICE) Str.DOC_DUE else Str.DOC_VALID_UNTIL
            sb.appendLine("${label.get(lang)}: $it")
        }
        sb.appendLine("${Str.DOC_BILL_TO.get(lang)}: ${doc.customerName}" + if (doc.customerPhone.isNotBlank()) " (${doc.customerPhone})" else "")
        sb.appendLine()
        sb.appendLine("${Str.DOC_ITEM.get(lang)} | ${Str.DOC_QTY.get(lang)} | ${Str.DOC_UNIT_PRICE.get(lang)} | ${Str.DOC_AMOUNT.get(lang)}")
        doc.items.forEachIndexed { i, item ->
            sb.appendLine("${i + 1}. ${item.description} | ${item.quantity.stripTrailingZeros().toPlainString()} | ${item.unitPrice.formatPlain()} | ${item.lineTotal.formatPlain()}")
        }
        sb.appendLine()
        sb.appendLine("${Str.DOC_SUBTOTAL.get(lang)}: ${t.subtotal.format(cur)}")
        if (!t.discount.isZero) sb.appendLine("${Str.DOC_DISCOUNT.get(lang)}: -${t.discount.format(cur)}")
        t.taxes.forEach { sb.appendLine("${it.rate.label} (${formatBasisPoints(it.rate.basisPoints)}): ${it.amount.format(cur)}") }
        sb.appendLine("${Str.DOC_TOTAL.get(lang)}: ${t.total.format(cur)}")
        if (doc.kind == DocKind.INVOICE) {
            sb.appendLine("${Str.DOC_PAID.get(lang)}: ${t.amountPaid.format(cur)}")
            sb.appendLine("${Str.DOC_BALANCE.get(lang)}: ${t.balanceDue.format(cur)}")
            sb.appendLine("${Str.DOC_STATUS.get(lang)}: ${statusLabel(t.status).get(lang)}")
        }
        if (doc.notes.isNotBlank()) {
            sb.appendLine()
            sb.appendLine(doc.notes.trim())
        }
        sb.appendLine()
        sb.append(Str.DOC_THANKS.get(lang))
        return sb.toString()
    }

    fun statusLabel(status: PaymentStatus): Str = when (status) {
        PaymentStatus.UNPAID -> Str.STATUS_UNPAID
        PaymentStatus.PARTIALLY_PAID -> Str.STATUS_PARTIAL
        PaymentStatus.PAID -> Str.STATUS_PAID
        PaymentStatus.OVERPAID -> Str.STATUS_OVERPAID
    }

    fun renderSummary(summary: PeriodSummary, profile: BusinessProfile, lang: AppLanguage, title: Str): String {
        val cur = profile.currency
        val sb = StringBuilder()
        sb.appendLine(profile.businessName)
        sb.appendLine(title.get(lang))
        val period = if (summary.from == summary.to) summary.from.toString() else "${summary.from} – ${summary.to}"
        sb.appendLine("${Str.SUM_PERIOD.get(lang)}: $period")
        sb.appendLine()
        if (summary.isEmpty) {
            sb.append(Str.NO_RECORDS_FOR_PERIOD.get(lang))
            return sb.toString()
        }
        sb.appendLine("${Str.SUM_SALES.get(lang)}: ${summary.totalSales.format(cur)}")
        sb.appendLine("${Str.SUM_EXPENSES.get(lang)}: ${summary.totalExpenses.format(cur)}")
        sb.appendLine("${Str.SUM_NET.get(lang)}: ${summary.net.format(cur)}")
        sb.appendLine("${Str.SUM_SALE_COUNT.get(lang)}: ${summary.saleCount}")
        sb.appendLine("${Str.SUM_EXPENSE_COUNT.get(lang)}: ${summary.expenseCount}")
        if (summary.salesByItem.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(Str.SUM_TOP_ITEMS.get(lang) + ":")
            summary.salesByItem.forEach { sb.appendLine("• ${it.category}: ${it.amount.format(cur)} (${it.count})") }
        }
        if (summary.expensesByCategory.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine(Str.SUM_EXPENSES_BY_CATEGORY.get(lang) + ":")
            summary.expensesByCategory.forEach { sb.appendLine("• ${it.category}: ${it.amount.format(cur)} (${it.count})") }
        }
        return sb.toString().trimEnd()
    }

    /** Minimal HTML for PDF/print export; content is escaped. */
    fun toHtml(title: String, body: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>${esc(title)}</title>" +
            "<style>body{font-family:sans-serif;white-space:pre-wrap;line-height:1.5;margin:32px}</style></head>" +
            "<body>${esc(body)}</body></html>"
    }
}
