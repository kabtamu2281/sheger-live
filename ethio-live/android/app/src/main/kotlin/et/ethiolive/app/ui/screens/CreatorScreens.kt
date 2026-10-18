package et.ethiolive.app.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.CreatorDashboard
import et.ethiolive.app.data.DayValue
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.NotConfiguredNotice
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Money
import et.ethiolive.core.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BarChart(points: List<DayValue>, value: (DayValue) -> Long, modifier: Modifier = Modifier) {
    val max = (points.maxOfOrNull(value) ?: 0L).coerceAtLeast(1L)
    val color = Brand.Gold
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (points.isEmpty()) return@Canvas
        val w = size.width / points.size
        points.forEachIndexed { i, p ->
            val h = size.height * value(p) / max
            drawRoundRect(color, topLeft = Offset(i * w + w * 0.15f, size.height - h), size = Size(w * 0.7f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
        }
    }
}

@Composable
fun CreatorDashboardScreen(container: AppContainer, nav: Nav) {
    val vm = rememberLoad("creator-dashboard") { container.wallet.creatorDashboard() }
    val language = currentLanguage()
    val giftsVm = rememberLoad("gift-catalog") { container.wallet.gifts() }
    val giftsState by giftsVm.state.collectAsState()
    val gifts = (giftsState as? et.ethiolive.app.ui.components.Load.Ready)?.value.orEmpty().associateBy { it.id }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.creator_dashboard), nav::back)
        LoadContent(vm) { d: CreatorDashboard ->
            val est = Money.payoutEstimate(d.balances.beans, d.terms.etbMinorPer100Beans, d.terms.feeBps)
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(stringResource(R.string.beans_available), Money.grouped(d.balances.beans), Modifier.weight(1f),
                            sub = stringResource(R.string.approx_value, Money.format(est.netMinor, "ETB", language)))
                        MetricCard(stringResource(R.string.beans_pending), Money.grouped(d.balances.beansPending), Modifier.weight(1f),
                            sub = stringResource(R.string.hold_days, d.terms.holdDays))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(stringResource(R.string.total_gift_coins), Money.grouped(d.totalGiftCoins), Modifier.weight(1f))
                        MetricCard(stringResource(R.string.followers), Money.grouped(d.followerCount.toLong()), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard(stringResource(R.string.live_time_30d), TimeFormat.duration(d.sessions.totalSeconds), Modifier.weight(1f),
                            sub = stringResource(R.string.sessions_n, d.sessions.count))
                        MetricCard(stringResource(R.string.peak_viewers_label), Money.grouped(d.sessions.peakViewers.toLong()), Modifier.weight(1f),
                            sub = stringResource(R.string.avg_peak, d.sessions.avgPeak))
                    }
                }
                item {
                    if (!d.isAdult) NotConfiguredNotice(R.string.payout_age_notice)
                    else if (d.kycStatus != "verified") NotConfiguredNotice(R.string.kyc_notice)
                }
                item {
                    PrimaryButton(stringResource(R.string.request_payout), onClick = { nav.to(Routes.PAYOUT) },
                        enabled = d.isAdult && d.kycStatus == "verified" && d.openPayout == null && d.balances.beans >= d.terms.minBeans)
                    d.openPayout?.let { Text(stringResource(R.string.payout_open_status, payoutStatusLabel(it.status)), modifier = Modifier.padding(top = 6.dp)) }
                    Text(stringResource(R.string.payout_terms_line, Money.grouped(d.terms.minBeans), d.terms.feeBps / 100.0, d.terms.processingDays),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
                item { SectionHeader(stringResource(R.string.earnings_30d)) }
                item { BarChart(d.earningsByDay, { it.beans ?: 0 }) }
                if (d.giftBreakdown.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.top_gifts)) }
                    items(d.giftBreakdown) { g ->
                        val gift = gifts[g.giftId]
                        ListItem(
                            leadingContent = { GiftArt(gift?.artworkKey ?: g.giftId, 36.dp) },
                            headlineContent = { Text(gift?.name ?: g.giftId) },
                            supportingContent = { Text("x${Money.grouped(g.count)}") },
                            trailingContent = { Text(Money.grouped(g.coins), color = Brand.Gold, fontWeight = FontWeight.Bold) },
                        )
                    }
                }
                if (d.topSupporters.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.top_supporters)) }
                    items(d.topSupporters, key = { it.id }) { s ->
                        UserRow(s.avatarUrl, s.displayName, s.username, onClick = { nav.profile(s.id) }) {
                            Text(Money.grouped(s.coins), color = Brand.Gold)
                        }
                    }
                }
                if (d.recentSessions.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.recent_lives)) }
                    items(d.recentSessions) { s ->
                        ListItem(
                            headlineContent = { Text(s.title) },
                            supportingContent = {
                                Text(listOfNotNull(relativeTime(s.startedAt), s.durationSeconds?.let { TimeFormat.duration(it) },
                                    stringResource(R.string.peak_viewers, s.peakViewers), stringResource(R.string.new_followers_n, s.newFollowers)).joinToString(" · "))
                            },
                            trailingContent = { Text(Money.grouped(s.giftCoins), color = Brand.Gold) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun payoutStatusLabel(status: String): String = stringResource(
    when (status) {
        "requested" -> R.string.payout_requested
        "under_review" -> R.string.payout_under_review
        "approved" -> R.string.payout_approved
        "paid" -> R.string.payout_paid
        "rejected" -> R.string.payout_rejected
        else -> R.string.payout_failed
    },
)

private val PAYOUT_METHODS = listOf("telebirr" to R.string.method_telebirr, "cbe_birr" to R.string.method_cbe_birr,
    "bank_transfer" to R.string.method_bank, "mpesa" to R.string.method_mpesa)

@Composable
fun PayoutScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    val dash = rememberLoad("payout-dashboard") { container.wallet.creatorDashboard() }
    val history = rememberLoad("payout-history") { container.wallet.payouts() }
    val language = currentLanguage()
    var beans by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("telebirr") }
    var account by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var done by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(stringResource(R.string.request_payout), nav::back)
        LoadContent(dash) { d ->
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.beans_available_n, Money.grouped(d.balances.beans)), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(beans, { beans = it.filter(Char::isDigit).take(9) }, label = { Text(stringResource(R.string.beans_to_withdraw)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                val amount = beans.toLongOrNull() ?: 0L
                val est = Money.payoutEstimate(amount, d.terms.etbMinorPer100Beans, d.terms.feeBps)
                Text(stringResource(R.string.payout_estimate, Money.format(est.grossMinor, "ETB", language), Money.format(est.feeMinor, "ETB", language),
                    Money.format(est.netMinor, "ETB", language)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PAYOUT_METHODS.forEach { (key, label) -> Chip(stringResource(label), method == key, { method = key }) }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(account, { account = it.take(40) }, label = { Text(stringResource(if (method == "bank_transfer") R.string.bank_account else R.string.phone_number)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accountName, { accountName = it.take(80) }, label = { Text(stringResource(R.string.account_holder_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.payout_review_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
                if (done) Text(stringResource(R.string.payout_submitted), color = Brand.Emerald)
                PrimaryButton(stringResource(R.string.submit), loading = busy,
                    enabled = !done && amount >= d.terms.minBeans && amount <= d.balances.beans && account.length >= 6 && accountName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                container.wallet.requestPayout(amount, method, account.trim(), accountName.trim())
                                done = true
                                history.reload()
                            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                            busy = false
                        }
                    })
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.payout_history), style = MaterialTheme.typography.titleMedium)
                val hs by history.state.collectAsState()
                (hs as? et.ethiolive.app.ui.components.Load.Ready)?.value?.forEach { p ->
                    ListItem(
                        headlineContent = { Text(Money.format(p.netEtbMinor, "ETB", language)) },
                        supportingContent = { Text(relativeTime(p.createdAt) + " · " + Money.grouped(p.beans) + " · " + (p.rejectionReason ?: "")) },
                        trailingContent = { Text(payoutStatusLabel(p.status)) },
                    )
                }
            }
        }
    }
}
