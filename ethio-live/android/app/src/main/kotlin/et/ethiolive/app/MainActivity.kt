package et.ethiolive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.ui.nav.EthioLiveRoot
import et.ethiolive.app.ui.theme.EthioLiveTheme
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    /** Deep-link target requested by a notification tap ("messages", "notifications") or a payment return. */
    val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        val container = (application as EthioLiveApp).container
        setContent {
            EthioLiveTheme {
                EthioLiveRoot(container = container, activity = this)
            }
        }
        if (BuildConfig.DEBUG && savedInstanceState == null) CrashReport.showPending(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra("open")?.let { pendingOpen.value = it }
        val data = intent.data ?: return
        if (data.scheme == "ethiolive") {
            when (data.host) {
                "auth" -> if (BackendConfig.isConfigured) {
                    runCatching { (application as EthioLiveApp).container.backend.client.handleDeeplinks(intent) }
                }
                "payment" -> pendingOpen.value = "wallet"
            }
        }
    }
}
