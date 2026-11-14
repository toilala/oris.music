package com.orismusic.client.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.utils.formatTime

@Composable
fun PlayerControls(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    connection: PlayerConnection,
) {
    val playbackState = connection.playbackState.collectAsState()
    val isPlaying = connection.isPlaying.collectAsState()
    val currentPosition = connection.currentPosition.collectAsState()
    val duration = connection.duration.collectAsState()
    val currentMetadata = connection.currentMetadata.collectAsState()
    val canSkipNext = connection.canSkipNext.collectAsState()
    val canSkipPrevious = connection.canSkipPrevious.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        // Song info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp)
        ) {
            currentMetadata.value?.let { metadata ->
                Text(
                    text = (metadata.title ?: "Unknown Title").toString(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = (metadata.artist ?: "Unknown Artist").toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress slider
        PlayerProgressSlider(
            currentPosition = currentPosition.value,
            duration = duration.value,
            onSeek = { connection.seekTo(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Transport controls
        PlayerTransportRow(
            controller = connection,
            isPlaying = isPlaying.value,
            canSkipNext = canSkipNext.value,
            canSkipPrevious = canSkipPrevious.value,
        )
    }
}

@Composable
fun PlayerProgressSlider(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
) {
    val progress = remember { mutableStateOf(0f) }
    val isDragging = remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Slider(
            modifier = Modifier.fillMaxWidth(),
            value = if (isDragging.value) progress.value else {
                if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            },
            onValueChange = { newValue ->
                progress.value = newValue
                isDragging.value = true
            },
            onValueChangeFinished = {
                isDragging.value = false
                if (duration > 0) {
                    val targetPosition = (progress.value * duration).toLong()
                    onSeek(targetPosition)
                }
            },
            interactionSource = remember { MutableInteractionSource() },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        )

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlayerTransportRow(
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    controller: PlayerConnection,
    isPlaying: Boolean,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
) {
    val buttonSize = 64.dp
    val iconSize = 28.dp
    val playButtonSize = 72.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp)
    ) {
        // Previous
        IconButton(
            onClick = { controller.seekToPrevious() },
            enabled = canSkipPrevious,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.NavigateBefore,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }

        // Play/Pause
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(playButtonSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(
                    onClick = {
                        if (isPlaying) {
                            controller.pause()
                        } else {
                            controller.play()
                        }
                    }
                )
        ) {
            if (isPlaying) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Pause",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize * 1.2f),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(iconSize * 1.2f),
                )
            }
        }

        // Next
        IconButton(
            onClick = { controller.seekToNext() },
            enabled = canSkipNext,
            modifier = Modifier.size(buttonSize)
        ) {
            Icon(
                imageVector = Icons.Filled.NavigateNext,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}