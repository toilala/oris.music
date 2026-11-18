package com.orismusic.client.ui.player

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.PlaylistStore
import com.orismusic.client.library.currentSavedSong
import android.os.SystemClock
import com.orismusic.client.playback.OfflineAudio
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.SleepTimer
import com.orismusic.client.utils.formatTime

/**
 * Full now-playing screen: artwork, title/artist, draggable seek bar, and
 * transport controls (shuffle, previous, play/pause, next, repeat).
 * Genuine Oris code.
 */
@Composable
fun NowPlayingScreen(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onQueue: () -> Unit = {},
) {
    val look = com.orismusic.client.settings.AppearanceSettings
    val mode = look.backgroundMode
    val adaptive = look.adaptiveBackground
    val art = connection.currentMetadata.collectAsState().value?.artworkUri?.toString()
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    // Fetch lyrics as soon as a song starts so the Lyrics view opens already filled.
    val lyricContext = androidx.compose.ui.platform.LocalContext.current
    val lyricMeta = connection.currentMetadata.collectAsState().value
    val lyricDuration = connection.duration.collectAsState().value
    val lyricId = connection.player.currentMediaItem?.mediaId.orEmpty()
    androidx.compose.runtime.LaunchedEffect(lyricId, lyricMeta?.title, lyricDuration > 0) {
        val title = lyricMeta?.title?.toString().orEmpty()
        if (lyricId.isBlank() || title.isBlank() || lyricDuration <= 0) return@LaunchedEffect
        runCatching {
            com.orismusic.client.lyrics.LyricsRepository.load(
                lyricId, title, lyricMeta?.artist?.toString().orEmpty(), lyricMeta?.albumTitle?.toString().orEmpty(),
                (lyricDuration.coerceAtLeast(0) / 1000).toInt(), "automatic",
                com.orismusic.client.settings.LyricsPreferences(lyricContext).read(),
            )
        }
    }
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = com.orismusic.client.ui.components.rememberArtworkColor(art) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "player tint",
    )
    val base = MaterialTheme.colorScheme.background
    val k = look.intensity
    val floor = if (!adaptive || mode == com.orismusic.client.settings.BackgroundMode.Solid) base
        else androidx.compose.ui.graphics.lerp(base, tint, 0.38f * k)
    val heroBlur by androidx.compose.animation.core.animateDpAsState(if (lyricsOpen) 36.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(450), label = "hero blur")
    val heroAlpha by animateFloatAsState(if (lyricsOpen) 0.35f else 1f,
        animationSpec = androidx.compose.animation.core.tween(450), label = "hero alpha")
    Box(modifier.fillMaxSize().background(floor)) {
        if (adaptive && mode == com.orismusic.client.settings.BackgroundMode.Blur) {
            com.orismusic.client.ui.components.Artwork(
                art,
                Modifier.fillMaxSize().blur(60.dp).graphicsLayer { alpha = 0.2f + 0.5f * k },
                androidx.compose.ui.graphics.RectangleShape,
            )
        }
        // Full-bleed artwork that fades into the background below it.
        Crossfade(targetState = art, label = "hero art", modifier = Modifier.fillMaxWidth().aspectRatio(1f).align(Alignment.TopCenter)) { url ->
            com.orismusic.client.ui.components.Artwork(
                url,
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = heroAlpha; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .blur(heroBlur)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0.8f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
                androidx.compose.ui.graphics.RectangleShape,
                null,
            )
        }
        // Soft shade at the top so the status bar and close button stay readable.
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        NowPlayingContent(connection, onCollapse, onQueue, lyricsOpen, setLyricsOpen = { lyricsOpen = it })
    }
}

