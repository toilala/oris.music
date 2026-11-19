/*
 * Oris Music home screen.
 *
 * Genuine Oris implementation: loads the real YouTube Music home feed through
 * the app's innertube engine and queues tapped songs through the app's own
 * playback layer.
 */
package com.orismusic.client.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.orismusic.client.utils.PlaybackDiagnostic
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

private suspend fun loadHomeFeed(): HomeFeedState =
    YouTube.home().fold(
        onSuccess = { page ->
            // YouTube rotates anonymous home shelves; some rounds carry no song
            // shelves at all. Show whatever came back - playlists included -
            // and only call it empty when nothing parseable arrived.
            if (page.sections.any { section -> section.items.isNotEmpty() }) {
                HomeFeedState.Ready(page)
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
) {
    var showDiagnostic by remember { androidx.compose.runtime.mutableStateOf(false) }
    val diagnostic = PlaybackDiagnostic.message.collectAsState().value
    if (showDiagnostic) AlertDialog(
        onDismissRequest = { showDiagnostic = false },
        title = { Text("Playback diagnostic") },
        text = { Text(diagnostic) },
        confirmButton = { TextButton(onClick = { showDiagnostic = false }) { Text("Close") } },
    )
    var refreshTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var refreshing by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(refreshing) { if (refreshing) { kotlinx.coroutines.delay(1400); refreshing = false } }
    val feed by produceState<HomeFeedState>(HomeFeedState.Loading, refreshTick) {
        value = loadHomeFeed()
    }

    val newAlbums by produceState<List<com.music.innertube.models.AlbumItem>>(emptyList(), refreshTick) {
        value = YouTube.explore().getOrNull()?.newReleaseAlbums.orEmpty()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val hasHistory = LibraryStore.get(context).history.collectAsState().value.isNotEmpty()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scroll: () -> Int = { if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset else 4000 }
    val scope = rememberCoroutineScope()
    val heroSongs = remember(feed) {
        (feed as? HomeFeedState.Ready)?.page?.sections
            ?.flatMap { MusicCandidates.select(it.items.filterIsInstance<SongItem>()) }
            .orEmpty().distinctBy { it.id }.take(5)
    }

    Scaffold(modifier = modifier) { outerPadding ->
        Box(Modifier.fillMaxSize().padding(outerPadding)) {
            if (heroSongs.isEmpty()) Aurora(Modifier.align(Alignment.TopCenter), scroll)
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { refreshing = true; refreshTick++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item(key = "header") {
                        if (heroSongs.isNotEmpty()) HomeHero(heroSongs, playerConnection, onSettings, scroll)
                        else Box(Modifier.statusBarsPadding()) { HomeHeader(scroll) }
                    }
                    if (hasHistory) item(key = "jump") { JumpBackIn(playerConnection) }
                    item(key = "moods") { MoodChips(onMood = { SearchRequest.pending = it; onSearch() }) }
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
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
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
                            }
                        }
                        is HomeFeedState.Ready -> feedItems(state.page, newAlbums, playerConnection, onOpen, scope)
                    }
                }
            }
        }
    }
}

/** Slow drifting colour behind the header. It scrolls away at half speed. */
@Composable
private fun Aurora(modifier: Modifier, scroll: () -> Int) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "aurora")
    val a by t.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(13000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "a",
    )
    val b by t.animateFloat(
        1f, 0f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(17000, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ), label = "b",
    )
    val warm = MaterialTheme.colorScheme.tertiary
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
private fun HomeHero(songs: List<SongItem>, connection: PlayerConnection?, onSettings: () -> Unit, scroll: () -> Int) {
    val bg = MaterialTheme.colorScheme.background
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { songs.size })
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(songs.size) {
        while (true) {
            kotlinx.coroutines.delay(6500)
            if (!pager.isScrollInProgress && songs.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % songs.size, animationSpec = androidx.compose.animation.core.tween(900))
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
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrisLogo(Modifier.size(28.dp), tint = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.width(10.dp))
            Text("Oris", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.16f))
                    .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.22f), androidx.compose.foundation.shape.CircleShape)
                    .pressClickable(onSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = "Account",
                    tint = androidx.compose.ui.graphics.Color.White)
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 52.dp)) {
            repeat(songs.size) { i ->
                val active = pager.currentPage == i
                val w by androidx.compose.animation.core.animateDpAsState(
                    if (active) 20.dp else 6.dp, androidx.compose.animation.core.spring(0.6f, 400f), label = "dot",
                )
                Box(
                    Modifier.padding(horizontal = 2.dp).height(6.dp).width(w).clip(RoundedCornerShape(3.dp))
                        .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (active) 0.95f else 0.35f)),
                )
            }
        }
    }
}

