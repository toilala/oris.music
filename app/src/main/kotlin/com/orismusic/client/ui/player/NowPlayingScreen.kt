package com.orismusic.client.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import com.orismusic.client.playback.PlayerConnection
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
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
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Now playing",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { lyricsOpen = true }) { Text("Lyrics") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AsyncImage(
            model = metadata?.artworkUri?.toString(),
            contentDescription = metadata?.title?.toString(),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = metadata?.title?.toString().orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = metadata?.artist?.toString().orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

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
            FilledIconButton(
                onClick = { if (isPlaying.value) connection.pause() else connection.play() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(),
            ) {
                Icon(
                    imageVector = if (isPlaying.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying.value) "Pause" else "Play",
                    modifier = Modifier.size(36.dp),
                )
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
