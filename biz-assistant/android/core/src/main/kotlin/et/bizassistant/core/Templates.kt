package et.bizassistant.core

/**
 * Manual templates used when AI drafting is off, over quota, or unavailable.
 * They only insert what the user typed; nothing is invented.
 */
object OfflineTemplates {

    fun render(
        task: TaskType,
        inputs: Map<String, String>,
        profile: BusinessProfile,
        lang: AppLanguage,
        summary: PeriodSummary? = null,
    ): String = when (task) {
        TaskType.CUSTOMER_MESSAGE -> customerMessage(inputs, profile, lang)
        TaskType.PRODUCT_DESCRIPTION -> productDescription(inputs, lang)
        TaskType.TRANSLATION -> translationFallback(inputs, lang)
        TaskType.BUSINESS_LETTER -> businessLetter(inputs, profile, lang)
        TaskType.REPORT -> report(inputs, profile, lang, summary)
        else -> error("$task is built by DocumentRenderer, not a template")
    }

    private fun v(inputs: Map<String, String>, key: String) = inputs[key]?.trim().orEmpty()

    private fun customerMessage(inputs: Map<String, String>, profile: BusinessProfile, lang: AppLanguage): String {
        val name = v(inputs, Fields.CUSTOMER_NAME)
        val details = v(inputs, Fields.DETAILS)
        val biz = profile.businessName
        val purpose = v(inputs, Fields.PURPOSE)
        return when (lang) {
            AppLanguage.ENGLISH -> {
                val opener = when (purpose) {
                    "payment_reminder" -> "This is a friendly reminder about your payment."
                    "thank_you" -> "Thank you for choosing us."
                    "order_ready" -> "Your order is ready."
                    "new_product" -> "We have something new for you."
                    "apology" -> "We are sorry for the inconvenience."
                    else -> "We would like to share an update with you."
                }
                "Dear $name,\n\n$opener\n$details\n\nKind regards,\n$biz"
            }
            AppLanguage.AMHARIC -> {
                val opener = when (purpose) {
                    "payment_reminder" -> "ስለ ክፍያዎ ይህ ወዳጃዊ ማስታወሻ ነው።"
                    "thank_you" -> "እኛን ስለመረጡ እናመሰግናለን።"
                    "order_ready" -> "ትዕዛዝዎ ዝግጁ ነው።"
                    "new_product" -> "ለእርስዎ አዲስ ነገር አለን።"
                    "apology" -> "ለተፈጠረው ችግር ይቅርታ እንጠይቃለን።"
                    else -> "አንድ መረጃ ልናካፍልዎ እንወዳለን።"
                }
                "ውድ $name፣\n\n$opener\n$details\n\nከአክብሮት ጋር፣\n$biz"
            }
            AppLanguage.OROMO -> {
                val opener = when (purpose) {
                    "payment_reminder" -> "Kun waa'ee kaffaltii keetii yaadachiisa michummaati."
                    "thank_you" -> "Nu filachuu keessaniif galatoomaa."
                    "order_ready" -> "Ajajni keessan qophaa'eera."
                    "new_product" -> "Wanta haaraa isiniif qabna."
                    "apology" -> "Rakkoo uumameef dhiifama gaafanna."
                    else -> "Odeeffannoo tokko isinii qooduu barbaanna."
                }
                "Kabajamoo $name,\n\n$opener\n$details\n\nKabajaan,\n$biz"
            }
        }
    }

    private fun productDescription(inputs: Map<String, String>, lang: AppLanguage): String {
        val name = v(inputs, Fields.PRODUCT_NAME)
        val features = v(inputs, Fields.FEATURES)
            .split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("\n") { "• $it" }
        val price = v(inputs, Fields.PRICE)
        val audience = v(inputs, Fields.AUDIENCE)
        val (priceLabel, forLabel) = when (lang) {
            AppLanguage.ENGLISH -> "Price" to "Ideal for"
            AppLanguage.AMHARIC -> "ዋጋ" to "ተስማሚ ለ"
            AppLanguage.OROMO -> "Gatii" to "Kan mijatu"
        }
        return buildString {
            appendLine(name)
            appendLine()
            appendLine(features)
            if (audience.isNotEmpty()) { appendLine(); appendLine("$forLabel: $audience") }
            if (price.isNotEmpty()) { appendLine(); appendLine("$priceLabel: $price") }
        }.trimEnd()
    }

    private fun translationFallback(inputs: Map<String, String>, lang: AppLanguage): String =
        Str.TRANSLATION_OFFLINE.get(lang) + "\n\n" + v(inputs, Fields.SOURCE_TEXT)

    private fun businessLetter(inputs: Map<String, String>, profile: BusinessProfile, lang: AppLanguage): String {
        val recipient = v(inputs, Fields.RECIPIENT)
        val subject = v(inputs, Fields.SUBJECT)
        val details = v(inputs, Fields.DETAILS)
        val sender = listOf(profile.businessName, profile.address, profile.phone).filter { it.isNotBlank() }.joinToString("\n")
        val signer = profile.ownerName.ifBlank { profile.businessName }
        return when (lang) {
            AppLanguage.ENGLISH -> "$sender\n\nTo: $recipient\nSubject: $subject\n\nDear $recipient,\n\n$details\n\nThank you for your attention.\n\nSincerely,\n$signer"
            AppLanguage.AMHARIC -> "$sender\n\nለ፦ $recipient\nጉዳዩ፦ $subject\n\nለ$recipient፣\n\n$details\n\nስለ ትኩረትዎ እናመሰግናለን።\n\nከሰላምታ ጋር፣\n$signer"
            AppLanguage.OROMO -> "$sender\n\nGara: $recipient\nDhimma: $subject\n\nKabajamoo $recipient,\n\n$details\n\nXiyyeeffannaa keessaniif galatoomaa.\n\nNagaa wajjin,\n$signer"
        }
    }

    private fun report(inputs: Map<String, String>, profile: BusinessProfile, lang: AppLanguage, summary: PeriodSummary?): String {
        val body = summary?.let { DocumentRenderer.renderSummary(it, profile, lang, Str.TASK_REPORT) }
            ?: Str.NO_RECORDS_FOR_PERIOD.get(lang)
        val notes = v(inputs, Fields.DETAILS)
        return if (notes.isEmpty()) body else "$body\n\n${Str.FIELD_REPORT_NOTES.get(lang)}:\n$notes"
    }
}
