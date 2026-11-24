package com.orismusic.client.ui.player

/*
 * Compact player bar. Its pill shape, progress-ring thumbnail, marquee text and wavy mini
 * play button follow the Echo Music app's new mini player (GPL-3.0,
 * https://github.com/EchoMusicApp/Echo-Music). All code here is Oris's own.
 */

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.pressClickable
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    connection: PlayerConnection,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = connection.currentMetadata.collectAsState().value ?: return
    val scope = rememberCoroutineScope()
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    var dismissed by remember { mutableStateOf(false) }
    val skipThreshold = when (com.orismusic.client.settings.PlayerSettings.swipeSensitivity) {
        com.orismusic.client.settings.SwipeSensitivity.Low -> 220f
        com.orismusic.client.settings.SwipeSensitivity.Regular -> 150f
        com.orismusic.client.settings.SwipeSensitivity.High -> 95f
    }
    val isPlaying by connection.isPlaying.collectAsState()
    val playbackState by connection.playbackState.collectAsState()
    LaunchedEffect(isPlaying) { if (isPlaying) dismissed = false }
    if (dismissed) return
    val position by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    val error by connection.error.collectAsState()
    val diagFailure by com.orismusic.client.utils.PlaybackDiagnostic.activeFailure.collectAsState()
    val artwork = metadata.artworkUri?.toString()

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    // The pill wears the song: its body and progress ring lean into the artwork's colour,
    // a little richer while the music is actually playing.
    val artworkTint = com.orismusic.client.ui.components.rememberArtworkColor(artwork)
    val pillColor by androidx.compose.animation.animateColorAsState(
        targetValue = artworkTint?.let {
            androidx.compose.ui.graphics.lerp(
                MaterialTheme.colorScheme.surfaceContainerHigh, it, if (isPlaying) 0.40f else 0.26f,
            )
        } ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(700), label = "mini pill colour",
    )
    val ringColor by androidx.compose.animation.animateColorAsState(
        targetValue = artworkTint?.let { androidx.compose.ui.graphics.lerp(primary, it, 0.65f) } ?: primary,
        animationSpec = tween(700), label = "mini ring colour",
    )

    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .graphicsLayer {
                translationX = offX.value; translationY = offY.value
                alpha = 1f - minOf(0.85f, abs(offX.value) / 700f + offY.value.coerceAtLeast(0f) / 350f)
            }
            .pointerInput(connection) {
                detectDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val x = offX.value; val y = offY.value
                            when {
                                x > skipThreshold -> { offX.animateTo(900f, tween(140)); connection.seekToPrevious(); offX.snapTo(-900f); offX.animateTo(0f, spring(0.7f, 300f)) }
                                x < -skipThreshold -> { offX.animateTo(-900f, tween(140)); connection.seekToNext(); offX.snapTo(900f); offX.animateTo(0f, spring(0.7f, 300f)) }
                                y < -90f -> { launch { offY.animateTo(0f, spring(0.75f, 320f)) }; onExpand() }
                                y > 100f -> { offY.animateTo(500f, tween(160)); connection.pause(); dismissed = true; offY.snapTo(0f) }
                                else -> { launch { offX.animateTo(0f, spring(0.6f, 400f)) }; offY.animateTo(0f, spring(0.6f, 400f)) }
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offX.snapTo(0f); offY.snapTo(0f) } },
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch {
                            if (abs(drag.x) >= abs(drag.y) && abs(offY.value) < 8f) offX.snapTo(offX.value + drag.x)
                            else offY.snapTo((offY.value + drag.y).coerceAtLeast(-260f))
                        }
                    },
                )
            }
            .heightIn(min = 64.dp)
            .clip(pillShape)
            .background(pillColor)
            .border(1.dp, outline.copy(alpha = 0.3f), pillShape)
            .pressClickable(onExpand, 0.985f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Artwork with a progress ring around it.
            val progress by animateFloatAsState(
                targetValue = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                animationSpec = tween(400),
                label = "mini progress",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).drawWithContent {
                    drawContent()
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val diameter = size.minDimension
                    val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                    drawArc(outline.copy(alpha = 0.2f), 0f, 360f, false, topLeft, Size(diameter, diameter), style = stroke)
                    drawArc(ringColor, -90f, 360f * progress, false, topLeft, Size(diameter, diameter), style = stroke)
                },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape).border(1.dp, outline.copy(alpha = 0.3f), CircleShape),
                ) {
                    Artwork(artwork, Modifier.size(40.dp), CircleShape, metadata.title?.toString(), px = 200)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    metadata.title?.toString().orEmpty(),
                    color = onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                )
                Text(
                    metadata.artist?.toString().orEmpty(),
                    color = onSurface.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .basicMarquee(iterations = 1, initialDelayMillis = 3000, velocity = 30.dp),
                )
                androidx.compose.animation.AnimatedVisibility(visible = error != null) {
                    Text(
                        // Same human voice as the player banner, first sentence only:
                        // the pill has room for the problem, the banner carries the advice.
                        diagFailure?.let { com.orismusic.client.utils.diagnosticSummary(it).substringBefore('.') + '.' }
                            ?: "Playback error - skipping may fix it",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            val canSkipPrevious by connection.canSkipPrevious.collectAsState()
            val canSkipNext by connection.canSkipNext.collectAsState()
            IconButton(
                enabled = canSkipPrevious,
                onClick = { connection.seekToPrevious() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(20.dp), tint = onSurface)
            }
            Spacer(Modifier.width(12.dp))
            // Mini orbit play button, matching the full player's disc and satellite dot.
            val ringAlpha by animateFloatAsState(if (isPlaying) 1f else 0f, tween(300, easing = LinearEasing), label = "mini orbit ring")
            val spin = rememberInfiniteTransition(label = "mini orbit spin")
            val degrees by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "mini orbit degrees")
            val ended = playbackState == Player.STATE_ENDED
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable {
                    if (ended) {
                        connection.player.seekTo(0, 0)
                        connection.player.playWhenReady = true
                    } else connection.togglePlayPause()
                },
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(primary))
                if (ringAlpha > 0.01f) {
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val radius = size.minDimension / 2f - 1.5.dp.toPx()
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            onSurface.copy(alpha = 0.35f * ringAlpha),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
                        )
                        val rad = Math.toRadians(degrees.toDouble())
                        drawCircle(
                            primary.copy(alpha = ringAlpha),
                            radius = 2.4.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(
                                (center.x + radius * kotlin.math.cos(rad)).toFloat(),
                                (center.y + radius * kotlin.math.sin(rad)).toFloat(),
                            ),
                        )
                    }
                }
                Crossfade(isPlaying || ended, label = "mini play") { active ->
                    Icon(
                        imageVector = if (ended) Icons.Rounded.Replay else if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (ended) "Replay" else if (active) "Pause" else "Play",
                        tint = onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                enabled = canSkipNext,
                onClick = { connection.seekToNext() },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(20.dp), tint = onSurface)
            }
        }
    }
}
