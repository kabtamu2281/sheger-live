package et.ethiolive.app.data

/** PK battles. Scores and winners are computed in Postgres from gift transactions; the client only displays them. */
class PkRepository(private val backend: Backend) {
    suspend fun candidates(myRoom: String) = backend.rpc<List<RoomCard>>("pk_candidates", params("p_my_room" to myRoom, "p_limit" to 20))
    suspend fun invite(myRoom: String, targetRoom: String, durationSeconds: Int) =
        backend.rpc<String>("pk_invite", params("p_my_room" to myRoom, "p_target_room" to targetRoom, "p_duration_seconds" to durationSeconds))
    suspend fun respond(matchId: String, accept: Boolean) = backend.rpc<PkMatch>("pk_respond", params("p_match" to matchId, "p_accept" to accept))
    suspend fun finish(matchId: String) = backend.rpc<PkMatch>("pk_finish", params("p_match" to matchId))
    suspend fun cancel(matchId: String) = backend.rpc<PkMatch>("pk_cancel", params("p_match" to matchId))
    suspend fun close(matchId: String) = backend.rpc<PkMatch>("pk_close", params("p_match" to matchId, "p_force" to true))
}
