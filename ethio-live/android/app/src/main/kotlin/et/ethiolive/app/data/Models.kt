package et.ethiolive.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire models for the Supabase backend. Field names follow the SQL (snake_case) via @SerialName.

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    val bio: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val language: String = "en",
    val interests: List<String> = emptyList(),
    val level: Int = 1,
    val xp: Long = 0,
    @SerialName("is_creator") val isCreator: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("private_account") val privateAccount: Boolean = false,
    @SerialName("age_band") val ageBand: String? = null,
    @SerialName("account_status") val accountStatus: String? = null,
)

@Serializable
data class UserSettings(
    @SerialName("dm_policy") val dmPolicy: String = "everyone",
    @SerialName("notify_follows") val notifyFollows: Boolean = true,
    @SerialName("notify_live_start") val notifyLiveStart: Boolean = true,
    @SerialName("notify_gifts") val notifyGifts: Boolean = true,
    @SerialName("notify_messages") val notifyMessages: Boolean = true,
    @SerialName("notify_pk") val notifyPk: Boolean = true,
    @SerialName("notify_guest_invites") val notifyGuestInvites: Boolean = true,
    @SerialName("show_in_nearby") val showInNearby: Boolean = false,
    @SerialName("show_gift_highlights") val showGiftHighlights: Boolean = true,
    @SerialName("recommendations_personalized") val recommendationsPersonalized: Boolean = true,
    @SerialName("nearby_region") val nearbyRegion: String? = null,
)

@Serializable
data class PrivateInfo(
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("kyc_status") val kycStatus: String = "none",
    @SerialName("is_adult") val isAdult: Boolean = false,
)

@Serializable
data class Account(
    val profile: Profile,
    val private: PrivateInfo = PrivateInfo(),
    val settings: UserSettings = UserSettings(),
    @SerialName("staff_role") val staffRole: String? = null,
)

@Serializable
data class HostSummary(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int = 1,
    @SerialName("is_verified") val isVerified: Boolean = false,
)

@Serializable
data class RoomCard(
    val id: String,
    val mode: String,
    val title: String,
    val category: String,
    val language: String = "en",
    val hashtags: List<String> = emptyList(),
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
    val audience: String = "public",
    @SerialName("max_seats") val maxSeats: Int = 1,
    val status: String = "live",
    val host: HostSummary,
    @SerialName("in_pk") val inPk: Boolean = false,
)

@Serializable
data class LiveRoomRow(
    val id: String,
    @SerialName("host_id") val hostId: String,
    val mode: String,
    val title: String,
    val category: String,
    @SerialName("channel_name") val channelName: String,
    val status: String,
    @SerialName("max_seats") val maxSeats: Int,
)

@Serializable
data class Participant(
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("seat_index") val seatIndex: Int? = null,
    @SerialName("mic_muted") val micMuted: Boolean = false,
    @SerialName("camera_off") val cameraOff: Boolean = false,
    @SerialName("muted_by_host") val mutedByHost: Boolean = false,
    @SerialName("rtc_uid") val rtcUid: Int = 0,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int = 1,
)

@Serializable
data class RoomMessage(
    val id: Long,
    val kind: String = "chat",
    val body: String,
    val meta: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("sender_id") val senderId: String? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val level: Int? = null,
    @SerialName("room_id") val roomId: String? = null,
    @SerialName("removed_at") val removedAt: String? = null,
)

