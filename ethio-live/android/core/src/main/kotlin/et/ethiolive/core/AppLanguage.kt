package et.ethiolive.core

/**
 * Languages the app can be shown in. English and Amharic are complete; Afaan Oromo ships with the most
 * important strings and falls back to English for the rest (Android resource fallback), so new languages
 * only need a values-xx/strings.xml file and an entry here.
 */
enum class AppLanguage(val tag: String, val nativeName: String, val complete: Boolean) {
    ENGLISH("en", "English", true),
    AMHARIC("am", "አማርኛ", true),
    OROMO("om", "Afaan Oromoo", false);

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH
    }
}
