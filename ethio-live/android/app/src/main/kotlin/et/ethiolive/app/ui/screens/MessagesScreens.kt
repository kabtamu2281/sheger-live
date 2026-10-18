package et.ethiolive.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.AppNotification
import et.ethiolive.app.data.Conversation
import et.ethiolive.app.data.DirectMessage
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.TimeFormat
import et.ethiolive.core.Validation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

fun isoToMs(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

@Composable
fun relativeTime(iso: String?): String {
    val ms = isoToMs(iso) ?: return ""
    return TimeFormat.relative(ms, System.currentTimeMillis()) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
}

@Composable
fun MessagesScreen(container: AppContainer, nav: Nav) {
    var tab by remember { mutableIntStateOf(0) }
    val chats = rememberLoad("conversations") { container.messages.conversations(false) }
    val requests = rememberLoad("conversation-requests") { container.messages.conversations(true) }
    LaunchedEffect(Unit) { chats.reload(); requests.reload() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(stringResource(R.string.tab_messages), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.chats)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.message_requests)) })
        }
        LoadContent(if (tab == 0) chats else requests) { list ->
            if (list.isEmpty()) EmptyState(if (tab == 0) R.string.no_chats else R.string.no_requests, if (tab == 0) R.string.no_chats_body else null)
            else LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { c -> ConversationRow(c) { nav.conversation(c.id) } }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    et.ethiolive.app.ui.components.UserRow(c.otherAvatarUrl, c.otherDisplayName, c.otherUsername, onClick = onClick) {
        Column(horizontalAlignment = Alignment.End) {
            Text(relativeTime(c.lastMessageAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.unreadCount > 0) Badge(containerColor = Brand.Coral) { Text("${c.unreadCount}") }
        }
    }
    c.lastMessagePreview?.let {
        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp))
    }
}

data class ChatState(
    val conversation: Conversation? = null,
    val messages: List<DirectMessage> = emptyList(),
    val sending: Set<String> = emptySet(),
    val failed: Map<String, String> = emptyMap(), // clientId -> body
    val error: Throwable? = null,
    val loading: Boolean = true,
)

class ConversationViewModel(private val c: AppContainer, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()
    val myId = c.backend.userId.orEmpty()

    init {
        viewModelScope.launch {
            try {
                val convo = (c.messages.conversations(false) + c.messages.conversations(true)).firstOrNull { it.id == id }
                val msgs = c.messages.messages(id)
                _state.value = _state.value.copy(conversation = convo, messages = msgs, loading = false)
                markRead()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e, loading = false)
            }
        }
        viewModelScope.launch {
            runCatching {
                c.messages.incoming(id).collect { m ->
                    _state.value = _state.value.copy(messages = (_state.value.messages + m).distinctBy { it.id }.sortedBy { it.id })
                    if (m.senderId != myId) markRead()
                }
            }
        }
    }

    private fun markRead() {
        val last = _state.value.messages.lastOrNull()?.id ?: return
        viewModelScope.launch { runCatching { c.messages.markRead(id, last) } }
    }

    fun send(body: String, clientId: String = UUID.randomUUID().toString()) {
        val text = body.trim().take(Validation.DM_MAX)
        if (text.isEmpty()) return
        _state.value = _state.value.copy(sending = _state.value.sending + clientId, failed = _state.value.failed - clientId)
        viewModelScope.launch {
            try {
                val m = c.messages.send(id, text, clientId)
                _state.value = _state.value.copy(
                    messages = (_state.value.messages + m).distinctBy { it.id }.sortedBy { it.id },
                    sending = _state.value.sending - clientId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = _state.value.sending - clientId, failed = _state.value.failed + (clientId to text), error = e)
            }
        }
    }

    fun respond(accept: Boolean) = viewModelScope.launch {
        runCatching { c.messages.respondRequest(id, accept) }.onSuccess {
            _state.value = _state.value.copy(conversation = _state.value.conversation?.copy(status = if (accept) "active" else "declined", isRequestToMe = false))
        }.onFailure { _state.value = _state.value.copy(error = it) }
    }

    fun block() = viewModelScope.launch {
        _state.value.conversation?.let { runCatching { c.profiles.block(it.otherId) } }
    }

    fun report(messageId: Long) = viewModelScope.launch {
        runCatching { c.profiles.report("dm_message", messageId.toString(), "harassment", "") }
    }
}

