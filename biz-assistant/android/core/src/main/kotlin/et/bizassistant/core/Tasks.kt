package et.bizassistant.core

/** How a task produces its result. */
enum class TaskMode {
    /** Built entirely by app code from structured data (invoices, quotations, summaries). */
    DETERMINISTIC,
    /** Wording drafted by the AI service, with an offline template as fallback. */
    AI_ASSISTED,
}

enum class FieldKind { SHORT_TEXT, LONG_TEXT, LANGUAGE, CHOICE }

data class FieldSpec(
    val key: String,
    val label: Str,
    val kind: FieldKind = FieldKind.SHORT_TEXT,
    val required: Boolean = true,
    val choices: List<String> = emptyList(),
    val hint: Str? = null,
)

enum class TaskType(val title: Str, val mode: TaskMode, val fields: List<FieldSpec>) {
    INVOICE(Str.TASK_INVOICE, TaskMode.DETERMINISTIC, emptyList()),
    QUOTATION(Str.TASK_QUOTATION, TaskMode.DETERMINISTIC, emptyList()),
    DAILY_SUMMARY(Str.TASK_DAILY_SUMMARY, TaskMode.DETERMINISTIC, emptyList()),
    MONTHLY_SUMMARY(Str.TASK_MONTHLY_SUMMARY, TaskMode.DETERMINISTIC, emptyList()),
    CUSTOMER_MESSAGE(
        Str.TASK_CUSTOMER_MESSAGE, TaskMode.AI_ASSISTED,
        listOf(
            FieldSpec(Fields.CUSTOMER_NAME, Str.FIELD_CUSTOMER_NAME),
            FieldSpec(
                Fields.PURPOSE, Str.FIELD_PURPOSE, FieldKind.CHOICE,
                choices = listOf("payment_reminder", "thank_you", "order_ready", "new_product", "apology", "other"),
            ),
            FieldSpec(Fields.DETAILS, Str.FIELD_DETAILS, FieldKind.LONG_TEXT, hint = Str.HINT_DETAILS_FACTS),
            FieldSpec(Fields.TONE, Str.FIELD_TONE, FieldKind.CHOICE, required = false, choices = listOf("friendly", "formal", "short")),
        ),
    ),
    PRODUCT_DESCRIPTION(
        Str.TASK_PRODUCT_DESCRIPTION, TaskMode.AI_ASSISTED,
        listOf(
            FieldSpec(Fields.PRODUCT_NAME, Str.FIELD_PRODUCT_NAME),
            FieldSpec(Fields.FEATURES, Str.FIELD_FEATURES, FieldKind.LONG_TEXT, hint = Str.HINT_DETAILS_FACTS),
            FieldSpec(Fields.PRICE, Str.FIELD_PRICE_OPTIONAL, required = false),
            FieldSpec(Fields.AUDIENCE, Str.FIELD_AUDIENCE, required = false),
        ),
    ),
    TRANSLATION(
        Str.TASK_TRANSLATION, TaskMode.AI_ASSISTED,
        listOf(
            FieldSpec(Fields.SOURCE_TEXT, Str.FIELD_SOURCE_TEXT, FieldKind.LONG_TEXT),
            FieldSpec(Fields.TARGET_LANGUAGE, Str.FIELD_TARGET_LANGUAGE, FieldKind.LANGUAGE),
        ),
    ),
    BUSINESS_LETTER(
        Str.TASK_BUSINESS_LETTER, TaskMode.AI_ASSISTED,
        listOf(
            FieldSpec(Fields.RECIPIENT, Str.FIELD_RECIPIENT),
            FieldSpec(
                Fields.LETTER_TYPE, Str.FIELD_LETTER_TYPE, FieldKind.CHOICE,
                choices = listOf("request", "complaint", "introduction", "confirmation", "price_change", "other"),
            ),
            FieldSpec(Fields.SUBJECT, Str.FIELD_SUBJECT),
            FieldSpec(Fields.DETAILS, Str.FIELD_DETAILS, FieldKind.LONG_TEXT, hint = Str.HINT_DETAILS_FACTS),
        ),
    ),
    REPORT(
        Str.TASK_REPORT, TaskMode.AI_ASSISTED,
        listOf(
            FieldSpec(Fields.REPORT_PERIOD, Str.FIELD_REPORT_PERIOD, FieldKind.CHOICE, choices = listOf("today", "this_month", "last_month")),
            FieldSpec(Fields.AUDIENCE, Str.FIELD_REPORT_AUDIENCE, required = false),
            FieldSpec(Fields.DETAILS, Str.FIELD_REPORT_NOTES, FieldKind.LONG_TEXT, required = false),
        ),
    );

    val usesAi: Boolean get() = mode == TaskMode.AI_ASSISTED

    /** Stable identifier shared with the backend allow-list. */
    val apiName: String get() = name.lowercase()

    /** Returns the required fields that are missing or blank, in display order. */
    fun missingFields(inputs: Map<String, String>): List<FieldSpec> =
        fields.filter { it.required && inputs[it.key].isNullOrBlank() }
}

object Fields {
    const val CUSTOMER_NAME = "customer_name"
    const val PURPOSE = "purpose"
    const val DETAILS = "details"
    const val TONE = "tone"
    const val PRODUCT_NAME = "product_name"
    const val FEATURES = "features"
    const val PRICE = "price"
    const val AUDIENCE = "audience"
    const val SOURCE_TEXT = "source_text"
    const val TARGET_LANGUAGE = "target_language"
    const val RECIPIENT = "recipient"
    const val LETTER_TYPE = "letter_type"
    const val SUBJECT = "subject"
    const val REPORT_PERIOD = "report_period"
}

/** Quick actions shown on the home screen, in order. */
val QUICK_ACTIONS: List<TaskType> = listOf(
    TaskType.INVOICE,
    TaskType.DAILY_SUMMARY,
    TaskType.CUSTOMER_MESSAGE,
    TaskType.TRANSLATION,
    TaskType.QUOTATION,
    TaskType.MONTHLY_SUMMARY,
    TaskType.PRODUCT_DESCRIPTION,
    TaskType.BUSINESS_LETTER,
    TaskType.REPORT,
)
