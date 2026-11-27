/*
 * Oris Music home screen.
 *
 * Genuine Oris implementation: loads the real YouTube Music home feed through
 * the app's innertube engine and queues tapped songs through the app's own
 * playback layer.
 */
package com.orismusic.client.ui.screens

import com.orismusic.client.ui.theme.OrisMotion
import com.orismusic.client.settings.HomeSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.artworkTintBackdrop
import com.orismusic.client.ui.components.DiagnosticHistoryDialog
import com.orismusic.client.ui.components.SectionHeader
import com.orismusic.client.ui.components.SkeletonCard
import com.orismusic.client.ui.components.entrance
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.components.shimmer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HomePage
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.library.DiscoveryFilterStore
import com.orismusic.client.library.FilteredSong
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.toPlayableItem
import com.orismusic.client.playback.queues.ListQueue
import timber.log.Timber
import kotlinx.coroutines.launch

private sealed interface HomeFeedState {
    data object Loading : HomeFeedState
    data class Ready(val page: HomePage) : HomeFeedState
    data class Failed(val message: String) : HomeFeedState
}

/** Kept for the process so coming back to Home shows the shelves at once instead of reloading. */
private var cachedFeed: HomeFeedState.Ready? = null
private var cachedAlbums: List<com.music.innertube.models.AlbumItem> = emptyList()

