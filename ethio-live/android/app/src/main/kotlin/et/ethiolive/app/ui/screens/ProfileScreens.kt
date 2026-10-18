package et.ethiolive.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.PublicProfile
import et.ethiolive.app.ui.components.Avatar
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.LevelBadge
import et.ethiolive.app.ui.components.LiveBadge
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.VerifiedMark
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.loadScaledJpeg
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Money
import et.ethiolive.core.TimeFormat
import et.ethiolive.core.Validation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun Stat(value: Long, label: String, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(8.dp)) {
        Text(Money.compact(value), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileHeader(
    avatarUrl: String?, displayName: String, username: String, level: Int, verified: Boolean, bio: String,
    followers: Int, following: Int, onFollowers: () -> Unit, onFollowing: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(avatarUrl, displayName, 96.dp, ring = Brand.Gold)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(4.dp))
            VerifiedMark(verified)
            Spacer(Modifier.width(6.dp))
            LevelBadge(level)
        }
        Text("@$username", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (bio.isNotBlank()) Text(bio, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat(followers.toLong(), stringResource(R.string.followers), onFollowers)
            Stat(following.toLong(), stringResource(R.string.following), onFollowing)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, trailing: String? = null) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, null, tint = Brand.Gold) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun MeScreen(container: AppContainer, nav: Nav) {
    val account by container.account.collectAsState()
    val wallet = rememberLoad("me-wallet") { container.wallet.wallet() }
    val walletState by wallet.state.collectAsState()
    val coins = (walletState as? et.ethiolive.app.ui.components.Load.Ready)?.value?.coins
    val p = account?.profile ?: return
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { nav.to(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
        }
        ProfileHeader(p.avatarUrl, p.displayName, p.username, p.level, p.isVerified, p.bio, p.followerCount, p.followingCount,
            onFollowers = { nav.to(Routes.follows(p.id, true)) }, onFollowing = { nav.to(Routes.follows(p.id, false)) })
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(stringResource(R.string.edit_profile), onClick = { nav.to(Routes.EDIT_PROFILE) }, modifier = Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.view_public_profile), onClick = { nav.profile(p.id) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MenuRow(Icons.Default.AccountBalanceWallet, stringResource(R.string.wallet), { nav.to(Routes.WALLET) }, coins?.let { Money.grouped(it) })
        MenuRow(Icons.Default.BarChart, stringResource(R.string.creator_dashboard), { nav.to(Routes.CREATOR) })
        MenuRow(Icons.Default.Favorite, stringResource(R.string.my_fan_club), { nav.to(Routes.FAN_CLUB_EDIT) })
        MenuRow(Icons.Default.EmojiEvents, stringResource(R.string.rankings), { nav.to(Routes.RANKINGS) })
        MenuRow(Icons.Default.Celebration, stringResource(R.string.events), { nav.to(Routes.EVENTS) })
        MenuRow(Icons.Default.Shield, stringResource(R.string.safety_center), { nav.to(Routes.SAFETY) })
        MenuRow(Icons.Default.Settings, stringResource(R.string.settings), { nav.to(Routes.SETTINGS) })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProfileScreen(container: AppContainer, nav: Nav, userId: String) {
    val vm = rememberLoad("profile-$userId") { container.profiles.publicProfile(userId) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<Throwable?>(null) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val me = container.backend.userId
    val am = currentLanguage() == et.ethiolive.core.AppLanguage.AMHARIC
    val giftsVm = rememberLoad("gift-catalog") { container.wallet.gifts() }
    val giftsState by giftsVm.state.collectAsState()
    val gifts = (giftsState as? et.ethiolive.app.ui.components.Load.Ready)?.value.orEmpty().associateBy { it.id }

    fun act(block: suspend () -> Unit) = scope.launch {
        error = null
        try { block(); vm.reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("", nav::back) {
            if (userId != me) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.report_user)) }, onClick = { menu = false; reporting = true })
                    val blocked = (vm.state.value as? et.ethiolive.app.ui.components.Load.Ready)?.value?.isBlocked == true
                    DropdownMenuItem(text = { Text(stringResource(if (blocked) R.string.unblock else R.string.block)) }, onClick = {
                        menu = false
                        act { if (blocked) container.profiles.unblock(userId) else container.profiles.block(userId) }
                    })
                }
            }
        }
        LoadContent(vm) { p: PublicProfile ->
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    ProfileHeader(p.avatarUrl, p.displayName, p.username, p.level, p.isVerified, p.bio, p.followerCount, p.followingCount,
                        onFollowers = { nav.to(Routes.follows(p.id, true)) }, onFollowing = { nav.to(Routes.follows(p.id, false)) })
                }
                if (p.id != me && !p.isBlocked) item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton(
                            stringResource(if (p.isFollowing) R.string.unfollow else if (p.followsYou) R.string.follow_back else R.string.follow),
                            onClick = { act { if (p.isFollowing) container.profiles.unfollow(p.id) else container.profiles.follow(p.id) } },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(stringResource(R.string.message), onClick = {
                            scope.launch {
                                error = null
                                runCatching { container.messages.open(p.id) }.onSuccess { nav.conversation(it) }.onFailure { error = it }
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                }
                if (p.isBlocked) item { Text(stringResource(R.string.you_blocked_user), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                error?.let { e -> item { Text(errorMessage(e), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                p.liveRoom?.let { room ->
                    item {
                        Surface(onClick = { nav.room(room.id) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                LiveBadge()
                                Spacer(Modifier.width(10.dp))
                                Text(room.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.watch), color = Brand.Gold)
                            }
                        }
                    }
                }
                if (p.badges.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.badges)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(p.badges) { b -> Chip(b.name, false, {}) }
                        }
                    }
                }
                p.fanClub?.let { club ->
                    item { SectionHeader(stringResource(R.string.fan_club)) }
                    item {
                        ListItem(
                            headlineContent = { Text(club.name) },
                            supportingContent = { Text(stringResource(R.string.fan_club_members, club.memberCount)) },
                            leadingContent = { Icon(Icons.Default.Favorite, null, tint = Brand.Coral) },
                            trailingContent = { Text(stringResource(if (club.isMember) R.string.member else R.string.join), color = Brand.Gold) },
                            modifier = Modifier.clickable { nav.to(Routes.fanClub(club.id)) },
                        )
                    }
                }
                if (p.giftHighlights.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.gifts_received)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(p.giftHighlights) { g ->
                                val gift = gifts[g.giftId]
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GiftArt(gift?.artworkKey ?: g.giftId, 44.dp)
                                    Text("x${Money.compact(g.count)}", style = MaterialTheme.typography.labelSmall)
                                    if (gift != null) Text(if (am) gift.nameAm else gift.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (p.recentLives.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.past_lives)) }
                    items(p.recentLives, key = { it.id }) { l ->
                        ListItem(
                            headlineContent = { Text(l.title) },
                            supportingContent = {
                                Text(listOfNotNull(relativeTime(l.startedAt), l.durationSeconds?.let { TimeFormat.duration(it) },
                                    l.peakViewers?.let { stringResource(R.string.peak_viewers, it) }).joinToString(" · "))
                            },
                        )
                    }
                }
                if (p.privateAccount && !p.isFollowing && p.id != me) item { EmptyState(R.string.private_account, R.string.private_account_body) }
            }
        }
    }
    if (reporting) ReportSheet(onSubmit = { reason, details ->
        reporting = false
        act { container.profiles.report("user", userId, reason, details) }
    }, onDismiss = { reporting = false })
}

@Composable
fun FollowsScreen(container: AppContainer, nav: Nav, userId: String, followers: Boolean) {
    val vm = rememberLoad("follows-$userId-$followers") { container.profiles.follows(userId, followers) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(if (followers) R.string.followers else R.string.following), nav::back)
        LoadContent(vm) { list ->
            if (list.isEmpty()) EmptyState(R.string.list_empty)
            else LazyColumn {
                items(list, key = { it.id }) { u -> UserRow(u.avatarUrl, u.displayName, u.username, u.level, onClick = { nav.profile(u.id) }) }
            }
        }
    }
}