@Serializable
data class SeatRequest(
    val id: String,
    @SerialName("user_id") val userId: String,
    val kind: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class IdOnly(val id: String)

@Serializable
data class PkMatch(
    val id: String,
    @SerialName("room_a") val roomA: String,
    @SerialName("room_b") val roomB: String,
    @SerialName("host_a") val hostA: String,
    @SerialName("host_b") val hostB: String,
    val status: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("score_a") val scoreA: Long = 0,
    @SerialName("score_b") val scoreB: Long = 0,
    val winner: String? = null,
    @SerialName("server_now") val serverNow: String? = null,
)

@Serializable
data class RoomSnapshot(
    val room: RoomCard,
    @SerialName("host_followed") val hostFollowed: Boolean = false,
    val participants: List<Participant> = emptyList(),
    val messages: List<RoomMessage> = emptyList(),
    @SerialName("pending_requests") val pendingRequests: List<SeatRequest> = emptyList(),
    @SerialName("my_invite") val myInvite: IdOnly? = null,
    val pk: PkMatch? = null,
)

@Serializable
data class JoinResult(
    val role: String,
    @SerialName("seat_index") val seatIndex: Int? = null,
    @SerialName("channel_name") val channelName: String,
)

@Serializable
data class HeartbeatResult(val status: String, @SerialName("viewer_count") val viewerCount: Int)

@Serializable
data class StreamToken(
    @SerialName("app_id") val appId: String,
    val token: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("rtc_uid") val rtcUid: Int,
    val publisher: Boolean,
    val mode: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class PkRelayToken(
    @SerialName("rtc_uid") val rtcUid: Int,
    @SerialName("source_channel") val sourceChannel: String,
    @SerialName("source_token") val sourceToken: String,
    @SerialName("dest_channel") val destChannel: String,
    @SerialName("dest_token") val destToken: String,
)

@Serializable
data class Viewer(
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int = 1,
    val role: String = "audience",
)

@Serializable
data class UserCard(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int = 1,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("is_creator") val isCreator: Boolean = false,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("is_live") val isLive: Boolean = false,
    val score: Long? = null,
)

@Serializable
data class Hashtag(val tag: String, @SerialName("live_rooms") val liveRooms: Long)

@Serializable
data class Badge(val id: String, val name: String, @SerialName("icon_key") val iconKey: String)

@Serializable
data class GiftCount(@SerialName("gift_id") val giftId: String, val count: Long)

@Serializable
data class PastLive(
    val id: String,
    val title: String,
    val category: String,
    val mode: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long? = null,
    @SerialName("peak_viewers") val peakViewers: Int? = null,
)

@Serializable
data class FanClubSummary(
    val id: String,
    val name: String,
    @SerialName("badge_label") val badgeLabel: String,
    @SerialName("member_count") val memberCount: Long = 0,
    @SerialName("is_member") val isMember: Boolean = false,
)

@Serializable
data class PublicProfile(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String = "",
    val language: String = "en",
    val interests: List<String> = emptyList(),
    val level: Int = 1,
    val xp: Long = 0,
    @SerialName("is_creator") val isCreator: Boolean = false,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("private_account") val privateAccount: Boolean = false,
    @SerialName("is_following") val isFollowing: Boolean = false,
    @SerialName("follows_you") val followsYou: Boolean = false,
    @SerialName("is_blocked") val isBlocked: Boolean = false,
    val badges: List<Badge> = emptyList(),
    @SerialName("live_room") val liveRoom: RoomCard? = null,
    @SerialName("gift_highlights") val giftHighlights: List<GiftCount> = emptyList(),
    @SerialName("recent_lives") val recentLives: List<PastLive> = emptyList(),
    @SerialName("fan_club") val fanClub: FanClubSummary? = null,
)

@Serializable
data class FollowRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int = 1,
)

@Serializable
data class BlockedRow(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class Gift(
    val id: String,
    val name: String,
    @SerialName("name_am") val nameAm: String,
    val category: String,
    @SerialName("price_coins") val priceCoins: Int,
    @SerialName("artwork_key") val artworkKey: String,
    @SerialName("animation_key") val animationKey: String,
    @SerialName("is_animated_fullscreen") val fullscreen: Boolean = false,
    val available: Boolean = true,
)

@Serializable
data class GiftResult(
    @SerialName("transaction_id") val transactionId: String? = null,
    val duplicate: Boolean = false,
    @SerialName("coins_spent") val coinsSpent: Long = 0,
    @SerialName("promo_coins_spent") val promoCoinsSpent: Long = 0,
    @SerialName("beans_earned") val beansEarned: Long = 0,
    @SerialName("pk_points") val pkPoints: Long = 0,
    val coins: Long = 0,
    @SerialName("promo_coins") val promoCoins: Long = 0,
)

@Serializable
data class NextRelease(val beans: Long? = null, val at: String? = null)

@Serializable
data class Wallet(
    val coins: Long = 0,
    @SerialName("promo_coins") val promoCoins: Long = 0,
    @SerialName("beans_pending") val beansPending: Long = 0,
    val beans: Long = 0,
    @SerialName("beans_in_payout") val beansInPayout: Long = 0,
    @SerialName("is_adult") val isAdult: Boolean = false,
    @SerialName("kyc_status") val kycStatus: String = "none",
    @SerialName("next_release") val nextRelease: NextRelease? = null,
)

@Serializable
data class LedgerEntry(
    @SerialName("entry_id") val entryId: Long,
    @SerialName("transaction_id") val transactionId: String,
    val kind: String,
    val account: String,
    val amount: Long,
    val unit: String,
    @SerialName("balance_after") val balanceAfter: Long,
    val reference: JsonObject = JsonObject(emptyMap()),
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CoinPackage(
    val id: String,
    val coins: Int,
    @SerialName("bonus_promo_coins") val bonusPromoCoins: Int = 0,
    @SerialName("price_minor") val priceMinor: Long,
    val currency: String = "ETB",
    @SerialName("play_product_id") val playProductId: String? = null,
)

@Serializable
data class PaymentOrder(
    val id: String,
    @SerialName("package_id") val packageId: String,
    val coins: Int,
    @SerialName("amount_minor") val amountMinor: Long,
    val currency: String,
    val status: String,
)

@Serializable
data class ChapaCheckout(@SerialName("order_id") val orderId: String, @SerialName("checkout_url") val checkoutUrl: String)

@Serializable
data class PayoutTerms(
    @SerialName("min_beans") val minBeans: Long,
    @SerialName("etb_minor_per_100_beans") val etbMinorPer100Beans: Long,
    @SerialName("fee_bps") val feeBps: Long,
    @SerialName("hold_days") val holdDays: Int,
    @SerialName("creator_share_bps") val creatorShareBps: Long,
    @SerialName("promo_share_bps") val promoShareBps: Long = 0,
    @SerialName("processing_days") val processingDays: String = "",
)

@Serializable
data class PayoutRequest(
    val id: String,
    val beans: Long,
    @SerialName("net_etb_minor") val netEtbMinor: Long,
    @SerialName("fee_etb_minor") val feeEtbMinor: Long,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
)

@Serializable
data class Sessions(
    val count: Long = 0,
    @SerialName("total_seconds") val totalSeconds: Long = 0,
    @SerialName("peak_viewers") val peakViewers: Int = 0,
    @SerialName("avg_peak") val avgPeak: Int = 0,
)

@Serializable
data class DayValue(val day: String, val beans: Long? = null, val coins: Long? = null, @SerialName("new_followers") val newFollowers: Long? = null)

@Serializable
data class GiftBreakdown(@SerialName("gift_id") val giftId: String, val count: Long, val coins: Long)

@Serializable
data class Supporter(val id: String, val username: String, @SerialName("display_name") val displayName: String,
                     @SerialName("avatar_url") val avatarUrl: String? = null, val coins: Long)

@Serializable
data class SessionRow(val title: String, @SerialName("started_at") val startedAt: String,
                      @SerialName("duration_seconds") val durationSeconds: Long? = null,
                      @SerialName("peak_viewers") val peakViewers: Int = 0, @SerialName("gift_coins") val giftCoins: Long = 0,
                      @SerialName("new_followers") val newFollowers: Int = 0)

@Serializable
data class CreatorDashboard(
    val balances: Wallet,
    val terms: PayoutTerms,
    @SerialName("kyc_status") val kycStatus: String = "none",
    @SerialName("is_adult") val isAdult: Boolean = false,
    @SerialName("total_gift_coins") val totalGiftCoins: Long = 0,
    @SerialName("beans_paid_out") val beansPaidOut: Long = 0,
    val sessions: Sessions = Sessions(),
    @SerialName("recent_sessions") val recentSessions: List<SessionRow> = emptyList(),
    @SerialName("earnings_by_day") val earningsByDay: List<DayValue> = emptyList(),
    @SerialName("follower_growth") val followerGrowth: List<DayValue> = emptyList(),
    @SerialName("follower_count") val followerCount: Int = 0,
    @SerialName("gift_breakdown") val giftBreakdown: List<GiftBreakdown> = emptyList(),
    @SerialName("top_supporters") val topSupporters: List<Supporter> = emptyList(),
    @SerialName("open_payout") val openPayout: PayoutRequest? = null,
)

@Serializable
data class Conversation(
    val id: String,
    val status: String,
    @SerialName("is_request_to_me") val isRequestToMe: Boolean = false,
    @SerialName("other_id") val otherId: String,
    @SerialName("other_username") val otherUsername: String,
    @SerialName("other_display_name") val otherDisplayName: String,
    @SerialName("other_avatar_url") val otherAvatarUrl: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_preview") val lastMessagePreview: String? = null,
    @SerialName("unread_count") val unreadCount: Long = 0,
    @SerialName("other_last_read_id") val otherLastReadId: Long = 0,
)

@Serializable
data class DirectMessage(
    val id: Long,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class AppNotification(
    val id: Long,
    val type: String,
    @SerialName("actor_id") val actorId: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RankingRow(
    val rank: Long,
    @SerialName("user_id") val userId: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val level: Int? = null,
    val score: Long,
)

@Serializable
data class LiveEvent(
    val id: String,
    val title: String,
    val description: String,
    val rules: String,
    @SerialName("reward_description") val rewardDescription: String,
    val metric: String,
    @SerialName("banner_key") val bannerKey: String = "event_default",
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
)

@Serializable
data class FanClub(
    val id: String,
    @SerialName("creator_id") val creatorId: String,
    val name: String,
    @SerialName("badge_label") val badgeLabel: String,
    val announcement: String = "",
    val benefits: String = "",
    @SerialName("join_cost_coins") val joinCostCoins: Int = 0,
)

@Serializable
data class FanMember(val id: String, val username: String, @SerialName("display_name") val displayName: String,
                     @SerialName("avatar_url") val avatarUrl: String? = null,
                     @SerialName("contribution_coins") val contributionCoins: Long, @SerialName("loyalty_level") val loyaltyLevel: Int)

@Serializable
data class FanMembership(@SerialName("contribution_coins") val contributionCoins: Long, @SerialName("loyalty_level") val loyaltyLevel: Int)

@Serializable
data class FanClubDetail(
    val club: FanClub,
    val creator: HostSummary,
    @SerialName("member_count") val memberCount: Long = 0,
    val me: FanMembership? = null,
    @SerialName("top_members") val topMembers: List<FanMember> = emptyList(),
)

@Serializable
data class ModerationNotice(val id: String, val kind: String, val reason: String, @SerialName("created_at") val createdAt: String,
                            @SerialName("expires_at") val expiresAt: String? = null)

@Serializable
data class MyReport(val id: String, @SerialName("target_type") val targetType: String, val reason: String,
                    val status: String, @SerialName("created_at") val createdAt: String)
