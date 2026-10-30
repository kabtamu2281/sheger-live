package et.ethiolive.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import et.ethiolive.app.BuildConfig
import et.ethiolive.app.R
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.AppException
import et.ethiolive.core.Money
import et.ethiolive.core.ServerError

@Composable
fun Avatar(url: String?, name: String, size: Dp = 40.dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .let { if (ring != null) it.border(2.dp, ring, CircleShape) else it }
    val fallback: @Composable () -> Unit = {
        Box(base.background(Brand.heroGradient), contentAlignment = Alignment.Center) {
            val initial = name.trim().firstOrNull()?.uppercase()
            if (initial != null) Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
            else Icon(Icons.Default.Person, null, tint = Color.White)
        }
    }
    if (url.isNullOrBlank()) fallback() else SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = base,
        loading = { fallback() },
        error = { fallback() },
    )
}

@Composable
fun LevelBadge(level: Int, modifier: Modifier = Modifier) {
    val color = when {
        level >= 40 -> Brand.Gold
        level >= 20 -> Brand.Coral
        level >= 10 -> Brand.Violet
        else -> Brand.Emerald
    }
    Text(
        "Lv$level",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

@Composable
fun VerifiedMark(show: Boolean) {
    if (show) Icon(Icons.Default.Verified, stringResource(R.string.verified), tint = Brand.Sky, modifier = Modifier.size(14.dp))
}

@Composable
fun LiveBadge(modifier: Modifier = Modifier, label: String = stringResource(R.string.live_badge)) {
    Text(
        label,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(Brand.Live).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ViewerPill(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Brand.Scrim).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Visibility, stringResource(R.string.viewers), tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(Money.compact(count.toLong()), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(error: Throwable?, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(errorMessage(error), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
fun EmptyState(@StringRes title: Int, @StringRes body: Int? = null, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = { actions() },
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun UserRow(
    avatarUrl: String?,
    displayName: String,
    username: String,
    level: Int? = null,
    verified: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatarUrl, displayName, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                VerifiedMark(verified)
                if (level != null) {
                    Spacer(Modifier.width(6.dp))
                    LevelBadge(level)
                }
            }
            Text("@$username", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** Notice for features whose server-side credentials are not configured in this build. Never pretends to work. */
@Composable
fun NotConfiguredNotice(@StringRes message: Int, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Text(stringResource(message), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@StringRes
fun errorRes(error: ServerError): Int = when (error) {
    ServerError.NOT_AUTHENTICATED -> R.string.err_not_authenticated
    ServerError.EMAIL_ALREADY_REGISTERED, ServerError.ALREADY_REGISTERED -> R.string.err_email_already_registered
    ServerError.EMAIL_INVALID -> R.string.err_email_invalid
    ServerError.EMAIL_NOT_AUTHORIZED -> R.string.err_email_not_authorized
    ServerError.EMAIL_NOT_CONFIRMED -> R.string.err_email_not_confirmed
    ServerError.INVALID_CREDENTIALS -> R.string.err_invalid_credentials
    ServerError.WEAK_PASSWORD -> R.string.err_weak_password
    ServerError.SIGNUP_DISABLED -> R.string.err_signup_disabled
    ServerError.TERMS_REQUIRED -> R.string.err_terms_required
    ServerError.PROFILE_REQUIRED -> R.string.err_profile_required
    ServerError.ACCOUNT_SUSPENDED -> R.string.err_account_suspended
    ServerError.ACCOUNT_BANNED -> R.string.err_account_banned
    ServerError.ACCOUNT_DELETED -> R.string.err_account_deleted
    ServerError.AGE_INELIGIBLE -> R.string.err_age_ineligible
    ServerError.INVALID_BIRTH_DATE -> R.string.err_invalid_birth_date
    ServerError.USERNAME_INVALID -> R.string.err_username_invalid
    ServerError.USERNAME_TAKEN -> R.string.err_username_taken
    ServerError.USERNAME_RESERVED, ServerError.USERNAME_IMPERSONATION -> R.string.err_username_reserved
    ServerError.USERNAME_CHANGE_TOO_SOON -> R.string.err_username_change_too_soon
    ServerError.RATE_LIMITED -> R.string.err_rate_limited
    ServerError.BLOCKED -> R.string.err_blocked
    ServerError.USER_NOT_FOUND -> R.string.err_user_not_found
    ServerError.ROOM_NOT_FOUND -> R.string.err_room_not_found
    ServerError.ROOM_NOT_LIVE -> R.string.err_room_not_live
    ServerError.ALREADY_LIVE -> R.string.err_already_live
    ServerError.FOLLOWERS_ONLY -> R.string.err_followers_only
    ServerError.REMOVED_FROM_ROOM -> R.string.err_removed_from_room
    ServerError.NOT_IN_ROOM -> R.string.err_not_in_room
    ServerError.MUTED_IN_ROOM -> R.string.err_muted_in_room
    ServerError.MESSAGE_TOO_LONG -> R.string.err_message_too_long
    ServerError.BROADCAST_AGE_RESTRICTED -> R.string.err_broadcast_age_restricted
    ServerError.BROADCAST_BANNED -> R.string.err_broadcast_banned
    ServerError.NO_FREE_SEAT -> R.string.err_no_free_seat
    ServerError.REQUEST_ALREADY_PENDING -> R.string.err_request_already_pending
    ServerError.INSUFFICIENT_BALANCE -> R.string.err_insufficient_balance
    ServerError.GIFT_UNAVAILABLE -> R.string.err_gift_unavailable
    ServerError.FREE_GIFT_LIMIT -> R.string.err_free_gift_limit
    ServerError.PAID_GIFTS_AGE_RESTRICTED -> R.string.err_paid_gifts_age_restricted
    ServerError.PURCHASE_AGE_RESTRICTED -> R.string.err_purchase_age_restricted
    ServerError.RECIPIENT_NOT_ON_STAGE -> R.string.err_recipient_not_on_stage
    ServerError.DUPLICATE_IN_PROGRESS -> R.string.err_duplicate_in_progress
    ServerError.DM_NOT_ALLOWED -> R.string.err_dm_not_allowed
    ServerError.AWAITING_ACCEPTANCE -> R.string.err_awaiting_acceptance
    ServerError.PK_ALREADY_ACTIVE -> R.string.err_pk_already_active
    ServerError.PK_INVITE_EXPIRED -> R.string.err_pk_invite_expired
    ServerError.PK_REQUIRES_VIDEO -> R.string.err_pk_requires_video
    ServerError.KYC_REQUIRED -> R.string.err_kyc_required
    ServerError.PAYOUT_BELOW_MINIMUM -> R.string.err_payout_below_minimum
    ServerError.PAYOUT_ALREADY_OPEN -> R.string.err_payout_already_open
    ServerError.SERVICE_NOT_CONFIGURED -> R.string.err_service_not_configured
    ServerError.STREAMING_UNAVAILABLE -> R.string.err_streaming_unavailable
    ServerError.PAYMENT_PROVIDER_UNAVAILABLE -> R.string.err_payment_provider_unavailable
    ServerError.NETWORK -> R.string.err_network
    ServerError.UNKNOWN -> R.string.err_unknown
}

@Composable
fun errorMessage(error: Throwable?): String {
    val code = (error as? AppException)?.error ?: if (error == null) ServerError.UNKNOWN else ServerError.parse(error.message)
    val text = stringResource(errorRes(code))
    // Debug (test) builds also show what actually failed, so testers can report it; release builds never do.
    return if (BuildConfig.DEBUG && error != null && (code == ServerError.UNKNOWN || code == ServerError.NETWORK)) {
        "$text\n(${errorDetail(error)})"
    } else text
}

private fun errorDetail(error: Throwable): String {
    val cause = if (error is AppException) error.cause ?: error else error
    return "${cause.javaClass.simpleName}: ${cause.message.orEmpty()}".take(200)
}