val INTERESTS = CATEGORIES

@Composable
fun EditProfileScreen(container: AppContainer, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by container.account.collectAsState()
    val p = account?.profile ?: return
    var displayName by remember { mutableStateOf(p.displayName) }
    var bio by remember { mutableStateOf(p.bio) }
    var username by remember { mutableStateOf(p.username) }
    var interests by remember { mutableStateOf(p.interests.toSet()) }
    var privateAccount by remember { mutableStateOf(p.privateAccount) }
    var avatarUrl by remember { mutableStateOf(p.avatarUrl) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null
            try {
                val bytes = loadScaledJpeg(context, uri, 512) ?: return@launch
                avatarUrl = container.profiles.uploadAvatar(bytes)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(stringResource(R.string.edit_profile), nav::back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(avatarUrl, displayName, 96.dp)
                Icon(Icons.Default.Edit, stringResource(R.string.change_photo), tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Brand.Violet, RoundedCornerShape(50)).padding(6.dp))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(displayName, { displayName = it.take(40) }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(username, { username = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' }.take(24) },
                label = { Text(stringResource(R.string.username)) }, prefix = { Text("@") }, singleLine = true,
                supportingText = { Text(stringResource(R.string.username_change_rule)) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(bio, { bio = it.take(Validation.BIO_MAX) }, label = { Text(stringResource(R.string.bio)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.interests), style = MaterialTheme.typography.labelLarge)
            LazyRow(contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(INTERESTS) { i -> Chip(categoryLabel(i), i in interests, { interests = if (i in interests) interests - i else interests + i }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.private_account))
                    Text(stringResource(R.string.private_account_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(privateAccount, { privateAccount = it })
            }
            Spacer(Modifier.height(16.dp))
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
            PrimaryButton(stringResource(R.string.save), loading = busy, enabled = displayName.isNotBlank(), onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        if (username != p.username) container.profiles.changeUsername(username)
                        container.profiles.updateProfile(displayName.trim(), bio.trim(), avatarUrl, p.language, interests.toList(), privateAccount)
                        container.refreshAccount()
                        nav.back()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                    busy = false
                }
            })
        }
    }
}
