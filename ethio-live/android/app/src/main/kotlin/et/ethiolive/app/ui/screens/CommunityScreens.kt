package et.ethiolive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.FanClubDetail
import et.ethiolive.app.data.LiveEvent
import et.ethiolive.app.data.RankingRow
import et.ethiolive.app.ui.components.BackTopBar
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.LoadContent
import et.ethiolive.app.ui.components.Loading
import et.ethiolive.app.ui.components.PrimaryButton
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import et.ethiolive.core.Money
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun RankRow(r: RankingRow, nav: Nav) {
    val color = when (r.rank) { 1L -> Brand.Gold; 2L -> Brand.Sky; 3L -> Brand.Coral; else -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
        Text("${r.rank}", color = color, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
        UserRow(r.avatarUrl, r.displayName, r.username, r.level, onClick = { nav.profile(r.userId) }) {
            Text(Money.compact(r.score), color = Brand.Gold, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RankingsScreen(container: AppContainer, nav: Nav) {
    var board by remember { mutableStateOf("creators") }
    var period by remember { mutableStateOf("weekly") }
    val vm = rememberLoad("rankings-$board-$period") { container.community.rankings(board, period) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.rankings), nav::back)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.board_creators), board == "creators", { board = "creators" })
            Chip(stringResource(R.string.board_supporters), board == "supporters", { board = "supporters" })
            Chip(stringResource(R.string.board_pk), board == "pk", { board = "pk" })
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.period_daily), period == "daily", { period = "daily" })
            Chip(stringResource(R.string.period_weekly), period == "weekly", { period = "weekly" })
            Chip(stringResource(R.string.period_monthly), period == "monthly", { period = "monthly" })
        }
        Text(stringResource(R.string.rankings_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))
        LoadContent(vm) { list ->
            if (list.isEmpty()) EmptyState(R.string.rankings_empty)
            else LazyColumn { items(list, key = { it.userId }) { RankRow(it, nav) } }
        }
    }
}

@Composable
fun EventsScreen(container: AppContainer, nav: Nav) {
    val vm = rememberLoad("events") { container.community.events() }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.events), nav::back)
        LoadContent(vm) { list ->
            if (list.isEmpty()) EmptyState(R.string.events_empty, R.string.events_empty_body)
            else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { e -> EventCard(e) { nav.to(Routes.event(e.id)) } }
            }
        }
    }
}

@Composable
private fun EventCard(e: LiveEvent, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GiftArt("bloom", 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(e.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                Text(stringResource(R.string.event_window, e.startsAt.take(10), e.endsAt.take(10)), style = MaterialTheme.typography.labelSmall, color = Brand.Gold)
            }
        }
    }
}