@Composable
fun ConversationScreen(container: AppContainer, nav: Nav, id: String) {
    val vm: ConversationViewModel = viewModel(key = "dm-$id") { ConversationViewModel(container, id) }
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) { if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex) }
    val convo = state.conversation
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(convo?.otherDisplayName ?: "", nav::back) {
            if (convo != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.view_profile)) }, onClick = { menu = false; nav.profile(convo.otherId) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.block)) }, onClick = { menu = false; vm.block(); nav.back() })
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.messages, key = { it.id }) { m ->
                val mine = m.senderId == vm.myId
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    if (!mine) { Avatar(convo?.otherAvatarUrl, convo?.otherDisplayName ?: "", 28.dp); Spacer(Modifier.padding(3.dp)) }
                    Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                        Text(
                            m.body,
                            color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        val seen = mine && convo != null && m.id <= convo.otherLastReadId
                        Text(relativeTime(m.createdAt) + if (seen) " · " + stringResource(R.string.seen) else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.failed.entries.toList(), key = { it.key }) { (clientId, body) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.send(body, clientId) }) { Text(stringResource(R.string.retry_send), color = Brand.Coral) }
                    Text(body, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp))
                }
            }
        }
        if (convo?.isRequestToMe == true && convo.status == "pending") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.message_request_notice, convo.otherDisplayName), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.respond(false) }) { Text(stringResource(R.string.decline)) }
                    TextButton(onClick = { vm.respond(true) }) { Text(stringResource(R.string.accept)) }
                }
            }
        } else {
            state.error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it.take(Validation.DM_MAX) }, placeholder = { Text(stringResource(R.string.type_message)) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4)
                IconButton(onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send), tint = Brand.Gold)
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(container: AppContainer, nav: Nav) {
    val vm = rememberLoad("notifications") {
        val list = container.messages.notifications()
        val actors = runCatching { container.profiles.basicProfiles(list.mapNotNull { it.actorId }.toSet()) }.getOrDefault(emptyList())
        val gifts = runCatching { container.wallet.gifts() }.getOrDefault(emptyList())
        NotificationFeed(list, actors.associate { it.id to it.displayName }, gifts.associate { it.id to it.name })
    }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.notifications), nav::back)
        LoadContent(vm) { feed ->
            val list = feed.items
            LaunchedEffect(list.firstOrNull()?.id) {
                list.firstOrNull()?.let { scope.launch { runCatching { container.messages.markNotificationsRead(it.id) } } }
            }
            if (list.isEmpty()) EmptyState(R.string.no_notifications)
            else LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { n -> NotificationRow(n, feed, nav) }
            }
        }
    }
}

data class NotificationFeed(val items: List<AppNotification>, val actorNames: Map<String, String>, val giftNames: Map<String, String>)

private fun AppNotification.str(key: String) = (payload[key] as? JsonPrimitive)?.content

@Composable
private fun NotificationRow(n: AppNotification, feed: NotificationFeed, nav: Nav) {
    val name = n.actorId?.let { feed.actorNames[it] } ?: ""
    val text = when (n.type) {
        "follow" -> stringResource(R.string.notif_follow, name)
        "live_start" -> stringResource(R.string.notif_live_start, n.str("title") ?: "")
        "gift" -> stringResource(R.string.notif_gift, name, n.str("gift_id")?.let { feed.giftNames[it] ?: it } ?: "")
        "message", "message_request" -> stringResource(R.string.notif_message, name)
        "pk_invite" -> stringResource(R.string.notif_pk_invite, name)
        "guest_invite" -> stringResource(R.string.notif_guest_invite, name)
        "payout" -> stringResource(R.string.notif_payout, n.str("status") ?: "")
        "moderation" -> stringResource(R.string.notif_moderation)
        "report_update" -> stringResource(R.string.notif_report_update)
        else -> stringResource(R.string.notif_generic)
    }
    val target: (() -> Unit)? = when (n.type) {
        "live_start", "guest_invite", "pk_invite" -> n.str("room_id")?.let { id -> { nav.room(id) } }
        "follow" -> n.actorId?.let { id -> { nav.profile(id) } }
        "message", "message_request" -> n.str("conversation_id")?.let { id -> { nav.conversation(id) } }
        "payout" -> ({ nav.to(Routes.CREATOR) })
        "gift" -> ({ nav.to(Routes.CREATOR) })
        else -> null
    }
    Row(
        Modifier.fillMaxWidth().background(if (n.readAt == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, fontWeight = if (n.readAt == null) FontWeight.SemiBold else FontWeight.Normal)
            Text(relativeTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (target != null) TextButton(onClick = target) { Text(stringResource(R.string.open)) }
    }
}