private suspend fun loadHomeFeed(): HomeFeedState =
    YouTube.home().fold(
        onSuccess = { page ->
            // YouTube rotates anonymous home shelves; some rounds carry no song
            // shelves at all. Show whatever came back - playlists included -
            // and only call it empty when nothing parseable arrived.
            if (page.sections.any { section -> section.items.isNotEmpty() }) {
                HomeFeedState.Ready(page).also { cachedFeed = it }
            } else {
                HomeFeedState.Failed("The feed came back empty")
            }
        },
        onFailure = { HomeFeedState.Failed(it.message ?: "Could not reach YouTube Music") },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (DetailTarget) -> Unit = {},
    modifier: Modifier = Modifier,
    playerConnection: PlayerConnection? = null,
    onSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    var showDiagnostic by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showDiagnostic) DiagnosticHistoryDialog { showDiagnostic = false }
    var refreshTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var refreshing by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(1400); refreshing = false } }
    val feed by produceState<HomeFeedState>(cachedFeed ?: HomeFeedState.Loading, refreshTick) {
        val fresh = loadHomeFeed()
        // A failed refresh keeps what is already on screen.
        value = if (fresh is HomeFeedState.Failed && cachedFeed != null) cachedFeed!! else fresh
    }

    val newAlbums by produceState<List<com.music.innertube.models.AlbumItem>>(cachedAlbums, refreshTick) {
        val fresh = YouTube.explore().getOrNull()?.newReleaseAlbums.orEmpty()
        if (fresh.isNotEmpty()) cachedAlbums = fresh
        value = fresh.ifEmpty { cachedAlbums }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = LibraryStore.get(context)
    val history = store.history.collectAsState().value
    val liked = store.liked.collectAsState().value
    val counts = store.playCounts.collectAsState().value
    val taste = remember(history, liked, counts) { Taste.from(history, liked, counts) }
    val filterStore = DiscoveryFilterStore.get(context)
    val filters = filterStore.filters.collectAsState().value
    val shelves by produceState<List<ArtistShelf>>(emptyList(), taste.artists, refreshTick) {
        value = if (taste.artists.isEmpty()) emptyList() else loadArtistShelves(taste.artists.take(6))
    }
    val community by produceState<List<com.music.innertube.models.PlaylistItem>>(emptyList(), taste.artists, refreshTick) {
        value = if (taste.artists.isEmpty()) emptyList() else loadCommunityPlaylists(taste.artists)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scroll: () -> Int = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 4000 }
    val scope = rememberCoroutineScope()
    val heroOn = HomeSettings.hero && !HomeSettings.minimal
    val heroSongs = remember(feed, heroOn) {
        if (!heroOn) emptyList() else (feed as? HomeFeedState.Ready)?.page?.sections
            ?.flatMap { MusicCandidates.select(it.items.filterIsInstance<SongItem>()) }
            .orEmpty().distinctBy { it.id }.take(5)
    }

    val quickPicks by produceState<Pair<List<SongItem>, String>?>(null, feed, refreshTick, filters, taste.artists) {
        value = (feed as? HomeFeedState.Ready)?.page?.let { loadQuickPicks(it, filters, taste.artists) }
    }
    Scaffold(modifier = modifier) { outerPadding ->
        Box(Modifier.fillMaxSize().padding(outerPadding)
                .artworkTintBackdrop(heroSongs.firstOrNull()?.thumbnail ?: history.firstOrNull()?.thumbnail)) {
            val auroraTint = com.orismusic.client.ui.components.rememberArtworkColor(history.firstOrNull()?.thumbnail)
            if (heroSongs.isEmpty() && (!heroOn || feed !is HomeFeedState.Loading)) Aurora(Modifier.align(Alignment.TopCenter), scroll, auroraTint)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshTick++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp + bottomInset),
                    verticalArrangement = Arrangement.spacedBy(HomeSettings.density.gap.dp),
                ) {
                    item(key = "header") {
                        HomeHeader(scroll)
                    }
                    listeningShelves(history, taste, shelves, playerConnection, onOpen, scope)
                    if (heroSongs.isNotEmpty()) item(key = "discovery-hero") {
                        if (HomeSettings.heroStyle == com.orismusic.client.settings.HeroStyle.Deck)
                            HomeDeck(heroSongs, playerConnection, scroll)
                        else HomeHero(heroSongs, playerConnection, scroll)
                    }
                    when (val state = feed) {
                        HomeFeedState.Loading -> item(key = "loading") {
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                repeat(2) {
                                    Column {
                                        Box(
                                            Modifier.padding(horizontal = 20.dp, vertical = 10.dp).width(150.dp).height(18.dp)
                                                .clip(RoundedCornerShape(8.dp)).shimmer(),
                                        )
                                        Row(
                                            Modifier.padding(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) { repeat(3) { SkeletonCard() } }
                                    }
                                }
                            }
                        }
                        is HomeFeedState.Failed -> item(key = "failed") {
                            Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Could not load your home feed", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "You're offline, or the service can't be reached right now.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Text(
                                    text = "Tap retry",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 16.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { refreshTick++ }.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                                var showDetails by remember { mutableStateOf(false) }
                                Text(
                                    text = if (showDetails) "Hide details" else "Details",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                                        .clickable { showDetails = !showDetails }.padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                                if (showDetails) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        is HomeFeedState.Ready -> if (HomeSettings.youtube) {
                            quickPicks?.let { (picks, _) ->
                                item(key = "quick-picks") {
                                    Column(Modifier.entrance(2)) {
                                        QuickPicksSection(
                                            picks, playerConnection, scope,
                                            onHide = { s -> filterStore.hide(FilteredSong(s.id, s.title, s.artists.joinToString { it.name })) },
                                            onDislike = { s -> filterStore.dislike(FilteredSong(s.id, s.title, s.artists.joinToString { it.name })) },
                                            onBlockArtist = { name -> filterStore.blockArtist(name) },
                                        )
                                    }
                                }
                            }
                            if (community.isNotEmpty() && !HomeSettings.minimal) item(key = "community-playlists") { CommunityPlaylistsSection(community, onOpen) }
                            feedItems(state.page, newAlbums, playerConnection, onOpen, scope, quickPicks?.second)
                        }
                    }
                }
            }

        }
    }
}

/** Slow drifting colour behind the header. It scrolls away at half speed. */
@Composable
private fun Aurora(modifier: Modifier, scroll: () -> Int, tint: androidx.compose.ui.graphics.Color? = null) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(OrisMotion.Drift, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "a",
    )
    val b by t.animateFloat(
        1f, 0f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(OrisMotion.SlowDrift, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "b",
    )
    val warmTarget = if (tint != null)
        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.tertiary, tint, 0.45f)
    else MaterialTheme.colorScheme.tertiary
    val warm by androidx.compose.animation.animateColorAsState(
        warmTarget, OrisMotion.entrance(), label = "aurora warm",
    )
    val cool = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(
        modifier.fillMaxWidth().height(460.dp).graphicsLayer {
            translationY = -scroll() * 0.5f
            alpha = (1f - scroll() / 700f).coerceIn(0f, 1f)
        },
    ) {
        val w = size.width
        val h = size.height
        val c1 = androidx.compose.ui.geometry.Offset(w * (0.1f + 0.6f * a), h * (0.12f + 0.2f * b))
        val c2 = androidx.compose.ui.geometry.Offset(w * (0.9f - 0.6f * a), h * (0.3f + 0.2f * a))
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(warm.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.Transparent), center = c1, radius = w * 0.75f,
            ), radius = w * 0.75f, center = c1,
        )
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(cool.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent), center = c2, radius = w * 0.7f,
            ), radius = w * 0.7f, center = c2,
        )
    }
}

