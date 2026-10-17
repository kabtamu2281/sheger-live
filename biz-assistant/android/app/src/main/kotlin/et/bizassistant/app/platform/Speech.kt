package et.bizassistant.app.platform

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import et.bizassistant.core.AppLanguage
import java.util.Locale

/** Optional read-aloud. Availability depends on the voices installed on the phone. */
class ReadAloud(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
    }

    /** Returns false when no voice is available for [lang]; the UI then explains this to the user. */
    fun speak(text: String, lang: AppLanguage): Boolean {
        if (!ready) return false
        val result = tts.setLanguage(Locale.forLanguageTag(lang.speechTag))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "biz-assistant")
        return true
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}

/** Optional voice input through the phone's speech recogniser. */
object VoiceInput {
    fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun intent(lang: AppLanguage): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.speechTag)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun result(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
}
