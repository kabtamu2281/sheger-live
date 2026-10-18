package et.ethiolive.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.push.EthioMessagingService
import kotlinx.coroutines.launch

class EthioLiveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        EthioMessagingService.ensureChannels(this)
        initPush()
    }

    /** Firebase is configured from build properties (no google-services.json in the repo) and only when provided. */
    private fun initPush() {
        if (!BackendConfig.pushConfigured) return
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                        .build(),
                )
            }
        }
    }

    /** Called after sign-in: fetches this device's FCM token and registers it with the backend. */
    fun syncPushToken() {
        if (!BackendConfig.pushConfigured || FirebaseApp.getApps(this).isEmpty()) return
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            fm.isAutoInitEnabled = true
            fm.token.addOnSuccessListener { token -> container.backend.scope.launch { container.registerPushToken(token) } }
        }
    }
}