/** Full-bleed featured song: cover drifts against the swipe, the Play pill starts a radio from it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeHero(songs: List<SongItem>, connection: PlayerConnection?, scroll: () -> Int) {
    val bg = MaterialTheme.colorScheme.background
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { songs.size })
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(songs.size) {
        while (true) {
            kotlinx.coroutines.delay(6500)
            if (!pager.isScrollInProgress && songs.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % songs.size, animationSpec = OrisMotion.entrance())
            }
        }
    }
    val topInset = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().height(520.dp + topInset)) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val song = songs[page]
            Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)) {
                Artwork(
                    song.thumbnail,
                    Modifier.fillMaxSize().graphicsLayer {
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        scaleX = 1.2f; scaleY = 1.2f
                        translationX = off * size.width * 0.22f
                        translationY = scroll() * 0.25f
                    },
                    androidx.compose.ui.graphics.RectangleShape, song.title, 1200,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                            0.22f to androidx.compose.ui.graphics.Color.Transparent,
                            0.5f to androidx.compose.ui.graphics.Color.Transparent,
                            1f to bg,
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 40.dp)) {
                    Text("Picked for you", style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f))
                    Text(song.title, style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp))
                    Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.padding(top = 16.dp).clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.tertiary)
                            .pressClickable({
                                scope.launch { connection?.playQueue(MusicRadioQueue("Picked for you", songs, song.id)) }
                                com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                            }, 0.94f)
                            .padding(start = 18.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Filled.PlayArrow, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiary)
                        Spacer(Modifier.width(6.dp))
                        Text("Play", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiary)
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 52.dp)) {
            repeat(songs.size) { i ->
                val active = pager.currentPage == i
                val w by androidx.compose.animation.core.animateDpAsState(
                    if (active) 20.dp else 6.dp, OrisMotion.settle(), label = "dot",
                )
                Box(
                    Modifier.padding(horizontal = 2.dp).height(6.dp).width(w).clip(RoundedCornerShape(3.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (active) 0.95f else 0.35f)),
                )
            }
        }
    }
}

/** Fixed-size first frame: feed loading never changes the header's position. */
@Composable
private fun HomeHeader(scroll: () -> Int) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late-night soundtrack"
    }
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        reveal.animateTo(1f, OrisMotion.slow())
    }
    Row(
        Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).graphicsLayer {
            val progress = reveal.value
            scaleX = 0.92f + 0.08f * progress
            scaleY = scaleX
            rotationZ = -18f * (1f - progress)
        }, contentAlignment = Alignment.Center) {
            OrisLogo(Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                remember { java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.8f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            Text(greeting, style = MaterialTheme.typography.headlineSmall,
                fontFamily = com.orismusic.client.ui.theme.DisplaySerif,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small Oris mark: an orbit ring with a bright dot. */
@Composable
fun OrisLogo(modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    val ink = tint
    val warm = MaterialTheme.colorScheme.tertiary
    val hole = MaterialTheme.colorScheme.background
    androidx.compose.foundation.Canvas(modifier) {
        // Same drawing as the launcher icon: a record with its tone arm resting on it, all inside the ring.
        val k = size.minDimension / 64f
        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset((x - 22f) * k, (y - 22f) * k)
        val disc = p(54f, 54f)
        drawCircle(ink, radius = 25f * k, center = disc, style = androidx.compose.ui.graphics.drawscope.Stroke(3f * k))
        drawCircle(ink.copy(alpha = 0.45f), radius = 18f * k, center = disc, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f * k))
        drawCircle(warm, radius = 7f * k, center = disc)
        drawCircle(hole, radius = 1.8f * k, center = disc)
        val arm = androidx.compose.ui.graphics.Path().apply {
            moveTo(p(69f, 39f).x, p(69f, 39f).y); lineTo(p(62f, 44f).x, p(62f, 44f).y); lineTo(p(58.5f, 49f).x, p(58.5f, 49f).y)
        }
        drawPath(arm, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2.6f * k,
            cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawCircle(ink, radius = 3.5f * k, center = p(69f, 39f))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.feedItems(
    page: HomePage,
    newAlbums: List<com.music.innertube.models.AlbumItem>,
    playerConnection: PlayerConnection?,
    onOpen: (DetailTarget) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
    skipTitle: String? = null,
) {
    if (newAlbums.isNotEmpty() && !HomeSettings.minimal) item(key = "hero") { HeroPager(newAlbums, onOpen) }
    itemsIndexed(
        // The feed often has its own "New releases" row; the hero above already covers it.
        // The shelf Quick picks shows as a list is not repeated as cards.
        items = page.sections.filter { it.items.isNotEmpty() && it.title != skipTitle && !(!HomeSettings.minimal && newAlbums.isNotEmpty() && it.title.trim().equals("New releases", ignoreCase = true)) }.take(if (HomeSettings.minimal) 1 else Int.MAX_VALUE),
        key = { i, it -> "$i-${it.title}" },
    ) { sectionIndex, section ->
        val lively = HomeSettings.motion == com.orismusic.client.settings.HomeMotion.Lively
        Column(if (lively) Modifier.entrance(sectionIndex) else Modifier) {
            SectionHeader(title = section.title)
            val songs = MusicCandidates.select(section.items.filterIsInstance<SongItem>())
            com.orismusic.client.ui.components.OrbitRow(
                items = section.items.filter { it !is SongItem || it in songs },
                key = { "${section.title}-${it.id}" },
            ) { item, _, orbit ->
                when (item) {
                    is SongItem -> SongCard(
                        modifier = orbit,
                        song = item,
                        onClick = {
                            Timber.tag("OrisTap").w("SONG TAPPED ${item.title} conn=${playerConnection != null}")
                            playerConnection?.let { connection ->
                                scope.launch {
                                    connection.playQueue(MusicRadioQueue(section.title, songs, item.id))
                                }
                                com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                            }
                        },
                    )
                    else -> GenericCard(
                        modifier = orbit,
                        title = item.title,
                        thumbnail = item.thumbnail,
                        onClick = item.toDetailTarget()?.let { target -> { onOpen(target) } },
                    )
                }
            }
        }
    }
}

/** Big swipeable new-release cards: the cover drifts against the card, neighbours tilt and shrink. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HeroPager(albums: List<com.music.innertube.models.AlbumItem>, onOpen: (DetailTarget) -> Unit) {
    val items = remember(albums) { albums.take(8) }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { items.size })
    androidx.compose.runtime.LaunchedEffect(items.size) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (!pager.isScrollInProgress && items.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }
    Column {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("New releases", style = MaterialTheme.typography.titleLarge)
        }
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 44.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.padding(top = 10.dp),
        ) { page ->
            val album = items[page]
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .graphicsLayer {
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        val near = 1f - kotlin.math.abs(off).coerceIn(0f, 1f)
                        val sc = 0.86f + 0.14f * near
                        scaleX = sc; scaleY = sc
                        alpha = 0.55f + 0.45f * near
                        rotationY = off * -14f
                        cameraDistance = 14f * density
                    }
                    .clip(HomeSettings.corners.shape(24))
                    .pressClickable({ album.toDetailTarget()?.let(onOpen) }, 0.98f),
            ) {
                Artwork(
                    album.thumbnail,
                    Modifier.fillMaxSize().graphicsLayer {
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        scaleX = 1.25f; scaleY = 1.25f
                        translationX = off * size.width * 0.12f
                    },
                    RoundedCornerShape(0.dp), album.title, 900,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.45f to androidx.compose.ui.graphics.Color.Transparent,
                            1f to androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text("NEW", style = MaterialTheme.typography.labelSmall,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall,
                        color = androidx.compose.ui.graphics.Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val who = album.artists?.joinToString { it.name }.orEmpty()
                    if (who.isNotEmpty()) Text(who, style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(items.size) { i ->
                val active = pager.currentPage == i
                val w by androidx.compose.animation.core.animateDpAsState(
                    if (active) 22.dp else 6.dp,
                    OrisMotion.settle(), label = "dot",
                )
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(w).clip(RoundedCornerShape(3.dp))
                        .background(if (active) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}

@Composable
private fun SongCard(
    song: SongItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .width(150.dp)
            .pressClickable(onClick),
    ) {
        Artwork(song.thumbnail, Modifier.size(150.dp), HomeSettings.corners.shape(16), song.title)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = song.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GenericCard(
    modifier: Modifier = Modifier,
    title: String,
    thumbnail: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.width(150.dp)
            .then(if (onClick != null) Modifier.pressClickable(onClick) else Modifier),
    ) {
        Artwork(thumbnail, Modifier.size(150.dp), HomeSettings.corners.shape(16), title)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
