package com.orismusic.client.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.orismusic.client.utils.formatTime
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Seek bar that moves every frame instead of once a second. The played part is a soft wave that
 * flows while the song plays and settles into a straight line when paused or while you drag.
 * Written for Oris.
 */
@Composable
fun WaveSeekBar(
    player: Player,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, durationMs, dragging) {
        if (dragging) return@LaunchedEffect
        while (true) {
            if (durationMs > 0) {
                progress = (runCatching { player.currentPosition }.getOrDefault(0L).toFloat() / durationMs).coerceIn(0f, 1f)
            }
            if (isPlaying) withFrameNanos { } else delay(250)
        }
    }

    val amp by animateFloatAsState(if (isPlaying && !dragging) 1f else 0f, tween(600), label = "wave amp")
    val thumb by animateFloatAsState(if (dragging) 34f else 22f, spring(dampingRatio = 0.6f, stiffness = 400f), label = "thumb")
    val phaseAnim = rememberInfiniteTransition(label = "wave phase")
    val phase by phaseAnim.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "wave phase value",
    )
    val track = Color.White.copy(alpha = 0.28f)
    val shownSec by remember(durationMs) {
        derivedStateOf { (((if (dragging) dragFrac else progress) * durationMs) / 1000f).toLong() }
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(44.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o ->
                        if (durationMs > 0) {
                            val f = (o.x / size.width).coerceIn(0f, 1f)
                            progress = f
                            onSeek((f * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            if (durationMs > 0) onSeek((dragFrac * durationMs).toLong())
                            progress = dragFrac
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    )
                },
        ) {
            val w = size.width
            val cy = size.height / 2f
            val x = (if (dragging) dragFrac else progress) * w
            val strokeW = 5.dp.toPx()
            drawLine(track, Offset(x, cy), Offset(w, cy), strokeW, StrokeCap.Round)
            val a = 2.2.dp.toPx() * amp
            val wavelength = 26.dp.toPx()
            val step = 3.dp.toPx()
            val path = Path()
            var px = 0f
            path.moveTo(0f, cy + a * sin(-phase))
            while (px < x) {
                px = (px + step).coerceAtMost(x)
                path.lineTo(px, cy + a * sin(px / wavelength * 2f * PI.toFloat() - phase))
            }
            drawPath(path, accent, style = Stroke(strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val th = thumb.dp.toPx()
            drawRoundRect(
                accent,
                topLeft = Offset(x - 2.5.dp.toPx(), cy - th / 2f),
                size = Size(5.dp.toPx(), th),
                cornerRadius = CornerRadius(2.5.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shownSec * 1000L), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(formatTime(durationMs.coerceAtLeast(0L)), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}
