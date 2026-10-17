package et.bizassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import et.bizassistant.core.PlanCatalog
import et.bizassistant.core.Str

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val lang by vm.language.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<Str, () -> Unit>?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.SETTINGS))
        ScrollColumn {
            SectionLabel(t(Str.LANGUAGE))
            LanguagePicker(lang) { vm.chooseLanguage(it) }

            SectionLabel(t(Str.BUSINESS_PROFILE))
            OutlinedButton(onClick = { vm.navigate(Screen.ProfileSetup) }, Modifier.fillMaxWidth()) { Text(t(Str.EDIT)) }

            SectionLabel(t(Str.AI_CONSENT_TITLE))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t(if (user.aiConsentGranted) Str.AI_ENABLED else Str.AI_DISABLED), Modifier.weight(1f))
                Switch(checked = user.aiConsentGranted, onCheckedChange = { on ->
                    // Turning AI on always goes through the explanation screen; turning it off is immediate.
                    if (on) vm.navigate(Screen.Consent(fromSettings = true)) else vm.setAiConsent(false)
                })
            }
            TextButton(onClick = { vm.navigate(Screen.Consent(fromSettings = true)) }) { Text(t(Str.WHAT_IS_SENT)) }

            SectionLabel(t(Str.PLANS))
            OutlinedButton(onClick = { vm.navigate(Screen.Plans) }, Modifier.fillMaxWidth()) { Text(t(Str.USAGE_TITLE)) }

            SectionLabel(t(Str.PRIVACY))
            Text(t(Str.KEEP_RECORDS_FOR))
            ChoiceRow(
                listOf(3, 6, 12, 24, 0).map { m -> m.toString() to if (m == 0) t(Str.FOREVER) else tf(Str.MONTHS, m) },
                user.recordRetentionMonths.toString(),
            ) { vm.setRetention(it.toInt()) }
            OutlinedButton(onClick = { confirm = Str.DELETE_CONVERSATIONS to { vm.deleteAllConversations(); Unit } }, Modifier.fillMaxWidth()) { Text(t(Str.DELETE_CONVERSATIONS)) }
            OutlinedButton(onClick = { confirm = Str.DELETE_DOCUMENTS to { vm.deleteAllDocuments(); Unit } }, Modifier.fillMaxWidth()) { Text(t(Str.DELETE_DOCUMENTS)) }
            OutlinedButton(onClick = { confirm = Str.DELETE_RECORDS to { vm.deleteAllRecords(); Unit } }, Modifier.fillMaxWidth()) { Text(t(Str.DELETE_RECORDS)) }
            Button(onClick = { confirm = Str.DELETE_EVERYTHING to { vm.deleteEverything(); Unit } }, Modifier.fillMaxWidth()) { Text(t(Str.DELETE_EVERYTHING)) }
        }
    }
    confirm?.let { (label, action) ->
        ConfirmDialog(t(Str.DELETE_CONFIRM), t(label), t(Str.DELETE), action, { confirm = null }, destructive = true)
    }
}

@Composable
fun PlansScreen(vm: AppViewModel) {
    val quota by vm.quota.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshUsage() }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle(t(Str.PLANS), onBack = { vm.back() })
        ScrollColumn {
            val q = quota
            if (q == null) NoticeCard(t(Str.USAGE_UNKNOWN), warning = true)
            else NoticeCard(tf(Str.USAGE_LINE, q.used, q.limit) + "\n" + tf(Str.USAGE_RESETS, q.resetsOn.toString()))

            PlanCatalog.plans.forEach { plan ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tf(Str.AI_DRAFTS_PER_MONTH, plan.monthlyAiDrafts))
                        Text(tf(Str.PER_MONTH, plan.monthlyPrice.format()))
                        if (q?.plan == plan.id) Text(t(Str.CURRENT_PLAN), color = MaterialTheme.colorScheme.primary)
                        else if (plan.monthlyPrice.minor > 0) Button(onClick = { vm.upgrade(plan.id.name.lowercase()) }) { Text(t(Str.UPGRADE)) }
                    }
                }
            }
            PlanCatalog.bundles.forEach { b ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(b.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(tf(Str.BUNDLE_EXTRA, b.extraAiDrafts))
                        Text(b.price.format())
                        OutlinedButton(onClick = { vm.upgrade(b.id) }) { Text(t(Str.UPGRADE)) }
                    }
                }
            }
        }
    }
}
