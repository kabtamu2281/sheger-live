package et.ethiolive.app.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import et.ethiolive.app.AppContainer
import et.ethiolive.app.MainActivity
import et.ethiolive.app.R
import et.ethiolive.app.data.BackendConfig
import et.ethiolive.app.data.Participant
import et.ethiolive.app.data.RoomMessage
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.components.ErrorState
import et.ethiolive.app.ui.components.LevelBadge
import et.ethiolive.app.ui.components.Loading
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.ViewerPill
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.gifts.FullscreenGiftEffect
import et.ethiolive.app.ui.gifts.GiftBanner
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import kotlinx.coroutines.delay

private enum class RoomSheet { NONE, GIFTS, VIEWERS, REQUESTS, PK, MORE, REPORT }

@Composable
fun LiveRoomScreen(container: AppContainer, nav: Nav, activity: MainActivity, roomId: String) {
    val vm: LiveRoomViewModel = viewModel(key = "room-$roomId") { LiveRoomViewModel(container, roomId) }
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val speaking by vm.stream.speaking.collectAsState()
    val connection by vm.connection.collectAsState()
    var sheet by remember { mutableStateOf(RoomSheet.NONE) }
    var confirmEnd by remember { mutableStateOf(false) }
    var selectedGuest by remember { mutableStateOf<Participant?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingSeatAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val audioOnly = state.snapshot?.room?.mode == "audio"
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) pendingSeatAction?.invoke()
        else Toast.makeText(context, context.getString(R.string.permission_needed_seat), Toast.LENGTH_LONG).show()
        pendingSeatAction = null
    }
    fun withMediaPermission(action: () -> Unit) {
        pendingSeatAction = action
        permissionLauncher.launch(if (audioOnly) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
    }

    LaunchedEffect(Unit) {
        vm.appContext = context.applicationContext
        vm.start()
    }
    LaunchedEffect(state.pk?.status) {
        while (state.pk?.status == "running") { now = System.currentTimeMillis(); delay(500) }
    }
    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            val text = when (t) {
                is RoomToast.Error -> null
                is RoomToast.Info -> when (t.key) {
                    "seat_requested" -> context.getString(R.string.seat_requested)
                    "invite_sent" -> context.getString(R.string.invite_sent)
                    "user_muted" -> context.getString(R.string.user_muted)
                    "report_sent" -> context.getString(R.string.report_sent)
                    "pk_invited" -> context.getString(R.string.pk_invited)
                    "relay_failed" -> context.getString(R.string.pk_relay_failed)
                    else -> null
                }
            }
            val message = text ?: (t as? RoomToast.Error)?.let { e ->
                context.getString(et.ethiolive.app.ui.components.errorRes((e.error as? et.ethiolive.core.AppException)?.error ?: et.ethiolive.core.ServerError.UNKNOWN))
            }
            if (message != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(Unit) { onDispose { if (activity.isFinishing) vm.leave() } }

    BackHandler {
        if (state.isHost && state.phase != RoomPhase.ENDED && state.phase != RoomPhase.FAILED) confirmEnd = true
        else { vm.leave(); nav.back() }
    }

    val snap = state.snapshot
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !BackendConfig.streamingConfigured -> StreamingNotConfigured { nav.back() }
            state.phase == RoomPhase.FAILED -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                ErrorState(state.failure, onRetry = null, modifier = Modifier.weight(1f))
                PrimaryButton(stringResource(R.string.close), onClick = { vm.leave(); nav.back() }, modifier = Modifier.padding(24.dp))
            }
            state.phase == RoomPhase.ENDED -> EndedView(state, onClose = { vm.leave(); nav.back() }, onProfile = { snap?.room?.host?.id?.let(nav::profile) })
            snap == null -> Loading()
            else -> {
                val participants = snap.participants
                val canSeat = !state.isHost && state.mySeat == null && snap.room.maxSeats > 1
                val onEmptySeat: (() -> Unit)? = when {
                    canSeat && state.myRequestId == null -> ({ withMediaPermission { vm.requestSeat() } })
                    state.isHost -> ({ sheet = RoomSheet.VIEWERS })
                    else -> null
                }
                val onSeatClick: ((Participant) -> Unit)? = { p -> if (p.userId != state.myUserId) selectedGuest = p }
                when {
                    state.pk != null && state.pk!!.status in setOf("running", "invited") && snap.room.mode != "audio" -> {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.Night, Color.Black))))
                        PkStage(vm.stream, state.pk!!, roomId, participants.firstOrNull { it.role == "host" }, state.myUserId,
                            state.remoteUids, state.serverOffsetMs, now)
                    }
                    snap.room.mode == "audio" -> {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1660), Brand.Night))))
                        AudioStage(snap.room.maxSeats, participants, state.myUserId, speaking, onEmptySeat, onSeatClick)
                    }
                    else -> VideoStage(vm.stream, snap.room.maxSeats, participants, state.myUserId, state.remoteUids, state.remoteVideoOff,
                        speaking, onEmptySeat, onSeatClick)
                }
                // Readability gradient for overlays.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x88000000), 0.18f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color(0xAA000000))))

                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    RoomTopBar(state, onFollow = vm::follow, onViewers = { sheet = RoomSheet.VIEWERS },
                        onProfile = { nav.profile(snap.room.host.id) },
                        onClose = { if (state.isHost) confirmEnd = true else { vm.leave(); nav.back() } })
                    if (state.phase == RoomPhase.PREPARING || connection == et.ethiolive.app.stream.ConnectionState.RECONNECTING) {
                        Text(
                            stringResource(if (state.phase == RoomPhase.PREPARING) R.string.going_live else R.string.reconnecting),
                            color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                                .background(Brand.Scrim, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    GiftBanner(state.banner, Modifier.padding(start = 8.dp, bottom = 8.dp))
                    ChatList(state.messages, state.isHost || state.isModerator, onRemove = vm::removeMessage, modifier = Modifier.heightIn(max = 220.dp))
                    BottomBar(
                        state = state,
                        canSeat = canSeat,
                        onSend = vm::sendChat,
                        onGift = { vm.refreshWallet(); sheet = RoomSheet.GIFTS },
                        onSeat = {
                            when {
                                state.mySeat != null -> vm.leaveSeat()
                                state.myRequestId != null -> vm.cancelSeatRequest()
                                else -> withMediaPermission { vm.requestSeat() }
                            }
                        },
                        onMore = { sheet = RoomSheet.MORE },
                        onRequests = { sheet = RoomSheet.REQUESTS },
                        onPk = { sheet = RoomSheet.PK },
                        onMic = vm::toggleMic,
                        onCamera = vm::toggleCamera,
                        onFlip = vm::switchCamera,
                        onBeauty = vm::toggleBeauty,
                    )
                }
                state.fullscreen?.let { FullscreenGiftEffect(it, vm::onFullscreenFinished) }
            }
        }
    }

    when (sheet) {
        RoomSheet.GIFTS -> GiftPanel(
            gifts = state.gifts,
            coins = state.coins,
            promoCoins = state.promoCoins,
            recipients = snap?.participants?.filter { it.seatIndex != null && it.userId != state.myUserId }.orEmpty(),
            onSend = { gift, recipient, qty -> vm.sendGift(gift, recipient, qty) },
            onRecharge = { sheet = RoomSheet.NONE; nav.to(Routes.WALLET) },
            onDismiss = { sheet = RoomSheet.NONE },
        )
        RoomSheet.VIEWERS -> ViewersSheet(vm, state, onProfile = { sheet = RoomSheet.NONE; nav.profile(it) }, onDismiss = { sheet = RoomSheet.NONE })
        RoomSheet.REQUESTS -> SeatRequestsSheet(state.pendingRequests, onRespond = vm::respondRequest, onDismiss = { sheet = RoomSheet.NONE })
        RoomSheet.PK -> PkSheet(vm, state, onDismiss = { sheet = RoomSheet.NONE })
        RoomSheet.MORE -> MoreSheet(
            isHost = state.isHost,
            onEnd = { sheet = RoomSheet.NONE; confirmEnd = true },
            onReport = { sheet = RoomSheet.REPORT },
            onBlock = { vm.blockHost(); sheet = RoomSheet.NONE },
            onDismiss = { sheet = RoomSheet.NONE },
        )
        RoomSheet.REPORT -> ReportSheet(onSubmit = { reason, details -> vm.report(reason, details); sheet = RoomSheet.NONE }, onDismiss = { sheet = RoomSheet.NONE })
        RoomSheet.NONE -> Unit
    }

    selectedGuest?.let { g ->
        GuestActionsDialog(
            guest = g,
            isHost = state.isHost || state.isModerator,
            onProfile = { selectedGuest = null; nav.profile(g.userId) },
            onMute = { vm.muteGuest(g.userId, !g.mutedByHost); selectedGuest = null },
            onRemove = { vm.removeGuest(g.userId, false); selectedGuest = null },
            onKick = { vm.removeGuest(g.userId, true); selectedGuest = null },
            onDismiss = { selectedGuest = null },
        )
    }

    if (state.myInviteId != null) {
        AlertDialog(
            onDismissRequest = { vm.respondInvite(false) },
            title = { Text(stringResource(R.string.seat_invite_title)) },
            text = { Text(stringResource(R.string.seat_invite_body, snap?.room?.host?.displayName.orEmpty())) },
            confirmButton = { TextButton(onClick = { withMediaPermission { vm.respondInvite(true) } }) { Text(stringResource(R.string.join_seat)) } },
            dismissButton = { TextButton(onClick = { vm.respondInvite(false) }) { Text(stringResource(R.string.decline)) } },
        )
    }

    val pk = state.pk
    if (pk != null && pk.status == "invited" && pk.hostB == state.myUserId) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.pk_invite_title)) },
            text = { Text(stringResource(R.string.pk_invite_body, pk.durationSeconds / 60)) },
            confirmButton = { TextButton(onClick = { vm.pkRespond(true) }) { Text(stringResource(R.string.accept)) } },
            dismissButton = { TextButton(onClick = { vm.pkRespond(false) }) { Text(stringResource(R.string.decline)) } },
        )
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text(stringResource(R.string.end_live_title)) },
            text = { Text(stringResource(R.string.end_live_body)) },
            confirmButton = { TextButton(onClick = { confirmEnd = false; vm.endLive {} }) { Text(stringResource(R.string.end_live)) } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun StreamingNotConfigured(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.streaming_not_configured_title), color = Color.White, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.streaming_not_configured_body), color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.size(24.dp))
        PrimaryButton(stringResource(R.string.close), onClick = onClose)
    }
}