@Composable
fun EventScreen(container: AppContainer, nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val events = rememberLoad("events") { container.community.events() }
    val board = rememberLoad("event-board-$id") { container.community.eventLeaderboard(id) }
    var joined by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.event), nav::back)
        LoadContent(events) { list ->
            val e = list.firstOrNull { it.id == id }
            if (e == null) EmptyState(R.string.event_not_found)
            else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { EventCard(e) {} }
                item {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.event_rules), style = MaterialTheme.typography.titleMedium)
                        Text(e.rules)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.event_reward), style = MaterialTheme.typography.titleMedium)
                        Text(e.rewardDescription)
                        Spacer(Modifier.height(12.dp))
                        error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
                        PrimaryButton(stringResource(if (joined) R.string.event_joined else R.string.event_join), enabled = !joined, onClick = {
                            scope.launch {
                                runCatching { container.community.joinEvent(id) }.onSuccess { joined = true; board.reload() }.onFailure { error = it }
                            }
                        })
                    }
                }
                item { SectionHeader(stringResource(R.string.leaderboard)) }
                item {
                    LoadContent(board, Modifier.height(200.dp)) { rows ->
                        Column {
                            if (rows.isEmpty()) EmptyState(R.string.rankings_empty)
                            rows.forEach { RankRow(it, nav) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FanClubScreen(container: AppContainer, nav: Nav, id: String) {
    val scope = rememberCoroutineScope()
    val vm = rememberLoad("fanclub-$id") { container.community.fanClubDetail(id) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BackTopBar(stringResource(R.string.fan_club), nav::back)
        LoadContent(vm) { d: FanClubDetail ->
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    UserRow(d.creator.avatarUrl, d.creator.displayName, d.creator.username, d.creator.level, d.creator.isVerified, onClick = { nav.profile(d.creator.id) })
                    Text(d.club.name, style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.fan_club_members, d.memberCount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Chip(d.club.badgeLabel, true, {})
                }
                if (d.club.announcement.isNotBlank()) item { Text(d.club.announcement) }
                if (d.club.benefits.isNotBlank()) item {
                    Text(stringResource(R.string.fan_club_benefits), style = MaterialTheme.typography.titleMedium)
                    Text(d.club.benefits)
                }
                item {
                    error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
                    val me = d.me
                    if (d.creator.id == container.backend.userId) {
                        SecondaryButton(stringResource(R.string.edit_fan_club), onClick = { nav.to(Routes.FAN_CLUB_EDIT) })
                    } else if (me == null) {
                        PrimaryButton(
                            if (d.club.joinCostCoins == 0) stringResource(R.string.join_free) else stringResource(R.string.join_for_coins, d.club.joinCostCoins),
                            loading = busy,
                            onClick = {
                                scope.launch {
                                    busy = true; error = null
                                    try { container.community.joinFanClub(id); vm.reload() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                                    busy = false
                                }
                            },
                        )
                    } else {
                        Text(stringResource(R.string.fan_membership, me.loyaltyLevel, Money.grouped(me.contributionCoins)))
                        SecondaryButton(stringResource(R.string.leave_fan_club), onClick = {
                            scope.launch { runCatching { container.community.leaveFanClub(id) }.onSuccess { vm.reload() }.onFailure { error = it } }
                        })
                    }
                }
                if (d.topMembers.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.top_members)) }
                    items(d.topMembers, key = { it.id }) { m ->
                        UserRow(m.avatarUrl, m.displayName, m.username, onClick = { nav.profile(m.id) }) {
                            Text(stringResource(R.string.loyalty_level, m.loyaltyLevel), color = Brand.Gold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FanClubEditScreen(container: AppContainer, nav: Nav) {
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(false) }
    var clubId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var badge by remember { mutableStateOf("") }
    var benefits by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(Unit) {
        runCatching { container.community.myFanClub() }.onSuccess { c ->
            if (c != null) { clubId = c.id; name = c.name; badge = c.badgeLabel; benefits = c.benefits; announcement = c.announcement; cost = c.joinCostCoins.toString() }
        }.onFailure { error = it }
        loaded = true
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackTopBar(stringResource(R.string.my_fan_club), nav::back)
        if (!loaded) Loading() else Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.fan_club_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.fan_club_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(badge, { badge = it.uppercase().filter(Char::isLetterOrDigit).take(6) }, label = { Text(stringResource(R.string.fan_club_badge)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(benefits, { benefits = it.take(500) }, label = { Text(stringResource(R.string.fan_club_benefits)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(announcement, { announcement = it.take(500) }, label = { Text(stringResource(R.string.fan_club_announcement)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(cost, { cost = it.filter(Char::isDigit).take(5) }, label = { Text(stringResource(R.string.fan_club_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
            PrimaryButton(stringResource(R.string.save), loading = busy, enabled = name.isNotBlank() && badge.length >= 2, onClick = {
                scope.launch {
                    busy = true; error = null
                    try {
                        val club = container.community.upsertFanClub(name.trim(), badge, benefits.trim(), announcement.trim(), cost.toIntOrNull() ?: 0)
                        clubId = club.id
                        nav.to(Routes.fanClub(club.id))
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e }
                    busy = false
                }
            })
            clubId?.let { id -> SecondaryButton(stringResource(R.string.view_fan_club), onClick = { nav.to(Routes.fanClub(id)) }) }
        }
    }
}
