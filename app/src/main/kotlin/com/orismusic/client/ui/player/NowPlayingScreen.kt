package com.orismusic.client.ui.player

/*
 * Full player screen. The layout, controls and motion follow the Echo Music app's player
 * (GPL-3.0, https://github.com/EchoMusicApp/Echo-Music): gradient artwork background, square
 * artwork carousel, slim seek bar, wavy play blob, cluster buttons and the bottom action bar.
 * Playback, queue, lyrics and timers run on Oris's own PlayerConnection. All code is Oris's own.
 */

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.PlaylistStore
import com.orismusic.client.library.currentSavedSong
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.settings.PlayButtonStyle
import com.orismusic.client.settings.PlayerBackgroundStyle
import com.orismusic.client.settings.PlayerSeekBar
import com.orismusic.client.settings.PlayerSettings
import com.orismusic.client.settings.SwipeSensitivity
import com.orismusic.client.playback.SleepTimer
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.utils.formatTime
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onQueue: () -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val metadata by connection.currentMetadata.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    val playbackState by connection.playbackState.collectAsState()
    val repeatMode by connection.repeatMode.collectAsState()
    val shuffle by connection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by connection.canSkipPrevious.collectAsState()
    val canSkipNext by connection.canSkipNext.collectAsState()
    val index by connection.currentMediaItemIndex.collectAsState()
    val position by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    val player = connection.player

    val art = metadata?.artworkUri?.toString()
    val title = metadata?.title?.toString().orEmpty()
    val artist = metadata?.artist?.toString().orEmpty()
    val artistId = metadata?.extras?.getString("artist_id")
    val mediaId = player.currentMediaItem?.mediaId.orEmpty()

    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    var trayOpen by remember { mutableStateOf(false) }
    var offsetDialog by remember { mutableStateOf(false) }
    // "Finish current song": pause when the track changes or the queue ends.
    var stopAfterSongId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stopAfterSongId, mediaId, playbackState) {
        val target = stopAfterSongId ?: return@LaunchedEffect
        val moved = mediaId.isNotBlank() && mediaId != target
        if (moved || playbackState == Player.STATE_ENDED) {
            stopAfterSongId = null
            connection.pause()
        }
    }

    // Fetch lyrics as soon as a song starts so the Lyrics view opens already filled.
    LaunchedEffect(mediaId, metadata?.title, duration > 0) {
        if (mediaId.isBlank() || title.isBlank() || duration <= 0) return@LaunchedEffect
        runCatching {
            com.orismusic.client.lyrics.LyricsRepository.load(
                mediaId, title, artist, metadata?.albumTitle?.toString().orEmpty(),
                (duration.coerceAtLeast(0) / 1000).toInt(), "automatic",
                com.orismusic.client.settings.LyricsPreferences(context).read(),
            )
        }
    }
    // If playback died while the app was away, the service restarts with an empty queue and
    // this screen would sit blank. Give metadata a moment, then fold back to the miniplayer.
    val restoredOpen = remember { com.orismusic.client.ui.navigation.PlayerLaunch.consumeRecreatedFlag() }
    LaunchedEffect(metadata == null, connection) {
        if (metadata != null || !restoredOpen) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (connection.currentMetadata.value == null && connection.player.mediaItemCount == 0) onCollapse()
    }

    val gradientColors = rememberPlayerGradientColors(art)
    val backgroundStyle = PlayerSettings.backgroundStyle

    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (backgroundStyle) {
            PlayerBackgroundStyle.Gradient -> {
                // Gradient background: three stops from the artwork, crossfading on song change.
                Crossfade(targetState = gradientColors, animationSpec = tween(800), label = "player gradient") { colors ->
                    if (colors != null) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(colors[0], colors[1], colors[2]))
                            )
                        )
                    }
                }
            }
            PlayerBackgroundStyle.Blur -> {
                Crossfade(targetState = art, animationSpec = tween(800), label = "player blur") { url ->
                    Artwork(
                        url,
                        Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(70.dp),
                        androidx.compose.ui.graphics.RectangleShape,
                        null,
                        px = 300,
                    )
                }
            }
            PlayerBackgroundStyle.Solid -> {}
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (backgroundStyle == PlayerBackgroundStyle.Blur) 0.6f else 0.2f)))

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Spacer(Modifier.height(48.dp))
            // Header: collapse control, or a mini artwork + title while lyrics are open.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (lyricsOpen) {
                    Artwork(art, Modifier.size(56.dp), RoundedCornerShape(6.dp), title, px = 200)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = PlayerTextButtonColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).basicMarquee(),
                    )
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                ClusterButton(
                    onClick = onCollapse,
                    icon = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Collapse player",
                    shape = CircleShape,
                )
            }
            Spacer(Modifier.height(4.dp))

            // Top area: artwork carousel, or the lyrics list in its place.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                Crossfade(targetState = lyricsOpen, animationSpec = tween(300), label = "player mode") { open ->
                    if (open) {
                        LyricsPane(connection, onClose = { lyricsOpen = false }, embedded = true)
                    } else {
                        val side = min(maxWidth.value - 64f, maxHeight.value).coerceAtLeast(120f).dp
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ArtworkCarousel(connection, side)
                        }
                    }
                }
            }

            // Title / artist + action cluster.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Crossfade(targetState = title, label = "title fade") { t ->
                        Text(
                            t,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = PlayerTextButtonColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Crossfade(targetState = artist, label = "artist fade") { a ->
                        Text(
                            a,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = PlayerTextButtonColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp)
                                .then(
                                    if (artistId != null) Modifier.clickable { onOpenArtist(artistId, artist) } else Modifier
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (lyricsOpen) {
                        ClusterButton(
                            onClick = { lyricsOpen = false },
                            icon = Icons.Rounded.Lyrics,
                            contentDescription = "Close lyrics",
                            shape = PlayerChipShape,
                        )
                        ClusterButton(
                            onClick = { offsetDialog = true },
                            icon = Icons.Rounded.MoreVert,
                            contentDescription = "Lyrics options",
                            shape = PlayerChipShape,
                        )
                    } else {
                        val library = remember { LibraryStore.get(context) }
                        val liked by library.liked.collectAsState()
                        val saved = remember(metadata, index) { connection.currentSavedSong() }
                        val isLiked = saved != null && liked.any { it.id == saved.id }
                        ClusterButton(
                            onClick = { saved?.let { library.toggleLike(it) } },
                            icon = Icons.Rounded.PlaylistAdd, // placeholder replaced by HeartBurstIcon content below
                            contentDescription = if (isLiked) "Unlike" else "Like",
                            shape = PlayerChipShape,
                            active = isLiked,
                            content = {
                                HeartBurstIcon(isLiked, if (isLiked) PlayerIconButtonColor else PlayerTextButtonColor, Modifier.size(22.dp))
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Seek bar: the slim line, or the wave bar when chosen in Player settings.
            var sliderPosition by remember { mutableStateOf<Float?>(null) }
            val safeDuration = duration.coerceAtLeast(0L)
            val shownPosition = sliderPosition?.let { (it * safeDuration).toLong() } ?: position.coerceAtLeast(0L)
            val fraction = if (safeDuration > 0) (shownPosition.toFloat() / safeDuration).coerceIn(0f, 1f) else 0f
            if (PlayerSettings.seekBar == PlayerSeekBar.Wave) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    WaveSeekBar(
                        player = player,
                        durationMs = safeDuration,
                        isPlaying = isPlaying,
                        onSeek = { connection.seekTo(it) },
                        accent = PlayerTextButtonColor,
                    )
                }
            } else {
                val view = androidx.compose.ui.platform.LocalView.current
                var lastTick by remember { mutableStateOf(-1) }
                Slider(
                    value = fraction,
                    onValueChange = { v ->
                        sliderPosition = v
                        val tick = (v * 20).toInt()
                        if (tick != lastTick) {
                            lastTick = tick
                            if (PlayerSettings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    onValueChangeFinished = {
                        sliderPosition?.let { connection.seekTo((it * safeDuration).toLong()) }
                        sliderPosition = null
                        lastTick = -1
                    },
                    thumb = { InvisibleSliderThumb() },
                    track = { SlimSliderTrack(fraction, Modifier.fillMaxWidth().height(10.dp)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp).height(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                    Text(formatTime(shownPosition), style = MaterialTheme.typography.labelMedium, color = PlayerTextButtonColor.copy(alpha = 0.7f))
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (safeDuration <= 0) ""
                        else if (PlayerSettings.remaining) "-" + formatTime((safeDuration - shownPosition).coerceAtLeast(0))
                        else formatTime(safeDuration),
                        style = MaterialTheme.typography.labelMedium,
                        color = PlayerTextButtonColor.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Transport.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostSkipButton(
                    onClick = { connection.seekToPrevious() },
                    enabled = canSkipPrevious,
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    direction = -1,
                )
                Spacer(Modifier.width(16.dp))
                val ended = playbackState == Player.STATE_ENDED
                OrbitPlayButton(
                    style = PlayerSettings.playButton,
                    orbit = PlayerSettings.playButton == PlayButtonStyle.Orbit,
                    playing = isPlaying,
                    onClick = {
                        if (ended) {
                            connection.player.seekTo(0, 0)
                            connection.player.playWhenReady = true
                        } else connection.togglePlayPause()
                    },
                    icon = if (ended) Icons.Rounded.Replay else if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (ended) "Replay" else if (isPlaying) "Pause" else "Play",
                )
                Spacer(Modifier.width(16.dp))
                GhostSkipButton(
                    onClick = { connection.seekToNext() },
                    enabled = canSkipNext,
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    direction = 1,
                )
            }

            Spacer(Modifier.height(30.dp))

            // Essentials only. Secondary actions live in More.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 12.dp).navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ClusterButton(onClick = onQueue, icon = Icons.Rounded.QueueMusic, contentDescription = "Queue", shape = PlayerChipShape)
                ClusterButton(
                    onClick = { lyricsOpen = !lyricsOpen },
                    icon = Icons.Rounded.Lyrics,
                    contentDescription = "Lyrics",
                    shape = PlayerChipShape,
                    active = lyricsOpen,
                )
                ClusterButton(onClick = { trayOpen = true }, icon = Icons.Rounded.MoreVert, contentDescription = "More", shape = PlayerChipShape)
            }
        }
    }

    if (trayOpen) {
        PlayerTray(connection, stopAfterSongActive = stopAfterSongId != null,
            onStopAfterSong = { SleepTimer.cancel(); stopAfterSongId = mediaId },
            onCancelStopAfterSong = { stopAfterSongId = null }, onQueue = { trayOpen = false; onQueue() }, onDismiss = { trayOpen = false })
    }
    if (offsetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { offsetDialog = false },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { offsetDialog = false }) { Text("Done") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { com.orismusic.client.settings.LyricTiming.set(mediaId, 0) }) { Text("Reset") } },
            title = { Text("Lyrics timing") },
            text = {
                val lyricOffset = com.orismusic.client.settings.LyricTiming.offset(mediaId)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.TextButton(onClick = { com.orismusic.client.settings.LyricTiming.set(mediaId, lyricOffset - 100) }) { Text("-100ms") }
                    Text((if (lyricOffset >= 0) "+" else "") + lyricOffset + "ms")
                    androidx.compose.material3.TextButton(onClick = { com.orismusic.client.settings.LyricTiming.set(mediaId, lyricOffset + 100) }) { Text("+100ms") }
                }
            },
        )
    }
}

/**
 * Square artwork with a three-page carousel: swipe left/right to skip, pages off centre
 * shrink and fade, double tap edges to jump ten seconds, centre to play or pause.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtworkCarousel(connection: PlayerConnection, side: androidx.compose.ui.unit.Dp) {
    val player = connection.player
    val index by connection.currentMediaItemIndex.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    LaunchedEffect(pagerState.settledPage) {
        when (pagerState.settledPage) {
            0 -> if (index > 0 || player.hasPreviousMediaItem()) connection.seekToPrevious()
            2 -> connection.seekToNext()
        }
    }
    LaunchedEffect(index) {
        if (pagerState.currentPage != 1) pagerState.scrollToPage(1)
    }

    fun itemAt(page: Int): androidx.media3.common.MediaItem? {
        val target = index + (page - 1)
        return runCatching { if (target in 0 until player.mediaItemCount) player.getMediaItemAt(target) else null }.getOrNull()
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.size(side),
        pageSpacing = 16.dp,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = when (PlayerSettings.swipeSensitivity) {
                SwipeSensitivity.Low -> 0.5f
                SwipeSensitivity.Regular -> 0.35f
                SwipeSensitivity.High -> 0.2f
            },
        ),
    ) { page ->
        val item = itemAt(page)
        val pageOffset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = 1f - pageOffset
                    val s = lerp(0.85f, 1f, t)
                    scaleX = s; scaleY = s
                    alpha = lerp(0.3f, 1f, t)
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val pos = connection.player.currentPosition
                            when {
                                offset.x < size.width / 3f -> connection.seekTo((pos - 10_000).coerceAtLeast(0))
                                offset.x > size.width * 2f / 3f -> connection.seekTo(pos + 10_000)
                                else -> connection.togglePlayPause()
                            }
                        },
                    )
                },
        ) {
            StyledArtwork(item?.mediaMetadata?.artworkUri?.toString(),
                item?.mediaMetadata?.title?.toString(), Modifier.fillMaxSize())
        }
    }
}
