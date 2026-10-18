package et.ethiolive.app.data

import io.github.jan.supabase.postgrest.from
import java.util.UUID

class CommunityRepository(private val backend: Backend) {
    suspend fun rankings(board: String, period: String) =
        backend.rpc<List<RankingRow>>("get_rankings", params("p_board" to board, "p_period" to period, "p_limit" to 50))

    suspend fun events() = backend.rpc<List<LiveEvent>>("list_events")
    suspend fun joinEvent(eventId: String) = backend.rpcUnit("join_event", params("p_event" to eventId))
    suspend fun eventLeaderboard(eventId: String) = backend.rpc<List<RankingRow>>("event_leaderboard", params("p_event" to eventId, "p_limit" to 50))

    suspend fun fanClubDetail(clubId: String) = backend.rpc<FanClubDetail>("fan_club_detail", params("p_club" to clubId))
    suspend fun joinFanClub(clubId: String, key: String = UUID.randomUUID().toString()) =
        backend.rpcRaw("join_fan_club", params("p_club" to clubId, "p_idempotency_key" to key))
    suspend fun leaveFanClub(clubId: String) = backend.rpcUnit("leave_fan_club", params("p_club" to clubId))
    suspend fun upsertFanClub(name: String, badge: String, benefits: String, announcement: String, joinCost: Int) =
        backend.rpc<FanClub>("upsert_fan_club", params("p_name" to name, "p_badge_label" to badge, "p_benefits" to benefits, "p_announcement" to announcement, "p_join_cost" to joinCost))

    suspend fun myFanClub(): FanClub? = backend.call {
        val uid = backend.requireUserId()
        backend.client.from("fan_clubs").select { filter { eq("creator_id", uid) } }.decodeSingleOrNull<FanClub>()
    }
}
