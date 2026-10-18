package et.ethiolive.app.data

class DiscoveryRepository(private val backend: Backend) {
    suspend fun rooms(tab: String, category: String? = null, language: String? = null, offset: Int = 0, limit: Int = 20) =
        backend.rpc<List<RoomCard>>(
            "discover_rooms",
            params("p_tab" to tab, "p_category" to category, "p_language" to language, "p_limit" to limit, "p_offset" to offset),
        )

    suspend fun searchRooms(query: String, category: String? = null, language: String? = null) =
        backend.rpc<List<RoomCard>>("search_rooms", params("p_query" to query, "p_category" to category, "p_language" to language, "p_limit" to 30, "p_offset" to 0))

    suspend fun searchUsers(query: String) = backend.rpc<List<UserCard>>("search_users", params("p_query" to query, "p_limit" to 30, "p_offset" to 0))
    suspend fun trendingHashtags() = backend.rpc<List<Hashtag>>("trending_hashtags", params("p_limit" to 15))
    suspend fun trendingCreators() = backend.rpc<List<UserCard>>("trending_creators", params("p_limit" to 20))
}
