package et.ethiolive.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import et.ethiolive.app.AppContainer
import et.ethiolive.app.BuildConfig
import et.ethiolive.app.R
import et.ethiolive.app.data.UserSettings
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.QualityPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SettingRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    ListItem(
        headlineContent = { Text(label, color = if (danger) Brand.Coral else MaterialTheme.colorScheme.onSurface) },
        leadingContent = { Icon(icon, null, tint = if (danger) Brand.Coral else MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
fun SettingsScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.settings), nav::back)
        LazyColumn {
            item { SectionHeader(stringResource(R.string.account)) }
            item { SettingRow(Icons.Default.Person, stringResource(R.string.edit_profile), { nav.to(Routes.EDIT_PROFILE) }) }
            item { SettingRow(Icons.Default.Language, stringResource(R.string.language), { nav.to(Routes.LANGUAGE) }) }
            item { SettingRow(Icons.Default.Lock, stringResource(R.string.privacy), { nav.to(Routes.PRIVACY) }) }
            item { SettingRow(Icons.Default.Notifications, stringResource(R.string.notification_settings), { nav.to(Routes.NOTIFICATION_SETTINGS) }) }
            item { SettingRow(Icons.Default.Videocam, stringResource(R.string.stream_settings), { nav.to(Routes.STREAM_SETTINGS) }) }
            item { SettingRow(Icons.Default.Block, stringResource(R.string.blocked_users), { nav.to(Routes.BLOCKED) }) }
            item { SectionHeader(stringResource(R.string.support_and_safety)) }
            item { SettingRow(Icons.Default.Shield, stringResource(R.string.safety_center), { nav.to(Routes.SAFETY) }) }
            item { SettingRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.help), { nav.to(Routes.HELP) }) }
            item { SectionHeader(stringResource(R.string.legal)) }
            item { SettingRow(Icons.Default.Gavel, stringResource(R.string.terms), { nav.to(Routes.legal("TERMS")) }) }
            item { SettingRow(Icons.Default.Policy, stringResource(R.string.privacy_policy), { nav.to(Routes.legal("PRIVACY_POLICY")) }) }
            item { SettingRow(Icons.Default.Shield, stringResource(R.string.community_guidelines), { nav.to(Routes.legal("COMMUNITY_GUIDELINES")) }) }
            item { SectionHeader(stringResource(R.string.your_data)) }
            item {
                SettingRow(Icons.Default.Download, stringResource(R.string.download_my_data), {
                    if (!exporting) scope.launch {
                        exporting = true
                        runCatching { container.profiles.exportMyData() }.onSuccess { data ->
                            val send = Intent(Intent.ACTION_SEND).setType("application/json")
                                .putExtra(Intent.EXTRA_SUBJECT, "Ethio Live data export")
                                .putExtra(Intent.EXTRA_TEXT, et.ethiolive.app.data.AppJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), data))
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.download_my_data)))
                        }
                        exporting = false
                    }
                })
            }
            item { SettingRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.sign_out), { confirmSignOut = true }) }
            item { SettingRow(Icons.Default.DeleteForever, stringResource(R.string.delete_account), { nav.to(Routes.DELETE_ACCOUNT) }, danger = true) }
            item {
                Text(stringResource(R.string.version_line, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text(stringResource(R.string.sign_out)) },
        text = { Text(stringResource(R.string.sign_out_confirm)) },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; scope.launch { container.signOut() } }) { Text(stringResource(R.string.sign_out)) } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Settings are saved on the server one change at a time; the UI shows the server's echo. */
@Composable
private fun rememberSettingsEditor(container: AppContainer): Pair<UserSettings?, (Map<String, Any?>) -> Unit> {
    val scope = rememberCoroutineScope()
    val account by container.account.collectAsState()
    return account?.settings to { changes ->
        scope.launch {
            runCatching { container.profiles.updateSettings(changes) }
            runCatching { container.refreshAccount() }
        }
    }
}

val REGIONS = listOf("addis_ababa", "dire_dawa", "bahir_dar", "hawassa", "mekelle", "adama", "gondar", "jimma", "harar", "dessie", "diaspora")

@Composable
fun regionLabel(key: String): String = stringResource(
    when (key) {
        "addis_ababa" -> R.string.region_addis
        "dire_dawa" -> R.string.region_dire_dawa
        "bahir_dar" -> R.string.region_bahir_dar
        "hawassa" -> R.string.region_hawassa
        "mekelle" -> R.string.region_mekelle
        "adama" -> R.string.region_adama
        "gondar" -> R.string.region_gondar
        "jimma" -> R.string.region_jimma
        "harar" -> R.string.region_harar
        "dessie" -> R.string.region_dessie
        else -> R.string.region_diaspora
    },
)