@Composable
private fun NowPlayingContent(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    lyricsOpen: Boolean,
    setLyricsOpen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = connection.currentMetadata.collectAsState().value
    val isPlaying = connection.isPlaying.collectAsState()
    val duration = connection.duration.collectAsState()
    val position = connection.currentPosition.collectAsState()
    val shuffle = connection.shuffleModeEnabled.collectAsState()
    val repeat = connection.repeatMode.collectAsState()
    val canSkipPrevious = connection.canSkipPrevious.collectAsState()
    val canSkipNext = connection.canSkipNext.collectAsState()

    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val library = LibraryStore.get(LocalContext.current)
    val likedSongs = library.liked.collectAsState().value
    val currentIndex = connection.currentMediaItemIndex.collectAsState().value
    val nowSong = remember(metadata, currentIndex) { connection.currentSavedSong() }
    val isLiked = nowSong != null && likedSongs.any { it.id == nowSong.id }

    val artworkUrl = metadata?.artworkUri?.toString()
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = com.orismusic.client.ui.components.rememberArtworkColor(artworkUrl) ?: Color(0xFF202020),
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "player tint",
    )
    // Sized from the screen height so the controls stay on screen without scrolling.
    val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    val artSize = minOf(360, (screenHeightDp * 0.34f).toInt()).dp
    val artScale by animateFloatAsState(
        targetValue = if (isPlaying.value) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 180f),
        label = "art scale",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close player",
                )
            }
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            val shareContext = androidx.compose.ui.platform.LocalContext.current
            IconButton(onClick = {
                val id = connection.player.currentMediaItem?.mediaId.orEmpty()
                if (id.isNotBlank()) runCatching {
                    val text = (metadata?.title?.toString().orEmpty() + " https://music.youtube.com/watch?v=" + id).trim()
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
                    }
                    shareContext.startActivity(android.content.Intent.createChooser(send, "Share song")
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }) {
                Icon(imageVector = Icons.Rounded.Share, contentDescription = "Share song")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = lyricsOpen,
                enter = fadeIn(androidx.compose.animation.core.tween(350)),
                exit = fadeOut(androidx.compose.animation.core.tween(250)),
            ) {
                LyricsPane(connection, onClose = { setLyricsOpen(false) }, embedded = true)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
        Text(
            text = metadata?.title?.toString().orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = metadata?.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
            }
            IconButton(onClick = { nowSong?.let { library.toggleLike(it) } }, enabled = nowSong != null) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from liked songs" else "Like song",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Spacer(modifier = Modifier.height(16.dp))

        val durationMs = duration.value.coerceAtLeast(0L)
        val shownPosition = dragPosition?.toLong() ?: position.value
        Slider(
            value = if (durationMs > 0) {
                (shownPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            onValueChange = { fraction ->
                if (durationMs > 0) dragPosition = fraction * durationMs
            },
            onValueChangeFinished = {
                dragPosition?.let { connection.seekTo(it.toLong()) }
                dragPosition = null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatTime(shownPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { connection.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffle.value) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { connection.seekToPrevious() },
                enabled = canSkipPrevious.value,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                )
            }
            val playPress = remember { MutableInteractionSource() }
            val pressed by playPress.collectIsPressedAsState()
            val playScale by animateFloatAsState(if (pressed) 0.9f else 1f,
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f), label = "play press")
            FilledIconButton(
                interactionSource = playPress,
                onClick = { if (isPlaying.value) connection.pause() else connection.play() },
                modifier = Modifier.size(76.dp).graphicsLayer { scaleX = playScale; scaleY = playScale },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.background),
            ) {
                Crossfade(isPlaying.value, label = "play icon") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            IconButton(
                onClick = { connection.seekToNext() },
                enabled = canSkipNext.value,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { connection.toggleRepeatMode() }) {
                Icon(
                    imageVector = if (repeat.value == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat.value != Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var playlistMenu by remember { mutableStateOf(false) }
            val playlistStore = PlaylistStore.get(LocalContext.current)
            val userPlaylists = playlistStore.playlists.collectAsState().value
            Box {
                IconButton(onClick = { playlistMenu = true }, enabled = nowSong != null) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                    if (userPlaylists.isEmpty()) {
                        DropdownMenuItem(text = { Text("Make a playlist in Library first") },
                            onClick = { playlistMenu = false })
                    }
                    userPlaylists.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = { nowSong?.let { playlistStore.add(p.id, it) }; playlistMenu = false },
                        )
                    }
                }
            }
            if (com.orismusic.client.settings.AppearanceSettings.sleepTimerButton) {
            var timerMenu by remember { mutableStateOf(false) }
            val timerEnd = SleepTimer.endsAt.collectAsState().value
            Box {
                IconButton(onClick = { timerMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = "Sleep timer",
                        tint = if (timerEnd != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = timerMenu, onDismissRequest = { timerMenu = false }) {
                    if (timerEnd != null) {
                        val left = ((timerEnd - SystemClock.elapsedRealtime()) / 60_000L + 1).coerceAtLeast(1)
                        DropdownMenuItem(
                            text = { Text("Turn off timer ($left min left)") },
                            onClick = { SleepTimer.cancel(); timerMenu = false },
                        )
                    }
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        DropdownMenuItem(
                            text = { Text("Stop after $minutes min") },
                            onClick = {
                                SleepTimer.start(minutes) { connection.pause() }
                                timerMenu = false
                            },
                        )
                    }
                }
            }
            }
            IconButton(onClick = onQueue) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Open queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { setLyricsOpen(!lyricsOpen) }) {
                Text("Lyrics", color = if (lyricsOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }

    }
}
