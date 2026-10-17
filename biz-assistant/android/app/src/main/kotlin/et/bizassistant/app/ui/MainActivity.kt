package et.bizassistant.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.core.AppLanguage
import et.bizassistant.core.Str

val LocalLang = staticCompositionLocalOf { AppLanguage.ENGLISH }

/** Current-language text for [s]. */
@Composable
fun t(s: Str): String = s.get(LocalLang.current)

@Composable
fun tf(s: Str, vararg args: Any): String = s.format(LocalLang.current, *args)

private val Colors = lightColorScheme(
    primary = Color(0xFF0F5132),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFE0),
    onPrimaryContainer = Color(0xFF082B1B),
    secondary = Color(0xFF8A6D00),
    secondaryContainer = Color(0xFFFFF1C2),
    tertiary = Color(0xFFA4262C),
    background = Color(0xFFFDFCF7),
    surface = Color(0xFFFDFCF7),
    error = Color(0xFFA4262C),
)

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val lang by vm.language.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalLang provides lang) {
                MaterialTheme(colorScheme = Colors) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot(vm, onExit = { finish() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel, onExit: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler { if (!vm.back()) onExit() }

    val screen = vm.screen
    val showTabs = screen in listOf(Screen.Home, Screen.Records, Screen.Library, Screen.Settings)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    TabItem(vm, Screen.Home, Str.HOME) { Icon(Icons.Default.Home, null) }
                    TabItem(vm, Screen.Records, Str.RECORDS) { Icon(Icons.AutoMirrored.Filled.List, null) }
                    TabItem(vm, Screen.Library, Str.LIBRARY) { Icon(Icons.Default.Search, null) }
                    TabItem(vm, Screen.Settings, Str.SETTINGS) { Icon(Icons.Default.Settings, null) }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (screen) {
                Screen.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                Screen.Welcome -> WelcomeScreen(vm)
                Screen.ProfileSetup -> ProfileScreen(vm, onboarding = vm.stack.size > 1 && vm.stack.first() == Screen.Welcome)
                is Screen.Consent -> ConsentScreen(vm, screen.fromSettings)
                Screen.Home -> HomeScreen(vm)
                Screen.Records -> RecordsScreen(vm)
                is Screen.RecordForm -> RecordFormScreen(vm, screen.sale)
                is Screen.DocBuilder -> DocBuilderScreen(vm, screen.kind)
                is Screen.Task -> TaskScreen(vm, screen.type)
                Screen.Editor -> EditorScreen(vm)
                Screen.Library -> LibraryScreen(vm)
                Screen.History -> HistoryScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.Plans -> PlansScreen(vm)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(vm: AppViewModel, target: Screen, label: Str, icon: @Composable () -> Unit) {
    NavigationBarItem(
        selected = vm.screen == target,
        onClick = { if (vm.screen != target) vm.replaceAll(target) },
        icon = icon,
        label = { Text(t(label)) },
    )
}
