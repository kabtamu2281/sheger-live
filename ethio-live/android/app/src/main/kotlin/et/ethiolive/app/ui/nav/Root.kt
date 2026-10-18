package et.ethiolive.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import et.ethiolive.app.AppContainer
import et.ethiolive.app.EthioLiveApp
import et.ethiolive.app.MainActivity
import et.ethiolive.app.R
import et.ethiolive.app.data.AuthState
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.ui.components.ErrorState
import et.ethiolive.app.ui.components.Loading
import et.ethiolive.app.ui.screens.AuthScreen
import et.ethiolive.app.ui.screens.CompleteSignupScreen
import et.ethiolive.app.ui.screens.OnboardingScreen
import et.ethiolive.app.ui.screens.RestrictedAccountScreen
import et.ethiolive.app.ui.screens.SetupRequiredScreen
import et.ethiolive.app.ui.theme.Brand
import kotlinx.coroutines.launch

/** Top-level gate: setup notice → onboarding → sign-in → profile completion → main app. */
@Composable
fun EthioLiveRoot(container: AppContainer, activity: MainActivity) {
    if (!BackendConfig.isConfigured) {
        SetupRequiredScreen()
        return
    }
    val prefs by container.prefs.prefs.collectAsState(initial = null)
    val authState by container.auth.state.collectAsState(initial = AuthState.LOADING)
    val account by container.account.collectAsState()
    var accountLoaded by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        if (authState == AuthState.SIGNED_IN) {
            accountLoaded = false
            accountError = null
            try {
                container.refreshAccount()
                (activity.application as EthioLiveApp).syncPushToken()
            } catch (e: Exception) {
                accountError = e
            }
            accountLoaded = true
        } else if (authState == AuthState.SIGNED_OUT) {
            container.clearAccount()
            accountLoaded = false
        }
    }

    val p = prefs
    when {
        p == null -> Loading()
        !p.onboardingDone -> OnboardingScreen(container)
        authState == AuthState.LOADING -> Loading()
        authState == AuthState.SIGNED_OUT -> AuthScreen(container, activity)
        !accountLoaded -> Loading()
        accountError != null -> ErrorState(accountError, onRetry = {
            scope.launch {
                accountError = null
                accountError = runCatching { container.refreshAccount() }.exceptionOrNull()
            }
        })
        account == null -> CompleteSignupScreen(container)
        account?.profile?.accountStatus in setOf("suspended", "banned") -> RestrictedAccountScreen(container, account!!)
        else -> MainScaffold(container, activity)
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
private fun MainScaffold(container: AppContainer, activity: MainActivity) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = listOf(
        Tab(Routes.HOME, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
        Tab(Routes.EXPLORE, R.string.tab_explore, Icons.Outlined.Explore, Icons.Filled.Explore),
        Tab(Routes.GO_LIVE, R.string.tab_go_live, Icons.Filled.Add, Icons.Filled.Add),
        Tab(Routes.MESSAGES, R.string.tab_messages, Icons.Outlined.ChatBubbleOutline, Icons.Filled.ChatBubble),
        Tab(Routes.ME, R.string.tab_me, Icons.Outlined.Person, Icons.Filled.Person),
    )
    val showBar = route in tabs.map { it.route }
    val pending by activity.pendingOpen.collectAsState()
    LaunchedEffect(pending) {
        when (pending) {
            "messages" -> nav.navigate(Routes.MESSAGES)
            "notifications" -> nav.navigate(Routes.NOTIFICATIONS)
            "wallet" -> nav.navigate(Routes.WALLET)
        }
        activity.pendingOpen.value = null
    }

    // Screens own their insets (top bars, the full-screen live room), so the scaffold adds none.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { tab ->
                    val selected = route == tab.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) nav.navigate(tab.route) {
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (tab.route == Routes.GO_LIVE) {
                                Box(Modifier.size(44.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                    Box(Modifier.fillMaxSize().clip(CircleShape).background(Brand.heroGradient))
                                    Icon(tab.icon, stringResource(tab.label), tint = Color.White)
                                }
                            } else Icon(if (selected) tab.selectedIcon else tab.icon, stringResource(tab.label))
                        },
                        label = if (tab.route == Routes.GO_LIVE) null else ({ Text(stringResource(tab.label)) }),
                    )
                }
            }
        },
    ) { padding ->
        AppNavHost(nav, container, activity, Modifier.padding(padding))
    }
}
