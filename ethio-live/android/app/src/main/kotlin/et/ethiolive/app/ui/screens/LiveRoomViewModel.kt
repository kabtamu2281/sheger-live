package et.ethiolive.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import et.ethiolive.app.AppContainer
import et.ethiolive.app.data.Gift
import et.ethiolive.app.data.Participant
import et.ethiolive.app.data.PkMatch
import et.ethiolive.app.data.RoomEvent
import et.ethiolive.app.data.RoomMessage
import et.ethiolive.app.data.RoomSnapshot
import et.ethiolive.app.data.SeatRequest
import et.ethiolive.app.stream.JoinParams
import et.ethiolive.app.stream.StreamSignal
import et.ethiolive.app.ui.gifts.GiftShow
import et.ethiolive.core.AppException
import et.ethiolive.core.Backoff
import et.ethiolive.core.ChatBuffer
import et.ethiolive.core.GiftCombo
import et.ethiolive.core.QualityAdapter
import et.ethiolive.core.ServerError
import et.ethiolive.core.Validation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.util.UUID

enum class RoomPhase { JOINING, PREPARING, LIVE, ENDED, FAILED }

data class LiveRoomState(
    val phase: RoomPhase = RoomPhase.JOINING,
    val snapshot: RoomSnapshot? = null,
    val myUserId: String = "",
    val isHost: Boolean = false,
    val isModerator: Boolean = false,
    val mySeat: Int? = null,
    val myMicMuted: Boolean = false,
    val myCameraOff: Boolean = false,
    val messages: List<RoomMessage> = emptyList(),
    val viewerCount: Int = 0,
    val remoteUids: Set<Int> = emptySet(),
    val remoteVideoOff: Set<Int> = emptySet(),
    val pk: PkMatch? = null,
    val serverOffsetMs: Long = 0,
    val coins: Long = 0,
    val promoCoins: Long = 0,
    val gifts: List<Gift> = emptyList(),
    val banner: GiftShow? = null,
    val fullscreen: GiftShow? = null,
    val pendingRequests: List<SeatRequest> = emptyList(),
    val myInviteId: String? = null,
    val myRequestId: String? = null,
    val failure: Throwable? = null,
    val endedReason: String? = null,
    val beauty: Boolean = true,
)

sealed interface RoomToast {
    data class Error(val error: Throwable) : RoomToast
    data class Info(val key: String) : RoomToast
}

/**
 * Drives one live room. Server state (participants, seats, PK scores, viewer counts) always comes from Postgres;
 * the client only renders it and reacts to realtime change events, re-fetching the snapshot when needed.
 */
class LiveRoomViewModel(private val c: AppContainer, val roomId: String) : ViewModel() {
    private val _state = MutableStateFlow(LiveRoomState(myUserId = c.backend.userId.orEmpty()))
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<RoomToast>(extraBufferCapacity = 8)
    val toasts = _toasts.asSharedFlow()

