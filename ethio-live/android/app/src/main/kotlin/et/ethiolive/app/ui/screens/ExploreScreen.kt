package et.ethiolive.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.Hashtag
import et.ethiolive.app.data.RoomCard
import et.ethiolive.app.data.UserCard
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.LiveBadge
import et.ethiolive.app.ui.components.SectionHeader
import et.ethiolive.app.ui.components.UserRow
import et.ethiolive.app.ui.components.errorMessage
import et.ethiolive.app.ui.components.rememberLoad
import et.ethiolive.app.ui.components.Load
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState

private data class Trending(val tags: List<Hashtag>, val creators: List<UserCard>)

@Composable
fun ExploreScreen(container: AppContainer, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchRooms by rememberSaveable { mutableStateOf(true) }
    var roomResults by remember { mutableStateOf<List<RoomCard>>(emptyList()) }
    var userResults by remember { mutableStateOf<List<UserCard>>(emptyList()) }
    var searchError by remember { mutableStateOf<Throwable?>(null) }
    val trending = rememberLoad("explore-trending") { Trending(container.discovery.trendingHashtags(), container.discovery.trendingCreators()) }
    val trendingState by trending.state.collectAsState()

    LaunchedEffect(query, searchRooms) {
        val q = query.trim()
        if (q.length < 2) { roomResults = emptyList(); userResults = emptyList(); return@LaunchedEffect }
        delay(300) // debounce typing
        searchError = null
        runCatching {
            if (searchRooms) roomResults = container.discovery.searchRooms(q) else userResults = container.discovery.searchUsers(q)
        }.onFailure { searchError = it }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it.take(60) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, stringResource(R.string.clear)) } },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true, shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (query.trim().length >= 2) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(R.string.search_rooms), searchRooms, { searchRooms = true })
                Chip(stringResource(R.string.search_people), !searchRooms, { searchRooms = false })
            }
            searchError?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (searchRooms) {
                    if (roomResults.isEmpty()) item { EmptyState(R.string.search_no_results) }
                    items(roomResults, key = { it.id }) { r ->
                        UserRow(r.host.avatarUrl, r.title, r.host.username, r.host.level, r.host.isVerified, onClick = { nav.room(r.id) }) { LiveBadge() }
                    }
                } else {
                    if (userResults.isEmpty()) item { EmptyState(R.string.search_no_results) }
                    items(userResults, key = { it.id }) { u ->
                        UserRow(u.avatarUrl, u.displayName, u.username, u.level, u.isVerified, onClick = { nav.profile(u.id) }) {
                            if (u.isLive) LiveBadge()
                        }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickTile(Icons.Default.EmojiEvents, stringResource(R.string.rankings), Modifier.weight(1f)) { nav.to(Routes.RANKINGS) }
                        QuickTile(Icons.Default.Celebration, stringResource(R.string.events), Modifier.weight(1f)) { nav.to(Routes.EVENTS) }
                    }
                }
                val t = (trendingState as? Load.Ready<Trending>)?.value
                if (t != null) {
                    if (t.tags.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.trending_hashtags)) }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(t.tags) { tag -> Chip("#${tag.tag} · ${tag.liveRooms}", false, { query = "#${tag.tag}"; searchRooms = true }) }
                            }
                        }
                    }
                    item { SectionHeader(stringResource(R.string.trending_creators)) }
                    if (t.creators.isEmpty()) item { EmptyState(R.string.trending_empty) }
                    items(t.creators, key = { it.id }) { u ->
                        UserRow(u.avatarUrl, u.displayName, u.username, u.level, u.isVerified, onClick = { nav.profile(u.id) }) {
                            if (u.isLive) LiveBadge()
                        }
                    }
                } else if (trendingState is Load.Failed) {
                    item { Text(errorMessage((trendingState as Load.Failed).error), modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Brand.Gold)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
    Spacer(Modifier.height(0.dp))
}
