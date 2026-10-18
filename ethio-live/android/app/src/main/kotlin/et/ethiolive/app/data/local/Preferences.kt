package et.ethiolive.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import et.ethiolive.core.QualityPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "ethiolive_prefs")

data class LocalPrefs(
    val onboardingDone: Boolean = false,
    val languageChosen: Boolean = false,
    val quality: QualityPreference = QualityPreference.AUTO,
    val giftAnimations: Boolean = true,
    val lastPushToken: String? = null,
)

/** Device-only preferences. Nothing here is security relevant; account settings live on the server. */
class Preferences(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val languageChosen = booleanPreferencesKey("language_chosen")
        val quality = stringPreferencesKey("quality")
        val giftAnimations = booleanPreferencesKey("gift_animations")
        val pushToken = stringPreferencesKey("push_token")
    }

    val prefs: Flow<LocalPrefs> = context.store.data.map { p ->
        LocalPrefs(
            onboardingDone = p[K.onboarding] ?: false,
            languageChosen = p[K.languageChosen] ?: false,
            quality = p[K.quality]?.let { runCatching { QualityPreference.valueOf(it) }.getOrNull() } ?: QualityPreference.AUTO,
            giftAnimations = p[K.giftAnimations] ?: true,
            lastPushToken = p[K.pushToken],
        )
    }

    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }
    suspend fun setLanguageChosen() = context.store.edit { it[K.languageChosen] = true }
    suspend fun setQuality(q: QualityPreference) = context.store.edit { it[K.quality] = q.name }
    suspend fun setGiftAnimations(on: Boolean) = context.store.edit { it[K.giftAnimations] = on }
    suspend fun setPushToken(token: String?) = context.store.edit { if (token == null) it.remove(K.pushToken) else it[K.pushToken] = token }
}
