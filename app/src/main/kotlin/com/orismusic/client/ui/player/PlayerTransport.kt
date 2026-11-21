package com.orismusic.client.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.PlayKey
import com.orismusic.client.settings.PlayerSettings
import com.orismusic.client.settings.SkipIcons

/** Title and artist. A new song slides the old text up and out and brings the new one in from below. */
@Composable
internal fun NowPlayingTitle(title: String, artist: String, modifier: Modifier = Modifier, onArtistClick: (() -> Unit)? = null) {
    val content: @Composable (Pair<String, String>) -> Unit = { (t, a) ->
        Column {
            Text(t, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(a, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.68f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (onArtistClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { onArtistClick() } else Modifier)
        }
    }
    if (!PlayerSettings.titleMotion) { Box(modifier) { content(title to artist) }; return }
    AnimatedContent(
        targetState = title to artist, modifier = modifier, label = "title",
        transitionSpec = {
            (slideInVertically(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                (slideOutVertically(tween(220)) { -it / 3 } + fadeOut(tween(180)))
        },
    ) { content(it) }
}

/**
 * Previous, play, next — nothing else. Shuffle and repeat live in the player tray instead of
 * flanking the transport, so the play key and the skip pair carry the whole row. Skip keys are
 * soft glass circles that lean the way they go when pressed. The play key is the accent colour,
 * squeezes under the finger and, while playing, settles into a rounded square (a circle when paused).
 */
@Composable
internal fun NowPlayingTransport(
    isPlaying: Boolean, canPrevious: Boolean, canNext: Boolean, accent: Color,
    onPrevious: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
) {
    val view = LocalView.current
    fun tick() { if (PlayerSettings.haptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    val size = PlayerSettings.size
    val skipIcons = PlayerSettings.icons
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy((size.skip / 2).dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        SkipKey(if (skipIcons == SkipIcons.Skip) Icons.Rounded.SkipPrevious else Icons.Rounded.FastRewind, "Previous", canPrevious, -1, size.skip) {
            tick(); onPrevious()
        }
        PlayKeyButton(isPlaying, accent, size.play) { tick(); onToggle() }
        SkipKey(if (skipIcons == SkipIcons.Skip) Icons.Rounded.SkipNext else Icons.Rounded.FastForward, "Next", canNext, 1, size.skip) {
            tick(); onNext()
        }
    }
}

@Composable
private fun SkipKey(icon: ImageVector, label: String, enabled: Boolean, direction: Int, box: Int, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.84f else 1f, spring(0.5f, 500f), label = "skip scale")
    val lean by animateFloatAsState(if (pressed) 7f * direction else 0f, spring(0.45f, 450f), label = "skip lean")
    Box(
        Modifier.size(box.dp).graphicsLayer { scaleX = scale; scaleY = scale; translationX = lean.dp.toPx() }
            .clickable(interactionSource = press, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size((box * 0.62f).dp),
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f))
    }
}

@Composable
private fun PlayKeyButton(isPlaying: Boolean, accent: Color, box: Int, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.5f, 500f), label = "play scale")
    val corner by animateDpAsState(
        if (PlayerSettings.morph && isPlaying) (box * 0.3f).dp else (box / 2f).dp,
        spring(Spring.DampingRatioLowBouncy, 260f), label = "play corner",
    )
    val shape = RoundedCornerShape(corner)
    val style = PlayerSettings.keys
    val container = when (style) {
        PlayKey.Glass -> Modifier.background(Color.White.copy(alpha = 0.16f), shape).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.26f)), shape)
        PlayKey.Solid -> Modifier.background(accent, shape)
        PlayKey.Plain -> Modifier
    }
    val tint = if (style == PlayKey.Solid) Color(0xFF1A1613) else Color.White
    Box(
        Modifier.size(box.dp).graphicsLayer { scaleX = scale; scaleY = scale }.then(container).clip(shape)
            .clickable(interactionSource = press, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(isPlaying, animationSpec = tween(180), label = "play icon") { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play", tint = tint,
                modifier = Modifier.size((box * if (style == PlayKey.Plain) 0.82f else 0.54f).dp),
            )
        }
    }
}
