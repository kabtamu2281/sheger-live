package et.bizassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.core.AppLanguage
import et.bizassistant.core.BusinessProfile
import et.bizassistant.core.Str

@Composable
fun LanguagePicker(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.entries.forEach { lang ->
            Card(Modifier.fillMaxWidth().selectable(selected = lang == selected, onClick = { onSelect(lang) })) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = lang == selected, onClick = { onSelect(lang) })
                    Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(vm: AppViewModel) {
    val lang by vm.language.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(t(Str.APP_NAME), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(t(Str.CHOOSE_LANGUAGE), style = MaterialTheme.typography.titleLarge)
        LanguagePicker(lang) { vm.chooseLanguage(it) }
        Button(onClick = { vm.navigate(Screen.ProfileSetup) }, modifier = Modifier.fillMaxWidth()) { Text(t(Str.CONTINUE)) }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, onboarding: Boolean) {
    val existing by vm.profile.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf(existing?.businessName.orEmpty()) }
    var owner by rememberSaveable { mutableStateOf(existing?.ownerName.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(existing?.address.orEmpty()) }
    var tin by rememberSaveable { mutableStateOf(existing?.tin.orEmpty()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.BUSINESS_PROFILE), onBack = { vm.back() })
        ScrollColumn {
            Field(t(Str.BUSINESS_NAME), name, { name = it }, error = if (showErrors && name.isBlank()) t(Str.REQUIRED_FIELD) else null)
            Field(t(Str.OWNER_NAME), owner, { owner = it })
            Field(t(Str.PHONE), phone, { phone = it })
            Field(t(Str.ADDRESS), address, { address = it })
            Field(t(Str.TIN_OPTIONAL), tin, { tin = it })
            Button(
                onClick = {
                    if (name.isBlank()) showErrors = true
                    else vm.saveProfile(BusinessProfile(name.trim(), owner.trim(), phone.trim(), address.trim(), tin.trim()), thenOnboarding = onboarding)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t(if (onboarding) Str.CONTINUE else Str.SAVE)) }
        }
    }
}

@Composable
fun ConsentScreen(vm: AppViewModel, fromSettings: Boolean) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.AI_CONSENT_TITLE), onBack = { vm.back() })
        ScrollColumn {
            Text(t(Str.WHAT_IS_SENT), style = MaterialTheme.typography.titleMedium)
            Text(t(Str.AI_CONSENT_BODY), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { vm.decideConsent(true, fromSettings) }, modifier = Modifier.fillMaxWidth()) { Text(t(Str.AI_ALLOW)) }
            OutlinedButton(onClick = { vm.decideConsent(false, fromSettings) }, modifier = Modifier.fillMaxWidth()) { Text(t(Str.AI_NOT_NOW)) }
        }
    }
}