    val stream get() = c.stream
    private val chat = ChatBuffer<RoomMessage>(200) { it.id }
    private val combo = GiftCombo()
    private val names = HashMap<String, Pair<String, String?>>() // id -> display name, avatar
    private val fullscreenQueue = ArrayDeque<GiftShow>()
    private var bannerJob: Job? = null
    private var snapshotJob: Job? = null
    private var channel: String? = null
    private var audioOnly = false
    private var publisher = false
    private var relayMatchId: String? = null
    private var quality: QualityAdapter? = null
    private var started = false
    private var left = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch { observeStream() }
        viewModelScope.launch { joinFlow() }
    }

    private suspend fun joinFlow() {
        try {
            val snap = c.live.snapshot(roomId)
            val me = c.backend.requireUserId()
            val isHost = snap.room.host.id == me
            audioOnly = snap.room.mode == "audio"
            apply(snap)
            _state.value = _state.value.copy(isHost = isHost, myUserId = me)
            val prefs = c.prefs.prefs.first()
            quality = QualityAdapter(prefs.quality)

            if (isHost && snap.room.status == "preparing") {
                _state.value = _state.value.copy(phase = RoomPhase.PREPARING)
            } else {
                val join = c.live.join(roomId)
                _state.value = _state.value.copy(mySeat = join.seatIndex)
            }
            val token = c.live.streamToken(roomId)
            channel = token.channelName
            publisher = token.publisher
            if (publisher) c.stream.startPreview(audioOnly, quality!!.current)
            c.stream.join(JoinParams(token.appId, token.token, token.channelName, token.rtcUid, token.publisher, audioOnly, quality!!.current))
            if (publisher) c.stream.setBeauty(_state.value.beauty)
            if (isHost) appContext?.let { et.ethiolive.app.stream.BroadcastService.start(it, audioOnly) }
            viewModelScope.launch { events() }
            viewModelScope.launch { heartbeatLoop() }
            viewModelScope.launch { loadWallet() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_state.value.isHost && _state.value.phase == RoomPhase.PREPARING) {
                runCatching { c.live.failLive(roomId, (e as? AppException)?.error?.code ?: "client_error") }
            }
            _state.value = _state.value.copy(phase = RoomPhase.FAILED, failure = e)
        }
    }

    /** Application context for starting the foreground service, provided by the screen. */
    var appContext: android.content.Context? = null

    private suspend fun observeStream() {
        c.stream.signals.collect { signal ->
            when (signal) {
                is StreamSignal.Joined -> onJoined()
                is StreamSignal.RemoteJoined -> _state.value = _state.value.copy(remoteUids = _state.value.remoteUids + signal.uid)
                is StreamSignal.RemoteLeft -> _state.value = _state.value.copy(remoteUids = _state.value.remoteUids - signal.uid)
                is StreamSignal.RemoteVideo -> _state.value = _state.value.copy(
                    remoteVideoOff = if (signal.on) _state.value.remoteVideoOff - signal.uid else _state.value.remoteVideoOff + signal.uid,
                )
                StreamSignal.TokenExpiring -> renewToken()
                is StreamSignal.NetworkQuality -> if (publisher) quality?.onNetworkQuality(signal.score)?.let { c.stream.setQuality(it) }
                is StreamSignal.LocalPublishFailed -> if (_state.value.phase == RoomPhase.PREPARING) failPreparing(signal.reason)
                is StreamSignal.Error -> if (_state.value.phase == RoomPhase.PREPARING) failPreparing("sdk_${signal.code}")
                is StreamSignal.RelayState -> if (signal.failed) _toasts.tryEmit(RoomToast.Info("relay_failed"))
            }
        }
    }

    private fun onJoined() {
        val s = _state.value
        if (s.isHost && s.phase == RoomPhase.PREPARING) {
            // The room becomes discoverable only after the host's stream actually connected.
            viewModelScope.launch {
                try {
                    c.live.confirmLive(roomId)
                    _state.value = _state.value.copy(phase = RoomPhase.LIVE)
                    refreshSnapshot()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.value = _state.value.copy(phase = RoomPhase.FAILED, failure = e)
                }
            }
        } else if (s.phase == RoomPhase.JOINING) {
            _state.value = s.copy(phase = RoomPhase.LIVE)
        }
    }

    private fun failPreparing(reason: String) {
        viewModelScope.launch {
            runCatching { c.live.failLive(roomId, reason) }
            c.stream.leave()
            _state.value = _state.value.copy(phase = RoomPhase.FAILED, failure = AppException(ServerError.STREAMING_UNAVAILABLE, IllegalStateException(reason)))
        }
    }

    private suspend fun renewToken() {
        runCatching { c.live.streamToken(roomId) }.onSuccess { c.stream.renewToken(it.token) }
        relayMatchId?.let { id -> runCatching { c.live.pkRelayToken(id) }.onSuccess { c.stream.startRelay(it.sourceToken, it.destChannel, it.destToken, it.rtcUid) } }
    }

    private suspend fun events() {
        val backoff = Backoff()
        while (true) {
            try {
                c.live.events(roomId).collect { event ->
                    backoff.reset()
                    when (event) {
                        is RoomEvent.Message -> onMessage(event.message)
                        is RoomEvent.MessageRemoved -> _state.value = _state.value.copy(messages = chat.remove(event.id))
                        else -> scheduleSnapshot()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(backoff.nextDelayMs())
                scheduleSnapshot()
            }
        }
    }

    private suspend fun heartbeatLoop() {
        while (true) {
            delay(20_000)
            if (_state.value.phase == RoomPhase.ENDED) return
            try {
                val hb = c.live.heartbeat(roomId)
                _state.value = _state.value.copy(viewerCount = hb.viewerCount)
                if (hb.status != "live" && hb.status != "preparing") onEnded(hb.status)
                else refreshSnapshot()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppException) {
                if (e.error == ServerError.ROOM_NOT_LIVE || e.error == ServerError.NOT_IN_ROOM || e.error == ServerError.REMOVED_FROM_ROOM) onEnded(e.error.code)
            } catch (_: Exception) {
            }
        }
    }

    private fun scheduleSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            delay(300) // coalesce bursts of change events
            refreshSnapshot()
        }
    }

    private suspend fun refreshSnapshot() {
        try {
            apply(c.live.snapshot(roomId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            if (e.error == ServerError.ROOM_NOT_FOUND) onEnded("room_not_found")
        } catch (_: Exception) {
        }
    }

    private fun apply(snap: RoomSnapshot) {
        val me = c.backend.userId
        snap.participants.forEach { names[it.userId] = it.displayName to it.avatarUrl }
        names[snap.room.host.id] = snap.room.host.displayName to snap.room.host.avatarUrl
        snap.messages.forEach { m -> if (m.senderId != null && m.displayName != null) names[m.senderId] = m.displayName to null }
        // Snapshot messages are authoritative (removed messages disappear); keep realtime lines newer than the snapshot.
        val maxId = snap.messages.maxOfOrNull { it.id } ?: 0
        val newer = chat.snapshot().filter { it.id > maxId }
        chat.clear()
        val merged = chat.addAll(snap.messages + newer)
        val mine = snap.participants.firstOrNull { it.userId == me }
        val prev = _state.value
        val offset = snap.pk?.serverNow?.let { runCatching { Instant.parse(it).toEpochMilli() - System.currentTimeMillis() }.getOrNull() } ?: prev.serverOffsetMs
        _state.value = prev.copy(
            snapshot = snap,
            messages = merged,
            viewerCount = snap.room.viewerCount,
            mySeat = mine?.seatIndex ?: if (prev.isHost) 0 else null,
            myMicMuted = mine?.micMuted ?: prev.myMicMuted,
            myCameraOff = mine?.cameraOff ?: prev.myCameraOff,
            pk = snap.pk,
            serverOffsetMs = offset,
            pendingRequests = snap.pendingRequests,
            myInviteId = snap.myInvite?.id,
            myRequestId = if (mine?.seatIndex != null) null else prev.myRequestId,
            isModerator = mine?.role == "moderator",
        )
        if (snap.room.status in setOf("ended", "banned", "failed")) onEnded(snap.room.status)
        syncPublishing(mine)
        syncRelay(snap.pk)
    }

    /** Becomes a publisher when the server seats us, and stops publishing when the seat is taken away. */
    private fun syncPublishing(mine: Participant?) {
        val shouldPublish = mine?.seatIndex != null
        if (shouldPublish == publisher || channel == null) {
            if (publisher && mine != null) {
                // Host-side mute is enforced by the server; mirror it locally so audio actually stops.
                c.stream.setMicMuted(mine.micMuted)
            }
            return
        }
        viewModelScope.launch {
            runCatching { c.live.streamToken(roomId) }.onSuccess { token ->
                publisher = token.publisher
                c.stream.setPublisher(token.publisher, audioOnly, token.token)
                if (token.publisher) c.stream.setBeauty(_state.value.beauty)
            }
        }
    }

    private fun syncRelay(pk: PkMatch?) {
        if (!_state.value.isHost) return
        val running = pk?.status == "running"
        if (running && relayMatchId != pk!!.id) {
            relayMatchId = pk.id
            viewModelScope.launch {
                runCatching { c.live.pkRelayToken(pk.id) }
                    .onSuccess { c.stream.startRelay(it.sourceToken, it.destChannel, it.destToken, it.rtcUid) }
                    .onFailure { _toasts.tryEmit(RoomToast.Error(it)) }
            }
            viewModelScope.launch { pkDeadlineWatcher(pk) }
        } else if (!running && relayMatchId != null) {
            relayMatchId = null
            c.stream.stopRelay()
        }
    }

    /** At the deadline either host asks the server to finish; the server decides the winner from recorded gifts. */
    private suspend fun pkDeadlineWatcher(pk: PkMatch) {
        val ends = pk.endsAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return
        val wait = ends - (System.currentTimeMillis() + _state.value.serverOffsetMs)
        if (wait > 0) delay(wait + 1500)
        if (relayMatchId == pk.id) runCatching { c.pk.finish(pk.id) }
        refreshSnapshot()
    }

    private fun onMessage(m: RoomMessage) {
        val withName = if (m.displayName == null && m.senderId != null) {
            val known = names[m.senderId]
            if (known != null) m.copy(displayName = known.first) else {
                viewModelScope.launch { resolveName(m.senderId) }
                m
            }
        } else m
        _state.value = _state.value.copy(messages = chat.addAll(listOf(withName)))
        when (m.kind) {
            "gift" -> onGiftMessage(withName)
            "pk", "seat" -> scheduleSnapshot()
            "system" -> if (m.body == "live_ended") onEnded("ended")
        }
    }

    private suspend fun resolveName(id: String) {
        val rows = runCatching { c.profiles.basicProfiles(listOf(id)) }.getOrDefault(emptyList())
        rows.forEach { names[it.id] = it.displayName to it.avatarUrl }
        val known = names[id] ?: return
        val updated = chat.snapshot().map { if (it.senderId == id && it.displayName == null) it.copy(displayName = known.first) else it }
        chat.clear()
        _state.value = _state.value.copy(messages = chat.addAll(updated))
    }

    private fun onGiftMessage(m: RoomMessage) {
        val meta = m.meta
        val giftId = (meta["gift_id"] as? JsonPrimitive)?.content ?: return
        val quantity = (meta["quantity"] as? JsonPrimitive)?.intOrNull ?: 1
        val recipient = (meta["recipient_id"] as? JsonPrimitive)?.content.orEmpty()
        val fullscreen = (meta["fullscreen"] as? JsonPrimitive)?.booleanOrNull ?: false
        val gift = _state.value.gifts.firstOrNull { it.id == giftId }
        val sender = m.senderId.orEmpty()
        val count = combo.register(sender, giftId, recipient, quantity, System.currentTimeMillis())
        val show = GiftShow(
            id = m.id,
            senderName = m.displayName ?: names[sender]?.first ?: "…",
            senderAvatar = names[sender]?.second,
            recipientName = names[recipient]?.first ?: "",
            giftName = gift?.name ?: m.body,
            artworkKey = gift?.artworkKey ?: giftId,
            animationKey = (meta["animation_key"] as? JsonPrimitive)?.content ?: "float",
            combo = count,
            fullscreen = fullscreen,
        )
        if (fullscreen) {
            if (_state.value.fullscreen == null) _state.value = _state.value.copy(fullscreen = show) else fullscreenQueue.addLast(show)
        }
        _state.value = _state.value.copy(banner = show)
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(3500)
            _state.value = _state.value.copy(banner = null)
        }
    }

    fun onFullscreenFinished() {
        _state.value = _state.value.copy(fullscreen = fullscreenQueue.removeFirstOrNull())
    }

    private fun onEnded(reason: String) {
        if (_state.value.phase == RoomPhase.ENDED) return
        _state.value = _state.value.copy(phase = RoomPhase.ENDED, endedReason = reason)
        c.stream.leave()
        appContext?.let { et.ethiolive.app.stream.BroadcastService.stop(it) }
    }

    private suspend fun loadWallet() {
        runCatching { c.wallet.gifts() }.onSuccess { _state.value = _state.value.copy(gifts = it) }
        runCatching { c.wallet.wallet() }.onSuccess { _state.value = _state.value.copy(coins = it.coins, promoCoins = it.promoCoins) }
    }

    fun refreshWallet() = viewModelScope.launch { loadWallet() }

    // ---------------------------------------------------------------- user actions

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { _toasts.tryEmit(RoomToast.Error(e)) }
    }

    fun sendChat(text: String) {
        val body = Validation.chatMessage(text) ?: return
        act { c.live.sendMessage(roomId, body) }
    }

    /** Each tap gets a fresh idempotency key; a failed network call is retried once with the same key. */
    fun sendGift(gift: Gift, recipientId: String, quantity: Int) = viewModelScope.launch {
        val key = UUID.randomUUID().toString()
        try {
            val result = try {
                c.wallet.sendGift(roomId, recipientId, gift.id, quantity, key)
            } catch (e: AppException) {
                if (e.error == ServerError.NETWORK) c.wallet.sendGift(roomId, recipientId, gift.id, quantity, key) else throw e
            }
            _state.value = _state.value.copy(coins = result.coins, promoCoins = result.promoCoins)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _toasts.tryEmit(RoomToast.Error(e))
        }
    }

    fun follow() = act {
        val host = _state.value.snapshot?.room?.host?.id ?: return@act
        c.profiles.follow(host)
        refreshSnapshot()
    }

    fun toggleMic() {
        val muted = !_state.value.myMicMuted
        c.stream.setMicMuted(muted)
        _state.value = _state.value.copy(myMicMuted = muted)
        act { c.live.setMediaState(roomId, muted, _state.value.myCameraOff) }
    }

    fun toggleCamera() {
        val off = !_state.value.myCameraOff
        c.stream.setCameraOff(off)
        _state.value = _state.value.copy(myCameraOff = off)
        act { c.live.setMediaState(roomId, _state.value.myMicMuted, off) }
    }

    fun toggleBeauty() {
        val on = !_state.value.beauty
        c.stream.setBeauty(on)
        _state.value = _state.value.copy(beauty = on)
    }

    fun switchCamera() = c.stream.switchCamera()

    fun requestSeat() = act {
        val id = c.live.requestSeat(roomId)
        _state.value = _state.value.copy(myRequestId = id)
        _toasts.tryEmit(RoomToast.Info("seat_requested"))
    }

    fun cancelSeatRequest() = act {
        _state.value.myRequestId?.let { c.live.cancelSeatRequest(it) }
        _state.value = _state.value.copy(myRequestId = null)
    }

    fun respondInvite(accept: Boolean) = act {
        val id = _state.value.myInviteId ?: return@act
        c.live.respondSeatInvite(id, accept)
        _state.value = _state.value.copy(myInviteId = null)
        refreshSnapshot()
    }

    fun respondRequest(requestId: String, accept: Boolean) = act { c.live.respondSeatRequest(requestId, accept); refreshSnapshot() }
    fun inviteToSeat(userId: String) = act { c.live.inviteToSeat(roomId, userId); _toasts.tryEmit(RoomToast.Info("invite_sent")) }
    fun leaveSeat() = act { c.live.leaveSeat(roomId); refreshSnapshot() }
    fun removeGuest(userId: String, kick: Boolean) = act { c.live.removeGuest(roomId, userId, kick); refreshSnapshot() }
    fun muteGuest(userId: String, muted: Boolean) = act { c.live.hostMuteGuest(roomId, userId, muted); refreshSnapshot() }
    fun muteInChat(userId: String) = act { c.live.muteInRoom(roomId, userId, 10); _toasts.tryEmit(RoomToast.Info("user_muted")) }
    fun setModerator(userId: String, enabled: Boolean) = act { c.live.setModerator(roomId, userId, enabled) }
    fun removeMessage(id: Long) = act { c.live.removeMessage(id); _state.value = _state.value.copy(messages = chat.remove(id)) }
    fun report(reason: String, details: String) = act { c.live.reportRoom(roomId, reason, details); _toasts.tryEmit(RoomToast.Info("report_sent")) }
    fun blockHost() = act {
        val host = _state.value.snapshot?.room?.host?.id ?: return@act
        c.profiles.block(host)
        onEnded("blocked")
    }

    fun pkInvite(targetRoom: String, seconds: Int) = act { c.pk.invite(roomId, targetRoom, seconds); _toasts.tryEmit(RoomToast.Info("pk_invited")) }
    fun pkRespond(accept: Boolean) = act { _state.value.pk?.let { c.pk.respond(it.id, accept) }; refreshSnapshot() }
    fun pkCancel() = act { _state.value.pk?.let { c.pk.cancel(it.id) }; refreshSnapshot() }
    suspend fun pkCandidates() = c.pk.candidates(roomId)
    suspend fun viewers() = c.live.viewers(roomId)

    fun endLive(onDone: () -> Unit) = viewModelScope.launch {
        runCatching { c.live.endLive(roomId) }
        onEnded("ended_by_host")
        onDone()
    }

    /** Leaving the screen: viewers leave the room; a host's room keeps running until they end it or the server times it out. */
    fun leave() {
        if (left) return
        left = true
        val wasHost = _state.value.isHost
        val ended = _state.value.phase == RoomPhase.ENDED
        c.stream.leave()
        if (!ended) c.backend.scope.launch {
            if (wasHost) runCatching { c.live.endLive(roomId) } else runCatching { c.live.leave(roomId) }
        }
        appContext?.let { et.ethiolive.app.stream.BroadcastService.stop(it) }
    }

    override fun onCleared() {
        leave()
        super.onCleared()
    }

    val connection get() = c.stream.connection
}
