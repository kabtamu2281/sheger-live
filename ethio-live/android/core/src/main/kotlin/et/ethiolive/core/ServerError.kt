package et.ethiolive.core

/**
 * Machine codes raised by the backend's SQL functions and edge functions. The app maps them to localized
 * messages; anything unrecognised becomes [UNKNOWN] so raw server text is never shown to users.
 */
enum class ServerError(val code: String) {
    NOT_AUTHENTICATED("not_authenticated"),
    EMAIL_ALREADY_REGISTERED("user_already_exists"),
    EMAIL_INVALID("email_address_invalid"),
    EMAIL_NOT_AUTHORIZED("email_address_not_authorized"),
    EMAIL_NOT_CONFIRMED("email_not_confirmed"),
    INVALID_CREDENTIALS("invalid_credentials"),
    WEAK_PASSWORD("weak_password"),
    SIGNUP_DISABLED("signup_disabled"),
    ALREADY_REGISTERED("already_registered"),
    TERMS_REQUIRED("terms_required"),
    PROFILE_REQUIRED("profile_required"),
    ACCOUNT_SUSPENDED("account_suspended"),
    ACCOUNT_BANNED("account_banned"),
    ACCOUNT_DELETED("account_deleted"),
    AGE_INELIGIBLE("age_ineligible"),
    INVALID_BIRTH_DATE("invalid_birth_date"),
    USERNAME_INVALID("username_invalid"),
    USERNAME_TAKEN("username_taken"),
    USERNAME_RESERVED("username_reserved"),
    USERNAME_IMPERSONATION("username_impersonation"),
    USERNAME_CHANGE_TOO_SOON("username_change_too_soon"),
    RATE_LIMITED("rate_limited"),
    BLOCKED("blocked"),
    USER_NOT_FOUND("user_not_found"),
    ROOM_NOT_FOUND("room_not_found"),
    ROOM_NOT_LIVE("room_not_live"),
    ALREADY_LIVE("already_live"),
    FOLLOWERS_ONLY("followers_only"),
    REMOVED_FROM_ROOM("removed_from_room"),
    NOT_IN_ROOM("not_in_room"),
    MUTED_IN_ROOM("muted_in_room"),
    MESSAGE_TOO_LONG("message_too_long"),
    BROADCAST_AGE_RESTRICTED("broadcast_age_restricted"),
    BROADCAST_BANNED("broadcast_banned"),
    NO_FREE_SEAT("no_free_seat"),
    REQUEST_ALREADY_PENDING("request_already_pending"),
    INSUFFICIENT_BALANCE("insufficient_balance"),
    GIFT_UNAVAILABLE("gift_unavailable"),
    FREE_GIFT_LIMIT("free_gift_limit"),
    PAID_GIFTS_AGE_RESTRICTED("paid_gifts_age_restricted"),
    PURCHASE_AGE_RESTRICTED("purchase_age_restricted"),
    RECIPIENT_NOT_ON_STAGE("recipient_not_on_stage"),
    DUPLICATE_IN_PROGRESS("duplicate_in_progress"),
    DM_NOT_ALLOWED("dm_not_allowed"),
    AWAITING_ACCEPTANCE("awaiting_acceptance"),
    PK_ALREADY_ACTIVE("pk_already_active"),
    PK_INVITE_EXPIRED("pk_invite_expired"),
    PK_REQUIRES_VIDEO("pk_requires_video"),
    KYC_REQUIRED("kyc_required"),
    PAYOUT_BELOW_MINIMUM("payout_below_minimum"),
    PAYOUT_ALREADY_OPEN("payout_already_open"),
    SERVICE_NOT_CONFIGURED("service_not_configured"),
    STREAMING_UNAVAILABLE("streaming_unavailable"),
    PAYMENT_PROVIDER_UNAVAILABLE("payment_provider_unavailable"),
    NETWORK("network"),
    UNKNOWN("unknown");

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** Other names Supabase Auth uses for the same situations. */
        private val aliases = mapOf(
            "email_exists" to EMAIL_ALREADY_REGISTERED,
            "over_email_send_rate_limit" to RATE_LIMITED,
            "over_request_rate_limit" to RATE_LIMITED,
            "over_sms_send_rate_limit" to RATE_LIMITED,
            "email_provider_disabled" to SIGNUP_DISABLED,
        )
        private val token = Regex("[a-z][a-z0-9_]{2,60}")

        /** Finds the first known code in an error message such as `"P0001: insufficient_balance"`. */
        fun parse(message: String?): ServerError {
            if (message.isNullOrBlank()) return UNKNOWN
            return token.findAll(message).firstNotNullOfOrNull { byCode[it.value] ?: aliases[it.value] } ?: UNKNOWN
        }
    }
}

/** Exception carrying a parsed server code; thrown by repositories so the UI can localize it. */
class AppException(val error: ServerError, cause: Throwable? = null) : Exception(error.code, cause)
