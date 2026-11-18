package com.orismusic.client.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.components.rememberArtworkColor

/**
 * Compact player bar above the bottom navigation. It wears a tint of the current
 * artwork, shows progress along its bottom edge and opens the full player on tap.
 */
@Composable
fun MiniPlayer(
    connection: PlayerConnection,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = connection.currentMetadata.collectAsState().value ?: return
    val isPlaying = connection.isPlaying.collectAsState()
    val position = connection.currentPosition.collectAsState().value
    val duration = connection.duration.collectAsState().value
    val artwork = metadata.artworkUri?.toString()
    val tint = rememberArtworkColor(artwork) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val progress by animateFloatAsState(
        targetValue = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(400),
        label = "mini progress",
    )
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.55f).compositeOver(base), base)))
            .pressClickable(onExpand, 0.985f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(artwork, Modifier.size(46.dp), androidx.compose.foundation.shape.CircleShape, metadata.title?.toString())
            Spacer(modifier = Modifier.width(12.dp))
            AnimatedContent(
                targetState = metadata.title?.toString().orEmpty() to metadata.artist?.toString().orEmpty(),
                transitionSpec = {
                    (slideInVertically(tween(280)) { it / 3 } + fadeIn(tween(280)))
                        .togetherWith(slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(160)))
                },
                label = "mini title",
                modifier = Modifier.weight(1f),
            ) { (title, artist) ->
                Column {
                    Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { if (isPlaying.value) connection.pause() else connection.play() }) {
                Crossfade(isPlaying.value, label = "mini play") { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            IconButton(onClick = { connection.seekToNext() }) {
                Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.12f)),
        ) {
            Box(Modifier.fillMaxWidth(progress).height(3.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        }
    }
}