@Composable
private fun RoomTopBar(state: LiveRoomState, onFollow: () -> Unit, onViewers: () -> Unit, onProfile: () -> Unit, onClose: () -> Unit) {
    val snap = state.snapshot ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Brand.Scrim).clickable(onClick = onProfile).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(snap.room.host.avatarUrl, snap.room.host.displayName, 34.dp)
            Column(Modifier.padding(horizontal = 6.dp).widthIn(max = 120.dp)) {
                Text(snap.room.host.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snap.room.title, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!state.isHost && !snap.hostFollowed) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brand.Coral).clickable(onClick = onFollow).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Text(stringResource(R.string.follow), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clickable(onClick = onViewers)) { ViewerPill(state.viewerCount) }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(34.dp).clip(CircleShape).background(Brand.Scrim).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, stringResource(R.string.close), tint = Color.White)
        }
    }
}

@Composable
private fun ChatList(messages: List<RoomMessage>, canModerate: Boolean, onRemove: (Long) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    var target by remember { mutableStateOf<RoomMessage?>(null) }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth(0.78f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(messages, key = { it.id }) { m ->
            val text = when (m.kind) {
                "system" -> buildAnnotatedString { withStyle(SpanStyle(color = Brand.Gold)) { append(systemText(m.body)) } }
                "gift" -> buildAnnotatedString {
                    withStyle(SpanStyle(color = Brand.Gold, fontWeight = FontWeight.Bold)) { append((m.displayName ?: "…") + " ") }
                    withStyle(SpanStyle(color = Color.White)) { append(stringResource(R.string.chat_sent_gift, m.body)) }
                }
                "pk" -> buildAnnotatedString { withStyle(SpanStyle(color = Brand.Sky)) { append(stringResource(if (m.body == "pk_started") R.string.pk_started else R.string.pk_finished)) } }
                else -> buildAnnotatedString {
                    withStyle(SpanStyle(color = Brand.VioletSoft, fontWeight = FontWeight.Bold)) { append((m.displayName ?: m.username ?: "…") + ": ") }
                    withStyle(SpanStyle(color = Color.White)) { append(m.body) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.level != null && m.kind == "chat") { LevelBadge(m.level); Spacer(Modifier.width(4.dp)) }
                Text(
                    text, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x55000000))
                        .clickable(enabled = canModerate && m.kind == "chat") { target = m }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
    target?.let { m ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text(stringResource(R.string.remove_message_title)) },
            text = { Text(m.body) },
            confirmButton = { TextButton(onClick = { onRemove(m.id); target = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { target = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun systemText(key: String): String = when (key) {
    "live_started" -> stringResource(R.string.sys_live_started)
    "live_ended" -> stringResource(R.string.sys_live_ended)
    else -> stringResource(R.string.sys_welcome)
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Color.White, background: Color = Brand.Scrim, badge: Int = 0) {
    BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint)
        }
    }
}

@Composable
private fun BottomBar(
    state: LiveRoomState,
    canSeat: Boolean,
    onSend: (String) -> Unit,
    onGift: () -> Unit,
    onSeat: () -> Unit,
    onMore: () -> Unit,
    onRequests: () -> Unit,
    onPk: () -> Unit,
    onMic: () -> Unit,
    onCamera: () -> Unit,
    onFlip: () -> Unit,
    onBeauty: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val publishing = state.mySeat != null
    val video = state.snapshot?.room?.mode != "audio"
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        if (publishing) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            RoundAction(if (state.myMicMuted) Icons.Default.MicOff else Icons.Default.Mic, stringResource(R.string.mic), onMic,
                tint = if (state.myMicMuted) Brand.Coral else Color.White)
            if (video) {
                RoundAction(if (state.myCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam, stringResource(R.string.camera), onCamera)
                RoundAction(Icons.Default.Cameraswitch, stringResource(R.string.flip_camera), onFlip)
                RoundAction(Icons.Default.Face, stringResource(R.string.beauty), onBeauty, tint = if (state.beauty) Brand.Gold else Color.White)
            }
            if (state.isHost) {
                if ((state.snapshot?.room?.maxSeats ?: 1) > 1) RoundAction(Icons.Default.PersonAdd, stringResource(R.string.seat_requests), onRequests, badge = state.pendingRequests.size)
                if (video && state.snapshot?.room?.maxSeats == 1) RoundAction(Icons.Default.SportsMma, stringResource(R.string.pk_battle), onPk)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(Brand.Scrim).padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (text.isEmpty()) Text(stringResource(R.string.say_something), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                BasicTextField(
                    value = text, onValueChange = { text = it.take(et.ethiolive.core.Validation.CHAT_MAX) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp), singleLine = true,
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (canSeat || (publishing && !state.isHost)) {
                RoundAction(Icons.Default.EventSeat, stringResource(if (publishing) R.string.leave_seat else if (state.myRequestId != null) R.string.cancel_request else R.string.request_seat),
                    onSeat, tint = if (state.myRequestId != null) Brand.Gold else Color.White)
            }
            if (!state.isHost) Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brand.goldGradient).clickable(onClick = onGift),
                contentAlignment = Alignment.Center,
            ) { et.ethiolive.app.ui.gifts.GiftArt("gift", 28.dp) }
            if (state.isHost) RoundAction(Icons.Default.CallEnd, stringResource(R.string.end_live), onMore, background = Brand.Coral)
            else RoundAction(Icons.Default.MoreHoriz, stringResource(R.string.more), onMore)
        }
    }
}

@Composable
private fun EndedView(state: LiveRoomState, onClose: () -> Unit, onProfile: () -> Unit) {
    val snap = state.snapshot
    Column(
        Modifier.fillMaxSize().background(Brand.Night).statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        if (snap != null) Avatar(snap.room.host.avatarUrl, snap.room.host.displayName, 88.dp)
        Spacer(Modifier.size(16.dp))
        Text(stringResource(if (state.endedReason == "banned") R.string.room_removed else R.string.live_ended), style = MaterialTheme.typography.titleLarge, color = Color.White)
        if (snap != null) Text(snap.room.host.displayName, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.size(24.dp))
        if (snap != null && !state.isHost) PrimaryButton(stringResource(R.string.view_profile), onClick = onProfile)
        Spacer(Modifier.size(8.dp))
        SecondaryButton(stringResource(R.string.close), onClick = onClose)
    }
}

@Composable
private fun GuestActionsDialog(
    guest: Participant,
    isHost: Boolean,
    onProfile: () -> Unit,
    onMute: () -> Unit,
    onRemove: () -> Unit,
    onKick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Avatar(guest.avatarUrl, guest.displayName, 56.dp) },
        title = { Text(guest.displayName) },
        text = {
            Column {
                TextButton(onClick = onProfile) { Text(stringResource(R.string.view_profile)) }
                if (isHost && guest.role != "host") {
                    TextButton(onClick = onMute) { Text(stringResource(if (guest.mutedByHost) R.string.unmute_guest else R.string.mute_guest)) }
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_from_seat)) }
                    TextButton(onClick = onKick) { Text(stringResource(R.string.kick_from_room), color = Brand.Coral) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
