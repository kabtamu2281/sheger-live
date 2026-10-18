package et.ethiolive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.ethiolive.app.R
import et.ethiolive.app.data.Gift
import et.ethiolive.app.data.Participant
import et.ethiolive.app.data.RoomCard
import et.ethiolive.app.data.SeatRequest
import et.ethiolive.app.data.Viewer
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftPanel(
    gifts: List<Gift>,
    coins: Long,
    promoCoins: Long,
    recipients: List<Participant>,
    onSend: (Gift, String, Int) -> Unit,
    onRecharge: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<Gift?>(null) }
    var recipientId by remember(recipients) { mutableStateOf(recipients.firstOrNull { it.role == "host" }?.userId ?: recipients.firstOrNull()?.userId) }
    var quantity by remember { mutableIntStateOf(1) }
    val am = currentLanguage() == et.ethiolive.core.AppLanguage.AMHARIC
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Brand.NightRaised) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            if (recipients.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(recipients, key = { it.userId }) { p ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { recipientId = p.userId }) {
                            Avatar(p.avatarUrl, p.displayName, 40.dp, ring = if (p.userId == recipientId) Brand.Gold else null)
                            Text(p.displayName, fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(56.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                items(gifts, key = { it.id }) { g ->
                    val isSel = selected?.id == g.id
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(12.dp))
                            .border(if (isSel) 2.dp else 0.dp, if (isSel) Brand.Gold else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { selected = g; if (g.priceCoins == 0) quantity = 1 }
                            .padding(6.dp),
                    ) {
                        GiftArt(g.artworkKey, 48.dp)
                        Text(if (am) g.nameAm else g.name, fontSize = 11.sp, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (g.priceCoins == 0) Text(stringResource(R.string.free), fontSize = 11.sp, color = Brand.Emerald)
                            else { CoinIcon(11.dp); Spacer(Modifier.width(2.dp)); Text("${g.priceCoins}", fontSize = 11.sp, color = Brand.Gold) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                CoinIcon(16.dp)
                Spacer(Modifier.width(4.dp))
                Text(Money.grouped(coins), fontWeight = FontWeight.Bold)
                if (promoCoins > 0) Text(" + " + stringResource(R.string.promo_coins_short, Money.grouped(promoCoins)), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                TextButton(onClick = onRecharge) { Text(stringResource(R.string.recharge)) }
                Spacer(Modifier.weight(1f))
                val g = selected
                if (g != null && g.priceCoins > 0) {
                    listOf(1, 10, 99).forEach { q -> Chip("x$q", quantity == q, { quantity = q }, Modifier.padding(end = 4.dp)) }
                }
            }
            val g = selected
            val target = recipientId
            PrimaryButton(
                text = if (g == null) stringResource(R.string.choose_gift)
                else if (g.priceCoins == 0) stringResource(R.string.send)
                else stringResource(R.string.send_for_coins, Money.grouped(g.priceCoins.toLong() * quantity)),
                enabled = g != null && target != null,
                onClick = { if (g != null && target != null) onSend(g, target, quantity) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (recipients.isEmpty()) Text(stringResource(R.string.no_gift_recipient), color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewersSheet(vm: LiveRoomViewModel, state: LiveRoomState, onProfile: (String) -> Unit, onDismiss: () -> Unit) {
    var viewers by remember { mutableStateOf<List<Viewer>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(Unit) { runCatching { vm.viewers() }.onSuccess { viewers = it }.onFailure { error = it } }
    val manage = state.isHost || state.isModerator
    val seatsAvailable = (state.snapshot?.room?.maxSeats ?: 1) > 1
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.viewers_count, state.viewerCount), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        error?.let { Text(errorMessage(it), modifier = Modifier.padding(16.dp)) }
        LazyColumn(Modifier.navigationBarsPadding().heightIn(max = 480.dp)) {
            val list = viewers.orEmpty()
            if (viewers != null && list.isEmpty()) item { EmptyState(R.string.no_viewers) }
            items(list, key = { it.userId }) { v ->
                var menu by remember { mutableStateOf(false) }
                UserRow(v.avatarUrl, v.displayName, v.username, v.level, onClick = { if (manage && v.userId != state.myUserId) menu = !menu else onProfile(v.userId) })
                if (menu) Row(Modifier.padding(start = 72.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(stringResource(R.string.view_profile), false, { onProfile(v.userId) })
                    if (state.isHost && seatsAvailable && v.role == "audience") Chip(stringResource(R.string.invite_to_seat), false, { vm.inviteToSeat(v.userId); menu = false })
                    Chip(stringResource(R.string.mute_chat), false, { vm.muteInChat(v.userId); menu = false })
                    if (state.isHost) Chip(stringResource(if (v.role == "moderator") R.string.remove_moderator else R.string.make_moderator), false, {
                        vm.setModerator(v.userId, v.role != "moderator"); menu = false
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatRequestsSheet(requests: List<SeatRequest>, onRespond: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.seat_requests), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.navigationBarsPadding().heightIn(max = 480.dp)) {
            val incoming = requests.filter { it.kind == "request" }
            if (incoming.isEmpty()) item { EmptyState(R.string.no_seat_requests) }
            items(incoming, key = { it.id }) { r ->
                UserRow(r.avatarUrl, r.displayName ?: "", r.username ?: "") {
                    TextButton(onClick = { onRespond(r.id, false) }) { Text(stringResource(R.string.decline)) }
                    TextButton(onClick = { onRespond(r.id, true) }) { Text(stringResource(R.string.accept)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PkSheet(vm: LiveRoomViewModel, state: LiveRoomState, onDismiss: () -> Unit) {
    var candidates by remember { mutableStateOf<List<RoomCard>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var minutes by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) { runCatching { vm.pkCandidates() }.onSuccess { candidates = it }.onFailure { error = it } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text(stringResource(R.string.pk_battle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            Text(stringResource(R.string.pk_explainer), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            val pk = state.pk
            if (pk != null && pk.status == "invited" && pk.hostA == state.myUserId) {
                Text(stringResource(R.string.pk_waiting_for_opponent), modifier = Modifier.padding(16.dp))
                TextButton(onClick = { vm.pkCancel(); onDismiss() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.cancel_invite)) }
                return@Column
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 5, 10).forEach { m -> Chip(stringResource(R.string.minutes_short, m), minutes == m, { minutes = m }) }
            }
            error?.let { Text(errorMessage(it), modifier = Modifier.padding(16.dp)) }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                val list = candidates.orEmpty()
                if (candidates != null && list.isEmpty()) item { EmptyState(R.string.pk_no_candidates) }
                items(list, key = { it.id }) { r ->
                    UserRow(r.host.avatarUrl, r.host.displayName, r.host.username, r.host.level, r.host.isVerified) {
                        TextButton(onClick = { vm.pkInvite(r.id, minutes * 60); onDismiss() }) { Text(stringResource(R.string.invite)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSheet(isHost: Boolean, onEnd: () -> Unit, onReport: () -> Unit, onBlock: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            if (isHost) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.end_live), color = Brand.Coral) },
                    leadingContent = { Icon(Icons.Default.CallEnd, null, tint = Brand.Coral) },
                    modifier = Modifier.clickable(onClick = onEnd),
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.report_live)) },
                    leadingContent = { Icon(Icons.Default.Flag, null) },
                    modifier = Modifier.clickable(onClick = onReport),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.block_host)) },
                    leadingContent = { Icon(Icons.Default.Block, null) },
                    modifier = Modifier.clickable(onClick = onBlock),
                )
            }
        }
    }
}

val REPORT_REASONS = listOf(
    "spam" to R.string.reason_spam,
    "harassment" to R.string.reason_harassment,
    "hate" to R.string.reason_hate,
    "nudity" to R.string.reason_nudity,
    "violence" to R.string.reason_violence,
    "minor_safety" to R.string.reason_minor_safety,
    "scam" to R.string.reason_scam,
    "impersonation" to R.string.reason_impersonation,
    "self_harm" to R.string.reason_self_harm,
    "illegal" to R.string.reason_illegal,
    "other" to R.string.reason_other,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheet(onSubmit: (String, String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(REPORT_REASONS) { (key, label) ->
                    Row(Modifier.fillMaxWidth().clickable { reason = key }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == key, onClick = { reason = key })
                        Text(stringResource(label))
                    }
                }
            }
            OutlinedTextField(details, { details = it.take(500) }, label = { Text(stringResource(R.string.report_details)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(R.string.submit_report), enabled = reason != null, onClick = { onSubmit(reason!!, details.trim()) })
            Spacer(Modifier.height(16.dp))
        }
    }
}
