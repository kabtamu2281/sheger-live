package et.ethiolive.app

import android.content.Context
import et.ethiolive.app.billing.PlayBilling
import et.ethiolive.app.data.Account
import et.ethiolive.app.data.AuthRepository
import et.ethiolive.app.data.Backend
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.data.CommunityRepository
import et.ethiolive.app.data.DiscoveryRepository
import et.ethiolive.app.data.LiveRepository
import et.ethiolive.app.data.MessageRepository
import et.ethiolive.app.data.PkRepository
import et.ethiolive.app.data.ProfileRepository
import et.ethiolive.app.data.WalletRepository
import et.ethiolive.app.data.local.Cache
import et.ethiolive.app.data.local.CacheDatabase
import et.ethiolive.app.data.local.Preferences
import et.ethiolive.app.stream.AgoraStreamEngine
import et.ethiolive.app.stream.StreamEngine
import et.ethiolive.app.stream.UnconfiguredStreamEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Manual dependency container (one per process). Chosen over Hilt to keep the build small and the wiring explicit;
 * every dependency is created once and shared by the view models.
 */
class AppContainer(private val context: Context) {
    val backend by lazy { Backend() }
    val auth by lazy { AuthRepository(backend) }
    val profiles by lazy { ProfileRepository(backend) }
    val discovery by lazy { DiscoveryRepository(backend) }
    val live by lazy { LiveRepository(backend) }
    val pk by lazy { PkRepository(backend) }
    val wallet by lazy { WalletRepository(backend) }
    val messages by lazy { MessageRepository(backend) }
    val community by lazy { CommunityRepository(backend) }
    val prefs by lazy { Preferences(context) }
    val cache by lazy { Cache(CacheDatabase.create(context).dao()) }
    val billing by lazy { PlayBilling(context) }

    val stream: StreamEngine by lazy {
        if (BackendConfig.streamingConfigured) AgoraStreamEngine(context, BuildConfig.AGORA_APP_ID) else UnconfiguredStreamEngine(context)
    }

    private val _account = MutableStateFlow<Account?>(null)
    /** The signed-in user's account as last returned by the server (null while signed out or before signup completes). */
    val account: StateFlow<Account?> = _account.asStateFlow()

    suspend fun refreshAccount(): Account? = auth.myAccount().also { _account.value = it }

    fun clearAccount() { _account.value = null }

    suspend fun registerPushToken(token: String) {
        prefs.setPushToken(token)
        if (backend.userId != null) runCatching { profiles.registerPushToken(token) }
    }

    suspend fun signOut() {
        prefs.prefs.first().lastPushToken?.let { runCatching { profiles.removePushToken(it) } }
        runCatching { auth.signOut() }
        cache.clear()
        clearAccount()
    }
}
