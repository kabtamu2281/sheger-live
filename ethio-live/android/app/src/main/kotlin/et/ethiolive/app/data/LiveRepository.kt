package et.ethiolive.app.data

import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Live room events pushed by Postgres changes. The snapshot is re-fetched whenever the channel (re)subscribes. */
sealed interface RoomEvent {
    data class Message(val message: RoomMessage) : RoomEvent
    data class MessageRemoved(val id: Long) : RoomEvent
    data object ParticipantsChanged : RoomEvent
    data object RoomChanged : RoomEvent
    data object SeatRequestsChanged : RoomEvent
    data object PkChanged : RoomEvent
    data object Resubscribed : RoomEvent
}

class LiveRepository(private val backend: Backend) {
    suspend fun startLive(mode: String, title: String, category: String, language: String, audience: String, hashtags: List<String>, maxSeats: Int, coverUrl: String?) =
        backend.rpc<LiveRoomRow>(
            "start_live",
            params("p_mode" to mode, "p_title" to title, "p_category" to category, "p_language" to language, "p_audience" to audience,
                "p_hashtags" to hashtags, "p_max_seats" to maxSeats, "p_cover_url" to coverUrl),
        )

    suspend fun confirmLive(roomId: String) = backend.rpc<LiveRoomRow>("confirm_live", params("p_room" to roomId))
    suspend fun failLive(roomId: String, reason: String) = backend.rpcUnit("fail_live", params("p_room" to roomId, "p_reason" to reason))
    suspend fun endLive(roomId: String) = backend.rpcUnit("end_live", params("p_room" to roomId))
    suspend fun join(roomId: String) = backend.rpc<JoinResult>("join_room", params("p_room" to roomId))
    suspend fun leave(roomId: String) = backend.rpcUnit("leave_room", params("p_room" to roomId))
    suspend fun heartbeat(roomId: String) = backend.rpc<HeartbeatResult>("room_heartbeat", params("p_room" to roomId))
    suspend fun snapshot(roomId: String) = backend.rpc<RoomSnapshot>("room_snapshot", params("p_room" to roomId))
    suspend fun viewers(roomId: String) = backend.rpc<List<Viewer>>("room_viewers", params("p_room" to roomId, "p_limit" to 100))

    suspend fun sendMessage(roomId: String, body: String) = backend.rpc<Long>("send_room_message", params("p_room" to roomId, "p_body" to body))
    suspend fun removeMessage(id: Long) = backend.rpcUnit("remove_room_message", params("p_message" to id))
    suspend fun muteInRoom(roomId: String, userId: String, minutes: Int) = backend.rpcUnit("mute_in_room", params("p_room" to roomId, "p_user" to userId, "p_minutes" to minutes))
    suspend fun setModerator(roomId: String, userId: String, enabled: Boolean) = backend.rpcUnit("set_room_moderator", params("p_room" to roomId, "p_user" to userId, "p_enabled" to enabled))

    suspend fun requestSeat(roomId: String) = backend.rpc<String>("request_seat", params("p_room" to roomId))
    suspend fun cancelSeatRequest(requestId: String) = backend.rpcUnit("cancel_seat_request", params("p_request" to requestId))
    suspend fun respondSeatRequest(requestId: String, accept: Boolean) = backend.rpcRaw("respond_seat_request", params("p_request" to requestId, "p_accept" to accept))
    suspend fun inviteToSeat(roomId: String, userId: String) = backend.rpc<String>("invite_to_seat", params("p_room" to roomId, "p_user" to userId))
    suspend fun respondSeatInvite(requestId: String, accept: Boolean) = backend.rpcRaw("respond_seat_invite", params("p_request" to requestId, "p_accept" to accept))
    suspend fun leaveSeat(roomId: String) = backend.rpcUnit("leave_seat", params("p_room" to roomId))
    suspend fun removeGuest(roomId: String, userId: String, kick: Boolean) = backend.rpcUnit("remove_guest", params("p_room" to roomId, "p_user" to userId, "p_kick" to kick))
    suspend fun hostMuteGuest(roomId: String, userId: String, muted: Boolean) = backend.rpcUnit("host_set_guest_mute", params("p_room" to roomId, "p_user" to userId, "p_muted" to muted))
    suspend fun setMediaState(roomId: String, micMuted: Boolean, cameraOff: Boolean) = backend.rpcUnit("set_media_state", params("p_room" to roomId, "p_mic_muted" to micMuted, "p_camera_off" to cameraOff))

    /** Short-lived RTC token minted server-side by the stream-token edge function (the certificate never leaves the server). */
    suspend fun streamToken(roomId: String) = backend.function<StreamToken>("stream-token", params("room_id" to roomId))
    suspend fun pkRelayToken(matchId: String) = backend.function<PkRelayToken>("stream-token", params("pk_match_id" to matchId))

    suspend fun reportRoom(roomId: String, reason: String, details: String) =
        backend.rpc<String>("report_content", params("p_target_type" to "room", "p_target_id" to roomId, "p_reason" to reason, "p_details" to details))

    fun events(roomId: String): Flow<RoomEvent> = callbackFlow {
        val channel = backend.client.channel("room-$roomId")
        val messages = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "room_messages"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        val participants = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "room_participants"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        val room = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "live_rooms"
            filter("id", FilterOperator.EQ, roomId)
        }
        val seats = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "seat_requests"
            filter("room_id", FilterOperator.EQ, roomId)
        }
        val pkA = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "pk_matches"
            filter("room_a", FilterOperator.EQ, roomId)
        }
        val pkB = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "pk_matches"
            filter("room_b", FilterOperator.EQ, roomId)
        }
        messages.onEach { action ->
            when (action) {
                is PostgresAction.Insert -> runCatching { action.decodeRecord<RoomMessage>() }.getOrNull()?.let { trySend(RoomEvent.Message(it)) }
                is PostgresAction.Update -> {
                    val msg = runCatching { action.decodeRecord<RoomMessage>() }.getOrNull()
                    if (msg?.removedAt != null) trySend(RoomEvent.MessageRemoved(msg.id))
                }
                else -> Unit
            }
        }.launchIn(this)
        participants.onEach { trySend(RoomEvent.ParticipantsChanged) }.launchIn(this)
        room.onEach { trySend(RoomEvent.RoomChanged) }.launchIn(this)
        seats.onEach { trySend(RoomEvent.SeatRequestsChanged) }.launchIn(this)
        pkA.onEach { trySend(RoomEvent.PkChanged) }.launchIn(this)
        pkB.onEach { trySend(RoomEvent.PkChanged) }.launchIn(this)
        var wasSubscribed = false
        channel.status.onEach { status ->
            if (status == RealtimeChannel.Status.SUBSCRIBED) {
                if (wasSubscribed) trySend(RoomEvent.Resubscribed)
                wasSubscribed = true
            } else if (status == RealtimeChannel.Status.UNSUBSCRIBED && wasSubscribed) {
                // The realtime client reconnects on its own; the next SUBSCRIBED triggers a snapshot reload.
                trySend(RoomEvent.Resubscribed)
            }
        }.launchIn(this)
        launch { runCatching { channel.subscribe() } }
        awaitClose {
            backend.scope.launch { runCatching { backend.client.realtime.removeChannel(channel) } }
        }
    }
}
