package com.orismusic.client.ui.player

/** Wave seek bar flavours. The player uses Wave; the rest are kept for the settings preview. */

import com.orismusic.client.ui.theme.OrisMotion
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
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

enum class SeekStyle(val label: String) { Wave("Wave"), Line("Line"), Bars("Bars") }

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

    val style = SeekStyle.Wave
    val view = androidx.compose.ui.platform.LocalView.current
    val amp by animateFloatAsState(if (isPlaying && !dragging && style == SeekStyle.Wave) 1f else 0f, OrisMotion.slow(), label = "wave amp")
    val thumb by animateFloatAsState(if (dragging) 34f else 22f, OrisMotion.settle(), label = "thumb")
    val phaseAnim = rememberInfiniteTransition(label = "wave phase")
    val phase by phaseAnim.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(OrisMotion.WavePeriod, easing = LinearEasing), RepeatMode.Restart),
        label = "wave phase value",
    )
    val track = PlayerTextButtonColor.copy(alpha = 0.28f)
    val shownSec by remember(durationMs) {
        derivedStateOf { (((if (dragging) dragFrac else progress) * durationMs) / 1000f).toLong() }
    }

    val btnText = PlayerTextButtonColor
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
                        onDragStart = { o -> if (com.orismusic.client.settings.PlayerSettings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onHorizontalDrag = { change, _ ->
                            dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        },
                        onDragEnd = {
                            if (durationMs > 0) onSeek((dragFrac * durationMs).toLong())
                            if (com.orismusic.client.settings.PlayerSettings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
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
            if (style == SeekStyle.Bars) {
                val gap = 3.dp.toPx(); val bw = 3.dp.toPx()
                val n = (w / (bw + gap)).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    val bx = i * (bw + gap) + bw / 2f
                    // shape only: a fixed pattern, it is not the song's real waveform
                    val hh = (0.35f + 0.65f * kotlin.math.abs(sin(i * 0.37f) * sin(i * 0.11f + 1f))) * size.height * 0.8f
                    drawLine(if (bx <= x) accent else track, Offset(bx, cy - hh / 2f), Offset(bx, cy + hh / 2f), bw, StrokeCap.Round)
                }
                val th = thumb.dp.toPx()
                drawRoundRect(btnText, topLeft = Offset(x - 1.5.dp.toPx(), cy - th / 2f), size = Size(3.dp.toPx(), th), cornerRadius = CornerRadius(1.5.dp.toPx()))
                return@Canvas
            }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val big by animateFloatAsState(if (dragging) 1.3f else 1f, OrisMotion.settle(), label = "time pop")
            Text(
                if (durationMs > 0) formatTime(shownSec * 1000L) else "--:--", style = MaterialTheme.typography.labelSmall,
                color = if (dragging) accent else PlayerTextButtonColor.copy(alpha = 0.75f),
                modifier = Modifier.graphicsLayer { scaleX = big; scaleY = big; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            val total = durationMs.coerceAtLeast(0L)
            Text(
                if (total <= 0) "--:--" else if (com.orismusic.client.settings.PlayerSettings.remaining) "-" + formatTime((total - shownSec * 1000L).coerceAtLeast(0L)) else formatTime(total),
                style = MaterialTheme.typography.labelSmall, color = PlayerTextButtonColor.copy(alpha = 0.75f),
            )
        }
    }
}


/** Static sample of the seek bar styles for the settings preview. Tap or drag to move it. */
@Composable
fun SeekPreview(progress: Float, accent: Color, onChange: (Float) -> Unit) {
    val style = SeekStyle.Wave
    val phaseAnim = rememberInfiniteTransition(label = "preview phase")
    val phase by phaseAnim.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(OrisMotion.WavePeriod, easing = LinearEasing), RepeatMode.Restart), label = "preview phase value",
    )
    val previewInk = PlayerTextButtonColor
    Canvas(
        Modifier.fillMaxWidth().height(40.dp)
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> c.consume(); onChange((c.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val w = size.width; val cy = size.height / 2f; val x = progress * w
        val track = previewInk.copy(alpha = 0.28f)
        if (style == SeekStyle.Bars) {
            val gap = 3.dp.toPx(); val bw = 3.dp.toPx()
            val n = (w / (bw + gap)).toInt().coerceAtLeast(1)
            for (i in 0 until n) {
                val bx = i * (bw + gap) + bw / 2f
                val hh = (0.35f + 0.65f * kotlin.math.abs(sin(i * 0.37f) * sin(i * 0.11f + 1f))) * size.height * 0.8f
                drawLine(if (bx <= x) accent else track, Offset(bx, cy - hh / 2f), Offset(bx, cy + hh / 2f), bw, StrokeCap.Round)
            }
            return@Canvas
        }
        val sw = 5.dp.toPx()
        drawLine(track, Offset(x, cy), Offset(w, cy), sw, StrokeCap.Round)
        val a = if (style == SeekStyle.Wave) 2.2.dp.toPx() else 0f
        val path = Path(); var px = 0f
        path.moveTo(0f, cy + a * sin(-phase))
        while (px < x) { px = (px + 3.dp.toPx()).coerceAtMost(x); path.lineTo(px, cy + a * sin(px / 26.dp.toPx() * 2f * PI.toFloat() - phase)) }
        drawPath(path, accent, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawRoundRect(accent, topLeft = Offset(x - 2.5.dp.toPx(), cy - 11.dp.toPx()), size = Size(5.dp.toPx(), 22.dp.toPx()), cornerRadius = CornerRadius(2.5.dp.toPx()))
    }
}