@Composable
fun PrivacyScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val (settings, update) = rememberSettingsEditor(container)
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.privacy), nav::back)
        val s = settings ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.who_can_message))
            listOf("everyone" to R.string.dm_everyone, "followers" to R.string.dm_followers, "nobody" to R.string.dm_nobody).forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { update(mapOf("dm_policy" to key)) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(s.dmPolicy == key, { update(mapOf("dm_policy" to key)) })
                    Text(stringResource(label))
                }
            }
            Text(stringResource(R.string.minor_dm_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ToggleRow(stringResource(R.string.show_in_nearby), stringResource(R.string.show_in_nearby_hint), s.showInNearby) { update(mapOf("show_in_nearby" to it)) }
            if (s.showInNearby) {
                SectionHeader(stringResource(R.string.nearby_region))
                androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(REGIONS) { r ->
                        Chip(regionLabel(r), s.nearbyRegion == r, {
                            scope.launch { runCatching { container.profiles.setNearbyRegion(r) }; runCatching { container.refreshAccount() } }
                        })
                    }
                }
            }
            ToggleRow(stringResource(R.string.show_gift_highlights), null, s.showGiftHighlights) { update(mapOf("show_gift_highlights" to it)) }
            ToggleRow(stringResource(R.string.personalized_recommendations), stringResource(R.string.personalized_recommendations_hint), s.recommendationsPersonalized) {
                update(mapOf("recommendations_personalized" to it))
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(container: AppContainer, nav: Nav) {
    val (settings, update) = rememberSettingsEditor(container)
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.notification_settings), nav::back)
        val s = settings ?: return@Column
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ToggleRow(stringResource(R.string.notify_live_start), null, s.notifyLiveStart) { update(mapOf("notify_live_start" to it)) }
            ToggleRow(stringResource(R.string.notify_follows), null, s.notifyFollows) { update(mapOf("notify_follows" to it)) }
            ToggleRow(stringResource(R.string.notify_gifts), null, s.notifyGifts) { update(mapOf("notify_gifts" to it)) }
            ToggleRow(stringResource(R.string.notify_messages), null, s.notifyMessages) { update(mapOf("notify_messages" to it)) }
            ToggleRow(stringResource(R.string.notify_pk), null, s.notifyPk) { update(mapOf("notify_pk" to it)) }
            ToggleRow(stringResource(R.string.notify_guest_invites), null, s.notifyGuestInvites) { update(mapOf("notify_guest_invites" to it)) }
            if (!et.ethiolive.app.data.BackendConfig.pushConfigured) {
                Text(stringResource(R.string.push_not_configured), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
fun StreamSettingsScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val prefs by container.prefs.prefs.collectAsState(initial = null)
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.stream_settings), nav::back)
        val p = prefs ?: return@Column
        SectionHeader(stringResource(R.string.video_quality))
        listOf(
            QualityPreference.AUTO to R.string.quality_auto,
            QualityPreference.DATA_SAVER to R.string.quality_data_saver,
            QualityPreference.STANDARD to R.string.quality_standard,
            QualityPreference.HIGH to R.string.quality_high,
        ).forEach { (q, label) ->
            Row(Modifier.fillMaxWidth().clickable { scope.launch { container.prefs.setQuality(q) } }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(p.quality == q, { scope.launch { container.prefs.setQuality(q) } })
                Text(stringResource(label))
            }
        }
        Text(stringResource(R.string.quality_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
        ToggleRow(stringResource(R.string.gift_animations), null, p.giftAnimations) { on -> scope.launch { container.prefs.setGiftAnimations(on) } }
    }
}

@Composable
fun BlockedUsersScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val vm = rememberLoad("blocked") { container.profiles.blocked() }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.blocked_users), nav::back)
        LoadContent(vm) { list ->
            if (list.isEmpty()) EmptyState(R.string.no_blocked)
            else LazyColumn {
                items(list, key = { it.id }) { b ->
                    UserRow(b.avatarUrl, b.displayName, b.username) {
                        TextButton(onClick = { scope.launch { runCatching { container.profiles.unblock(b.id) }; vm.reload() } }) { Text(stringResource(R.string.unblock)) }
                    }
                }
            }
        }
    }
}

/** Renders the bundled Markdown legal documents (headings, paragraphs and bullet lists). */
@Composable
fun LegalScreen(nav: Nav, doc: String) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc) {
        val file = doc.filter { it.isLetter() || it == '_' } + ".md"
        text = withContext(Dispatchers.IO) { runCatching { context.assets.open(file).bufferedReader().use { it.readText() } }.getOrNull() }
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(when (doc) {
            "TERMS" -> R.string.terms
            "PRIVACY_POLICY" -> R.string.privacy_policy
            else -> R.string.community_guidelines
        }), nav::back)
        val t = text
        if (t == null) EmptyState(R.string.document_unavailable)
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(t.lines()) { line ->
                when {
                    line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineMedium)
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleMedium)
                    line.startsWith("- ") || line.startsWith("* ") -> Text("•  " + line.drop(2).replace("**", ""))
                    line.isBlank() -> Spacer(Modifier.height(2.dp))
                    else -> Text(line.replace("**", ""))
                }
            }
        }
    }
}

