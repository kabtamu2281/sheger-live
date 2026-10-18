package et.ethiolive.app.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.AppLanguage
import kotlinx.coroutines.launch

/** The language the UI is currently using (per-app locale), defaulting to English. */
fun currentLanguage(): AppLanguage {
    val locales = AppCompatDelegate.getApplicationLocales()
    return AppLanguage.fromTag(if (locales.isEmpty) java.util.Locale.getDefault().language else locales[0]?.language)
}

fun applyLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}

@Composable
fun LanguagePicker(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppLanguage.entries.forEach { lang ->
            Surface(
                onClick = { onSelect(lang) },
                shape = RoundedCornerShape(14.dp),
                color = if (lang == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                        if (!lang.complete) Text(stringResource(R.string.language_partial), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (lang == selected) Icon(Icons.Default.Check, null, tint = Brand.Gold)
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    var language by remember { mutableStateOf(currentLanguage()) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        if (step == 0) {
            Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(32.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Brand.Gold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.choose_language), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                LanguagePicker(language) {
                    language = it
                    applyLanguage(it)
                }
                Spacer(Modifier.height(32.dp))
                PrimaryButton(stringResource(R.string.continue_label), onClick = {
                    scope.launch { container.prefs.setLanguageChosen() }
                    step = 1
                })
            }
        } else {
            val pages = listOf(
                Triple("lion", R.string.onboard_1_title, R.string.onboard_1_body),
                Triple("jebena", R.string.onboard_2_title, R.string.onboard_2_body),
                Triple("crown", R.string.onboard_3_title, R.string.onboard_3_body),
            )
            val pager = rememberPagerState { pages.size }
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { scope.launch { container.prefs.setOnboardingDone() } }) { Text(stringResource(R.string.skip)) }
                }
                HorizontalPager(pager, modifier = Modifier.weight(1f)) { i ->
                    val (art, title, body) = pages[i]
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(200.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            GiftArt(art, 140.dp)
                        }
                        Spacer(Modifier.height(32.dp))
                        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(pages.size) { i ->
                        Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) Brand.Gold else MaterialTheme.colorScheme.outline))
                    }
                }
                PrimaryButton(
                    stringResource(if (pager.currentPage == pages.lastIndex) R.string.get_started else R.string.next),
                    onClick = {
                        scope.launch {
                            if (pager.currentPage == pages.lastIndex) container.prefs.setOnboardingDone()
                            else pager.animateScrollToPage(pager.currentPage + 1)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun LanguageScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    var language by remember { mutableStateOf(currentLanguage()) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.language), nav::back)
        Column(Modifier.padding(16.dp)) {
            LanguagePicker(language) {
                language = it
                applyLanguage(it)
                scope.launch {
                    val p = container.account.value?.profile ?: return@launch
                    runCatching { container.profiles.updateProfile(p.displayName, p.bio, p.avatarUrl, it.tag, p.interests, p.privateAccount) }
                }
            }
        }
    }
}

/** Shown when the build has no Supabase configuration. The app does not pretend to work without a backend. */
@Composable
fun SetupRequiredScreen() {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        GiftArt("lion", 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Brand.Gold)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.setup_required_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.setup_required_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoPill(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Brand.Scrim).padding(horizontal = 10.dp, vertical = 4.dp))
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text)
    }
}
