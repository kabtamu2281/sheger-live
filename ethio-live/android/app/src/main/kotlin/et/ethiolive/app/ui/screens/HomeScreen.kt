package et.ethiolive.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import et.ethiolive.app.AppContainer
import et.ethiolive.app.R
import et.ethiolive.app.data.RoomCard
import et.ethiolive.app.ui.components.Chip
import et.ethiolive.app.ui.components.EmptyState
import et.ethiolive.app.ui.components.ErrorState
import et.ethiolive.app.ui.components.Loading
import et.ethiolive.app.ui.gifts.GiftArt
import et.ethiolive.app.ui.nav.Nav
import et.ethiolive.app.ui.nav.Routes
import et.ethiolive.app.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

val HOME_TABS = listOf(
    "popular" to R.string.tab_popular,
    "recommended" to R.string.tab_for_you,
    "following" to R.string.tab_following,
    "new" to R.string.tab_new,
    "pk" to R.string.tab_pk,
    "multi" to R.string.tab_multi,
    "audio" to R.string.tab_audio,
    "nearby" to R.string.tab_nearby,
    "recent" to R.string.tab_recent,
)

data class FeedState(
    val tab: String = "popular",
    val category: String? = null,
    val rooms: List<RoomCard> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: Throwable? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FeedState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val pageSize = 20

    init { load(reset = true) }

    fun selectTab(tab: String) {
        if (tab == _state.value.tab) return
        _state.value = FeedState(tab = tab, category = _state.value.category)
        load(reset = true)
    }

    fun selectCategory(category: String?) {
        _state.value = FeedState(tab = _state.value.tab, category = category)
        load(reset = true)
    }

    fun refresh() {
        _state.value = _state.value.copy(refreshing = true)
        load(reset = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.endReached || s.rooms.isEmpty()) return
        load(reset = false)
    }

    private fun cacheKey(s: FeedState) = "feed:${s.tab}:${s.category ?: "all"}"

    private fun load(reset: Boolean) {
        job?.cancel()
        val s = _state.value
        job = viewModelScope.launch {
            if (reset && s.rooms.isEmpty()) {
                container.cache.read(cacheKey(s), ListSerializer(RoomCard.serializer()), maxAgeMs = 10 * 60_000L)?.let { cached ->
                    _state.value = _state.value.copy(rooms = cached)
                }
            }
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val offset = if (reset) 0 else s.rooms.size
                val page = container.discovery.rooms(s.tab, s.category, offset = offset, limit = pageSize)
                val rooms = if (reset) page else (s.rooms + page).distinctBy { it.id }
                _state.value = _state.value.copy(rooms = rooms, loading = false, refreshing = false, endReached = page.size < pageSize)
                if (reset) container.cache.write(cacheKey(s), ListSerializer(RoomCard.serializer()), page)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, refreshing = false, error = e)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, nav: Nav) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsState()
    val account by container.account.collectAsState()
    val gridState = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 } }
    LaunchedEffect(gridState) {
        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it >= state.rooms.size - 4 }.collect { vm.loadMore() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            GiftArt("adey", 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Brand.Gold, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.to(Routes.EXPLORE) }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
            IconButton(onClick = { nav.to(Routes.NOTIFICATIONS) }) { Icon(Icons.Default.Notifications, stringResource(R.string.notifications)) }
        }
        ScrollableTabRow(
            selectedTabIndex = HOME_TABS.indexOfFirst { it.first == state.tab }.coerceAtLeast(0),
            edgePadding = 12.dp,
            containerColor = Color.Transparent,
            divider = {},
        ) {
            HOME_TABS.forEach { (key, label) ->
                Tab(selected = state.tab == key, onClick = { vm.selectTab(key) }, text = {
                    Text(stringResource(label), fontWeight = if (state.tab == key) FontWeight.Bold else FontWeight.Normal)
                })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Chip(stringResource(R.string.all), state.category == null, { vm.selectCategory(null) }) }
            items(CATEGORIES) { c -> Chip(categoryLabel(c), state.category == c, { vm.selectCategory(c) }) }
        }
        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                state.rooms.isEmpty() && state.loading -> Loading()
                state.rooms.isEmpty() && state.error != null -> ErrorState(state.error, onRetry = vm::refresh)
                state.rooms.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    if (state.tab == "nearby" && account?.settings?.nearbyRegion == null) {
                        EmptyState(R.string.nearby_off_title, R.string.nearby_off_body) {
                            Chip(stringResource(R.string.nearby_choose_region), true, { nav.to(Routes.PRIVACY) })
                        }
                    } else EmptyState(R.string.feed_empty_title, R.string.feed_empty_body) {
                        Chip(stringResource(R.string.go_live_now), true, { nav.to(Routes.GO_LIVE) })
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.rooms, key = { it.id }) { room -> RoomCardTile(room, onClick = { nav.room(room.id) }) }
                    if (state.loading) item(span = { GridItemSpan(2) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CoinPill(coins: Long, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoinIcon(16.dp)
        Spacer(Modifier.width(4.dp))
        Text(et.ethiolive.core.Money.compact(coins), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun CoinIcon(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape).background(Brand.goldGradient), contentAlignment = Alignment.Center) {
        Text("E", color = Color(0xFF6E3219), fontWeight = FontWeight.Black, fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.6f, androidx.compose.ui.unit.TextUnitType.Sp))
    }
}