@Composable
fun HelpScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    var topic by remember { mutableStateOf("account") }
    var message by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val faqs = listOf(
        R.string.faq_coins_q to R.string.faq_coins_a,
        R.string.faq_beans_q to R.string.faq_beans_a,
        R.string.faq_payout_q to R.string.faq_payout_a,
        R.string.faq_pk_q to R.string.faq_pk_a,
        R.string.faq_safety_q to R.string.faq_safety_a,
    )
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(stringResource(R.string.help), nav::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            faqs.forEach { (q, a) ->
                var open by remember { mutableStateOf(false) }
                Text(stringResource(q), fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp))
                if (open) Text(stringResource(a), color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.contact_support), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("account" to R.string.topic_account, "payments" to R.string.topic_payments, "payouts" to R.string.topic_payouts,
                    "safety" to R.string.topic_safety).forEach { (k, l) -> Chip(stringResource(l), topic == k, { topic = k }) }
            }
            OutlinedTextField(message, { message = it.take(2000) }, label = { Text(stringResource(R.string.support_message)) }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
            if (sent) Text(stringResource(R.string.support_sent), color = Brand.Emerald)
            PrimaryButton(stringResource(R.string.submit), enabled = !sent && message.trim().length >= 10, onClick = {
                scope.launch {
                    error = null
                    try { container.profiles.support(topic, message.trim()); sent = true } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                }
            })
        }
    }
}

@Composable
fun SafetyCenterScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val reports = rememberLoad("my-reports") { container.profiles.myReports() }
    val actions = rememberLoad("my-actions") { container.profiles.myModerationActions() }
    var appealFor by remember { mutableStateOf<String?>(null) }
    var appealText by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<String?>(null) }
    val sentText = stringResource(R.string.appeal_sent)
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.safety_center), nav::back)
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text(stringResource(R.string.safety_intro), modifier = Modifier.padding(16.dp)) }
            item { SettingRow(Icons.Default.Shield, stringResource(R.string.community_guidelines), { nav.to(Routes.legal("COMMUNITY_GUIDELINES")) }) }
            item { SettingRow(Icons.Default.Block, stringResource(R.string.blocked_users), { nav.to(Routes.BLOCKED) }) }
            item { SectionHeader(stringResource(R.string.actions_on_account)) }
            item {
                LoadContent(actions, Modifier.height(120.dp)) { list ->
                    Column {
                        if (list.isEmpty()) Text(stringResource(R.string.no_actions), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.forEach { a ->
                            ListItem(
                                headlineContent = { Text(a.kind) },
                                supportingContent = { Text(a.reason + " · " + relativeTime(a.createdAt)) },
                                trailingContent = { TextButton(onClick = { appealFor = a.id }) { Text(stringResource(R.string.appeal)) } },
                            )
                        }
                    }
                }
            }
            info?.let { item { Text(it, color = Brand.Emerald, modifier = Modifier.padding(16.dp)) } }
            item { SectionHeader(stringResource(R.string.my_reports)) }
            item {
                LoadContent(reports, Modifier.height(120.dp)) { list ->
                    Column {
                        if (list.isEmpty()) Text(stringResource(R.string.no_reports), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        list.forEach { r ->
                            ListItem(headlineContent = { Text(r.reason) }, supportingContent = { Text(r.targetType + " · " + relativeTime(r.createdAt)) },
                                trailingContent = { Text(r.status) })
                        }
                    }
                }
            }
        }
    }
    appealFor?.let { id ->
        AlertDialog(
            onDismissRequest = { appealFor = null },
            title = { Text(stringResource(R.string.appeal)) },
            text = { OutlinedTextField(appealText, { appealText = it.take(1000) }, label = { Text(stringResource(R.string.appeal_message)) }, minLines = 3) },
            confirmButton = {
                TextButton(enabled = appealText.trim().length >= 10, onClick = {
                    scope.launch {
                        runCatching { container.profiles.appeal(id, appealText.trim()) }.onSuccess { info = sentText }
                        appealFor = null; appealText = ""
                    }
                }) { Text(stringResource(R.string.submit)) }
            },
            dismissButton = { TextButton(onClick = { appealFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun DeleteAccountScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(stringResource(R.string.delete_account), nav::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.delete_account_body))
            Text(stringResource(R.string.delete_account_balance_warning), color = Brand.Coral)
            OutlinedTextField(confirm, { confirm = it }, label = { Text(stringResource(R.string.type_delete)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(stringResource(R.string.delete_account), loading = busy, enabled = confirm == "DELETE", onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        container.profiles.deleteAccount()
                        container.signOut()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                    busy = false
                }
            })
        }
    }
}
