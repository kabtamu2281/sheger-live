package et.ethiolive.app.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

class MessageRepository(private val backend: Backend) {
    suspend fun conversations(requests: Boolean) =
        backend.rpc<List<Conversation>>("list_conversations", params("p_requests" to requests, "p_limit" to 50, "p_offset" to 0))

    suspend fun open(otherUserId: String) = backend.rpc<String>("open_conversation", params("p_other" to otherUserId))

    suspend fun messages(conversationId: String, beforeId: Long? = null): List<DirectMessage> = backend.call {
        backend.client.from("dm_messages").select {
            filter {
                eq("conversation_id", conversationId)
                if (beforeId != null) lt("id", beforeId)
            }
            order("id", Order.DESCENDING)
            limit(50)
        }.decodeList<DirectMessage>().reversed()
    }

    /** client_id makes a resend after a dropped connection return the original message instead of duplicating it. */
    suspend fun send(conversationId: String, body: String, clientId: String = UUID.randomUUID().toString()) =
        backend.rpc<DirectMessage>("send_dm", params("p_conversation" to conversationId, "p_body" to body, "p_client_id" to clientId))

    suspend fun respondRequest(conversationId: String, accept: Boolean) =
        backend.rpcUnit("respond_message_request", params("p_conversation" to conversationId, "p_accept" to accept))

    suspend fun markRead(conversationId: String, lastId: Long) =
        backend.rpcUnit("mark_conversation_read", params("p_conversation" to conversationId, "p_last_id" to lastId))

    fun incoming(conversationId: String): Flow<DirectMessage> = callbackFlow {
        val channel = backend.client.channel("dm-$conversationId")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "dm_messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.onEach { runCatching { it.decodeRecord<DirectMessage>() }.getOrNull()?.let { m -> trySend(m) } }.launchIn(this)
        launch { runCatching { channel.subscribe() } }
        awaitClose { backend.scope.launch { runCatching { backend.client.realtime.removeChannel(channel) } } }
    }

    suspend fun notifications(): List<AppNotification> = backend.call {
        backend.client.from("notifications").select { order("id", Order.DESCENDING); limit(60) }.decodeList<AppNotification>()
    }

    suspend fun markNotificationsRead(upTo: Long) = backend.rpcUnit("mark_notifications_read", params("p_up_to" to upTo))

    fun notificationStream(userId: String): Flow<AppNotification> = callbackFlow {
        val channel = backend.client.channel("notes-$userId")
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notifications"
            filter("user_id", FilterOperator.EQ, userId)
        }.onEach { runCatching { it.decodeRecord<AppNotification>() }.getOrNull()?.let { n -> trySend(n) } }.launchIn(this)
        launch { runCatching { channel.subscribe() } }
        awaitClose { backend.scope.launch { runCatching { backend.client.realtime.removeChannel(channel) } } }
    }
}
