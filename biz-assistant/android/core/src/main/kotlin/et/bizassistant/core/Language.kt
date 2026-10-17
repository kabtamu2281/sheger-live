package et.bizassistant.core

/** Languages the app supports. [tag] is the BCP-47 language tag used for locale, speech and TTS. */
enum class AppLanguage(val tag: String, val speechTag: String, val nativeName: String) {
    ENGLISH("en", "en-US", "English"),
    AMHARIC("am", "am-ET", "አማርኛ"),
    OROMO("om", "om-ET", "Afaan Oromoo");

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag?.substringBefore('-'), ignoreCase = true) } ?: ENGLISH
    }
}