/** Logo, name and a time-aware greeting. Lifts and fades as the page scrolls. */
@Composable
private fun HomeHeader(scroll: () -> Int) {
    val hour = remember { java.time.LocalTime.now().hour }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Still up?"
    }
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(24000, easing = androidx.compose.animation.core.LinearEasing),
        ), label = "angle",
    )
    val reveal = remember { androidx.compose.animation.core.Animatable(0f) }
    val reveal2 = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        launch { reveal.animateTo(1f, androidx.compose.animation.core.spring(0.7f, 120f)) }
        kotlinx.coroutines.delay(140)
        reveal2.animateTo(1f, androidx.compose.animation.core.spring(0.75f, 100f))
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 10.dp).graphicsLayer {
            translationY = scroll() * 0.35f
            alpha = (1f - scroll() / 420f).coerceIn(0f, 1f)
        },
    ) {
        Row(
            Modifier.graphicsLayer { alpha = reveal.value; translationY = (1f - reveal.value) * -30f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrisLogo(Modifier.size(30.dp).graphicsLayer { rotationZ = angle })
            Spacer(Modifier.width(10.dp))
            Text("Oris", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            greeting,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp).graphicsLayer {
                alpha = reveal2.value; translationY = (1f - reveal2.value) * 50f
            },
        )
        Text(
            "What are we listening to?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).graphicsLayer {
                alpha = reveal2.value; translationY = (1f - reveal2.value) * 70f
            },
        )
    }
}

/** Quick mood searches. Each chip springs in, tinted with its own colour. */
@Composable
private fun MoodChips(onMood: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val moods = listOf(
        Triple("Chill", "chill music", androidx.compose.ui.graphics.Color(0xFF6FB6C9)) to androidx.compose.material.icons.Icons.Rounded.Spa,
        Triple("Hype", "hype workout music", androidx.compose.ui.graphics.Color(0xFFE0745A)) to androidx.compose.material.icons.Icons.Rounded.Bolt,
        Triple("Focus", "focus music", androidx.compose.ui.graphics.Color(0xFF9B8FE0)) to androidx.compose.material.icons.Icons.Rounded.Headphones,
        Triple("Late night", "late night music", androidx.compose.ui.graphics.Color(0xFF7C8FBF)) to androidx.compose.material.icons.Icons.Rounded.NightsStay,
        Triple("Nepali", "nepali songs", androidx.compose.ui.graphics.Color(0xFFD9A441)) to androidx.compose.material.icons.Icons.Rounded.Landscape,
    )
    Row(
        Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        moods.forEachIndexed { index, (mood, moodIcon) ->
            val (label, query, accent) = mood
            val pop = remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(300L + index * 70L)
                pop.animateTo(1f, androidx.compose.animation.core.spring(0.55f, 260f))
            }
            Row(
                Modifier
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(50))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.30f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.07f)),
                        ),
                    )
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(50))
                    .pressClickable({ onMood(query) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(moodIcon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
            }
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

/** Recently played songs kept on the phone, so Home is useful even when the feed is not. */
@Composable
private fun JumpBackIn(connection: PlayerConnection?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by LibraryStore.get(context).history.collectAsState()
    if (history.isEmpty()) return
    val songs = history.take(12)
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionHeader(title = "Recently played")
        com.orismusic.client.ui.components.OrbitRow(items = songs, key = { "back-${it.id}" }) { song, songIndex, orbit ->
                Column(
                    modifier = orbit
                        .width(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            connection?.playQueue(
                                ListQueue(
                                    title = "Recently played",
                                    items = songs.map { it.toPlayableItem() },
                                    startIndex = songs.indexOf(song),
                                ),
                            )
                        },
                ) {
                    Artwork(song.thumbnail, Modifier.size(150.dp),
                        RoundedCornerShape(24.dp),
                        song.title)
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.feedItems(
    page: HomePage,
    newAlbums: List<com.music.innertube.models.AlbumItem>,
    playerConnection: PlayerConnection?,
    onOpen: (DetailTarget) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (newAlbums.isNotEmpty()) item(key = "hero") { HeroPager(newAlbums, onOpen) }
    itemsIndexed(
        // The feed often has its own "New releases" row; the hero above already covers it.
        items = page.sections.filter { it.items.isNotEmpty() && !(newAlbums.isNotEmpty() && it.title.trim().equals("New releases", ignoreCase = true)) },
        key = { _, it -> it.title },
    ) { sectionIndex, section ->
        Column(Modifier.entrance(sectionIndex)) {
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
                    .clip(RoundedCornerShape(30.dp))
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
                    androidx.compose.animation.core.spring(0.6f, 400f), label = "dot",
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
        Artwork(song.thumbnail, Modifier.size(150.dp), RoundedCornerShape(16.dp), song.title)
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
        Artwork(thumbnail, Modifier.size(150.dp), RoundedCornerShape(16.dp), title)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

