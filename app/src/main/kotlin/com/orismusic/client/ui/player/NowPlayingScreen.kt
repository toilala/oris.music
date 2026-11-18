package com.orismusic.client.ui.player

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
) {
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    if (lyricsOpen) {
        LyricsPane(connection, onClose = { lyricsOpen = false })
        return
    }
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
            .background(Color.Black)
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.75f), tint.copy(alpha = 0.25f), Color.Black)))
            .statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState())
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
                    imageVector = Icons.Filled.KeyboardArrowDown,
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

        }

        Spacer(modifier = Modifier.height(16.dp))

        Crossfade(targetState = artworkUrl, label = "player artwork") { artwork ->
            com.orismusic.client.ui.components.Artwork(
                url = artwork,
                contentDescription = metadata?.title?.toString(),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .size(artSize)
                    .graphicsLayer { scaleX = artScale; scaleY = artScale }
                    .shadow(24.dp, RoundedCornerShape(28.dp)),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val appCtx = LocalContext.current
            remember { OfflineAudio.load(appCtx) }
            val savedSongs = OfflineAudio.downloaded.collectAsState().value
            val busy = OfflineAudio.inProgress.collectAsState().value
            val isSaved = nowSong != null && savedSongs.any { it.id == nowSong.id }
            val isBusy = nowSong != null && nowSong.id in busy
            IconButton(
                onClick = {
                    nowSong?.let { if (isSaved) OfflineAudio.remove(appCtx, it.id) else OfflineAudio.download(appCtx, it) }
                },
                enabled = nowSong != null && !isBusy,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        contentDescription = if (isSaved) "Remove offline copy" else "Save for offline",
                        tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            var playlistMenu by remember { mutableStateOf(false) }
            val playlistStore = PlaylistStore.get(LocalContext.current)
            val userPlaylists = playlistStore.playlists.collectAsState().value
            Box {
                IconButton(onClick = { playlistMenu = true }, enabled = nowSong != null) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist",
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
            var timerMenu by remember { mutableStateOf(false) }
            val timerEnd = SleepTimer.endsAt.collectAsState().value
            Box {
                IconButton(onClick = { timerMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
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
            IconButton(onClick = { nowSong?.let { library.toggleLike(it) } }, enabled = nowSong != null) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from liked songs" else "Like song",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { lyricsOpen = true }) { Text("Lyrics") }
        }

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
                    imageVector = Icons.Filled.Shuffle,
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
                    imageVector = Icons.Filled.SkipPrevious,
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
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Crossfade(isPlaying.value, label = "play icon") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
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
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(onClick = { connection.toggleRepeatMode() }) {
                Icon(
                    imageVector = if (repeat.value == Player.REPEAT_MODE_ONE) {
                        Icons.Filled.RepeatOne
                    } else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeat.value != Player.REPEAT_MODE_OFF) {
                        MaterialTheme.colorScheme.primary
                    } else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
