package et.ethiolive.app.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonObject

class ProfileRepository(private val backend: Backend) {
    suspend fun updateProfile(displayName: String, bio: String, avatarUrl: String?, language: String, interests: List<String>, privateAccount: Boolean) =
        backend.rpc<Profile>(
            "update_profile",
            params("p_display_name" to displayName, "p_bio" to bio, "p_avatar_url" to avatarUrl, "p_language" to language,
                "p_interests" to interests, "p_private_account" to privateAccount),
        )

    suspend fun changeUsername(username: String) = backend.rpc<Profile>("change_username", params("p_username" to username))

    suspend fun updateSettings(changes: Map<String, Any?>) = backend.rpc<UserSettings>("update_settings", params("p" to changes))

    suspend fun setNearbyRegion(region: String?) = backend.rpcUnit("set_nearby_region", params("p_region" to region))

    /** Uploads to the caller's own folder (storage policies enforce it) and returns the public URL. */
    suspend fun uploadAvatar(bytes: ByteArray): String = backend.call {
        val uid = backend.requireUserId()
        val path = "$uid/avatar-${System.currentTimeMillis()}.jpg"
        val bucket = backend.client.storage.from("avatars")
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    suspend fun uploadCover(bytes: ByteArray): String = backend.call {
        val uid = backend.requireUserId()
        val path = "$uid/cover-${System.currentTimeMillis()}.jpg"
        val bucket = backend.client.storage.from("covers")
        bucket.upload(path, bytes) { upsert = true }
        bucket.publicUrl(path)
    }

    /** Minimal public fields for a batch of users (names for realtime chat lines). */
    suspend fun basicProfiles(ids: Collection<String>): List<FollowRow> = if (ids.isEmpty()) emptyList() else backend.call {
        backend.client.from("profiles").select(io.github.jan.supabase.postgrest.query.Columns.list("id", "username", "display_name", "avatar_url", "level")) {
            filter { isIn("id", ids.toList()) }
        }.decodeList<FollowRow>()
    }

    suspend fun publicProfile(userId: String) = backend.rpc<PublicProfile>("public_profile", params("p_user" to userId))
    suspend fun follow(userId: String) = backend.rpcUnit("follow_user", params("p_target" to userId))
    suspend fun unfollow(userId: String) = backend.rpcUnit("unfollow_user", params("p_target" to userId))
    suspend fun block(userId: String) = backend.rpcUnit("block_user", params("p_target" to userId))
    suspend fun unblock(userId: String) = backend.rpcUnit("unblock_user", params("p_target" to userId))
    suspend fun blocked() = backend.rpc<List<BlockedRow>>("list_blocked")

    suspend fun follows(userId: String, followers: Boolean, offset: Int = 0) = backend.rpc<List<FollowRow>>(
        "list_follows",
        params("p_user" to userId, "p_direction" to if (followers) "followers" else "following", "p_limit" to 30, "p_offset" to offset),
    )

    suspend fun report(targetType: String, targetId: String, reason: String, details: String) =
        backend.rpc<String>("report_content", params("p_target_type" to targetType, "p_target_id" to targetId, "p_reason" to reason, "p_details" to details))

    suspend fun myReports() = backend.call {
        backend.client.from("reports").select { order("created_at", Order.DESCENDING); limit(50) }.decodeList<MyReport>()
    }

    suspend fun myModerationActions() = backend.call {
        backend.client.from("moderation_actions").select { order("created_at", Order.DESCENDING); limit(20) }.decodeList<ModerationNotice>()
    }

    suspend fun appeal(actionId: String, message: String) =
        backend.rpc<String>("submit_appeal", params("p_action" to actionId, "p_message" to message))

    suspend fun support(topic: String, message: String) =
        backend.rpc<String>("submit_support_request", params("p_topic" to topic, "p_message" to message))

    suspend fun exportMyData(): JsonObject = backend.rpc("export_my_data")

    /** Server-side deletion: the edge function soft-deletes the profile, revokes sessions and removes the auth identity. */
    suspend fun deleteAccount() {
        backend.function<JsonObject>("delete-account", params("confirm" to "DELETE"))
    }

    suspend fun registerPushToken(token: String) = backend.rpcUnit("register_push_token", params("p_token" to token))
    suspend fun removePushToken(token: String) = backend.rpcUnit("remove_push_token", params("p_token" to token))
}
