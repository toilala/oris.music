package com.orismusic.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orismusic.client.settings.NavAccent
import com.orismusic.client.settings.NavEffect
import com.orismusic.client.settings.NavSettings
import com.orismusic.client.settings.NavStyle

/**
 * Compact dock. The look comes from NavSettings: glass, solid or clear body, a stretching
 * lens, a plain slide or a small dot for the active tab, three accents, sizes and spacing.
 * The Stretch lens runs its two edges on different springs so it pulls toward the next tab.
 */
@Composable
fun OrisDock(
    count: Int,
    selected: Int,
    label: (Int) -> String,
    icon: (Int) -> ImageVector,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showLabels = NavSettings.labels
    val effect = NavSettings.effect
    val tabH = (if (showLabels) 54 else 50).dp + NavSettings.size.extra.dp
    // Anchored bar: flush to the screen edges with only the top corners eased, so it
    // reads as the floor of the page instead of a second floating pill.
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val lensShape = RoundedCornerShape(14.dp)
    val scheme = MaterialTheme.colorScheme
    val (accent, onAccent) = when (NavSettings.accent) {
        NavAccent.Warm -> scheme.tertiary to scheme.onTertiary
        NavAccent.Light -> if (scheme.background.luminance() < 0.5f)
            Color(0xFFEDE6E0) to Color(0xFF1B1816) else Color(0xFF2A2521) to Color(0xFFFAF6F0)
        NavAccent.Primary -> scheme.primary to scheme.onPrimary
    }
    val dark = scheme.background.luminance() < 0.5f
    val body = when (NavSettings.style) {
        NavStyle.Glass -> scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.82f else 0.94f)
        NavStyle.Solid -> scheme.surfaceContainerHigh
        NavStyle.Clear -> scheme.surfaceContainerHigh.copy(alpha = 0.45f)
    }
    val none = selected < 0
    val sel = selected.coerceAtLeast(0)
    var previous by remember { mutableIntStateOf(sel) }
    val right = sel >= previous
    LaunchedEffect(sel) { previous = sel }
    val stretch = effect == NavEffect.Stretch
    val haptic = LocalHapticFeedback.current
    // A grounded slab, not a second floating pill: the mini player floats, the dock anchors.
    // Tabs share the full width; the width setting shapes how wide the lens sits in its tab.
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier.fillMaxWidth(),
    ) {
        val tabW = maxWidth / count
        val lensInset = (16 - NavSettings.width.extra / 4).dp
        val left by animateDpAsState(tabW * sel, spring(0.6f, if (stretch && right) 220f else if (stretch) 1000f else 420f), label = "lens left")
        val end by animateDpAsState(tabW * (sel + 1), spring(0.6f, if (stretch && right) 1000f else if (stretch) 220f else 420f), label = "lens right")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(body)
            .border(
                BorderStroke(
                    1.dp,
                    if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
                    else Brush.verticalGradient(listOf(scheme.surfaceContainerLowest.copy(alpha = 0.9f), scheme.outlineVariant.copy(alpha = 0.5f))),
                ),
                shape,
            )
            .padding(4.dp),
    ) {
        if (effect != NavEffect.Dot) {
            Box(
                Modifier
                    .graphicsLayer { alpha = if (none) 0f else 1f }
                    .offset(x = left + lensInset, y = 7.dp)
                    .width((end - left - lensInset * 2).coerceAtLeast(20.dp))
                    .height(tabH - 14.dp)
                    .clip(lensShape)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.78f)), Offset.Zero, Offset(300f, 100f)))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), lensShape),
            )
        } else if (!none) {
            Box(
                Modifier.offset(x = left + (tabW - 6.dp) / 2, y = tabH - 7.dp).size(6.dp).clip(CircleShape).background(accent),
            )
        }
        Row {
            repeat(count) { i ->
                val active = i == sel && !none
                val pop by animateFloatAsState(if (active) 1.08f else 1f, spring(0.38f, 520f), label = "pop")
                // A finger on a tab dips the icon, so taps answer before the lens arrives.
                val pressSrc = remember { MutableInteractionSource() }
                val pressed by pressSrc.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (pressed) 0.82f else 1f, spring(0.5f, 600f), label = "tab press")
                val activeTint = if (effect == NavEffect.Dot) accent else onAccent
                val tint by animateColorAsState(if (active) activeTint else scheme.onSurfaceVariant, label = "tint")
                Column(
                    Modifier.width(tabW).height(tabH)
                        .clickable(interactionSource = pressSrc, indication = null) {
                            if (NavSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon(i), contentDescription = label(i), tint = tint,
                        modifier = Modifier.size(NavSettings.size.icon.dp).graphicsLayer { scaleX = pop * pressScale; scaleY = pop * pressScale },
                    )
                    if (showLabels) Text(label(i), color = tint, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}
}
