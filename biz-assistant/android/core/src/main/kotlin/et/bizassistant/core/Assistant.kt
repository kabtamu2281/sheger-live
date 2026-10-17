package et.bizassistant.core

/** What the app sends to its own backend for an AI-assisted task. No API keys ever live on the client. */
data class AiRequest(
    val task: TaskType,
    val language: AppLanguage,
    val businessName: String,
    /** Only the fields defined for this task. */
    val inputs: Map<String, String>,
    /** Figures computed by the app (e.g. report totals) that the AI may restate but not change. */
    val facts: Map<String, String> = emptyMap(),
)

sealed interface AiResult {
    data class Success(val text: String, val quota: UsageQuota? = null) : AiResult
    data class Failure(val kind: Kind, val message: String = "", val quota: UsageQuota? = null) : AiResult {
        enum class Kind { UNAVAILABLE, QUOTA_EXCEEDED, CONSENT_REQUIRED, REJECTED }
    }
}

fun interface AiClient {
    fun generate(request: AiRequest): AiResult
}

enum class DraftSource { AI, TEMPLATE, CALCULATED }

sealed interface TaskOutcome {
    /** Required information is missing; ask the user before producing anything. */
    data class NeedsInput(val missing: List<FieldSpec>) : TaskOutcome

    data class Draft(
        val text: String,
        val source: DraftSource,
        /** Explains why a fallback was used, if any. */
        val notice: Str? = null,
        val noticeArgs: List<String> = emptyList(),
        val quota: UsageQuota? = null,
    ) : TaskOutcome
}

/**
 * Orchestrates AI-assisted tasks with strict safety rules:
 *  - asks for missing information first;
 *  - never contacts the AI service without the user's consent;
 *  - falls back to manual templates when the AI is unavailable or the allowance is used up;
 *  - rejects AI drafts that contain numbers not present in the user's inputs or the app's own figures.
 */
class AssistantEngine(
    private val ai: AiClient,
    private val consent: () -> ConsentState,
    private val quota: () -> UsageQuota?,
) {

    fun run(
        task: TaskType,
        inputs: Map<String, String>,
        profile: BusinessProfile,
        lang: AppLanguage,
        summary: PeriodSummary? = null,
    ): TaskOutcome {
        require(task.usesAi) { "$task is calculated locally; use DocumentRenderer" }
        val missing = task.missingFields(inputs)
        if (missing.isNotEmpty()) return TaskOutcome.NeedsInput(missing)

        val cleanInputs = task.fields.associate { it.key to inputs[it.key].orEmpty().trim() }
            .filterValues { it.isNotEmpty() }
        fun template(notice: Str?, args: List<String> = emptyList(), q: UsageQuota? = null) = TaskOutcome.Draft(
            OfflineTemplates.render(task, cleanInputs, profile, lang, summary), DraftSource.TEMPLATE, notice, args, q,
        )

        if (!consent().aiAllowed) return template(Str.FALLBACK_NO_CONSENT)
        val currentQuota = quota()
        if (currentQuota != null && currentQuota.isExhausted) return template(Str.FALLBACK_QUOTA, q = currentQuota)

        val facts = summary?.let { factsFor(it, profile) }.orEmpty()
        val request = AiRequest(task, lang, profile.businessName, cleanInputs, facts)
        val result = try {
            ai.generate(request)
        } catch (e: Exception) {
            AiResult.Failure(AiResult.Failure.Kind.UNAVAILABLE, e.message.orEmpty())
        }

        return when (result) {
            is AiResult.Failure -> when (result.kind) {
                AiResult.Failure.Kind.QUOTA_EXCEEDED -> template(Str.FALLBACK_QUOTA, q = result.quota)
                AiResult.Failure.Kind.CONSENT_REQUIRED -> template(Str.FALLBACK_NO_CONSENT)
                else -> template(Str.FALLBACK_AI_UNAVAILABLE, q = result.quota)
            }
            is AiResult.Success -> {
                val text = result.text.trim()
                if (text.isEmpty()) return template(Str.FALLBACK_AI_UNAVAILABLE, q = result.quota)
                val allowed = cleanInputs.values + facts.values + summary?.factNumbers().orEmpty() +
                    listOf(profile.phone, profile.tin, profile.address, profile.businessName)
                val unsupported = NumberGuard.unsupportedNumbers(text, allowed)
                if (unsupported.isNotEmpty()) {
                    template(Str.FALLBACK_UNVERIFIED_NUMBERS, listOf(unsupported.sorted().joinToString(", ")), result.quota)
                } else {
                    TaskOutcome.Draft(text, DraftSource.AI, quota = result.quota)
                }
            }
        }
    }

    companion object {
        /** Aggregated figures only – individual records are never sent. */
        fun factsFor(summary: PeriodSummary, profile: BusinessProfile): Map<String, String> = buildMap {
            val cur = profile.currency
            put("period_from", summary.from.toString())
            put("period_to", summary.to.toString())
            put("total_sales", summary.totalSales.format(cur))
            put("total_expenses", summary.totalExpenses.format(cur))
            put("sales_minus_expenses", summary.net.format(cur))
            put("number_of_sales", summary.saleCount.toString())
            put("number_of_expenses", summary.expenseCount.toString())
            summary.salesByItem.take(5).forEachIndexed { i, c -> put("top_item_${i + 1}", "${c.category}: ${c.amount.format(cur)} (${c.count})") }
            summary.expensesByCategory.take(5).forEachIndexed { i, c -> put("expense_category_${i + 1}", "${c.category}: ${c.amount.format(cur)} (${c.count})") }
        }
    }
}
