package com.orismusic.client.ui.components

import com.orismusic.client.ui.theme.OrisMotion
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
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
    tint: Color? = null,
    embedded: Boolean = false,
) {
    val showLabels = NavSettings.labels
    val effect = NavSettings.effect
    val tabH = (if (showLabels) 54 else 50).dp + NavSettings.size.extra.dp
    // Floating pill: hugs its tabs and rounds fully, so it reads as one stack with the
    // mini player above it instead of a second anchored bar.
    val shape = CircleShape
    val lensShape = CircleShape
    val scheme = MaterialTheme.colorScheme
    val (accent, onAccent) = when (NavSettings.accent) {
        NavAccent.Warm -> scheme.tertiary to scheme.onTertiary
        NavAccent.Light -> if (scheme.background.luminance() < 0.5f)
            Color(0xFFEDE6E0) to Color(0xFF1B1816) else Color(0xFF2A2521) to Color(0xFFFAF6F0)
        NavAccent.Primary -> scheme.primary to scheme.onPrimary
    }
    val dark = scheme.background.luminance() < 0.5f
    val bodyBase = when (NavSettings.style) {
        NavStyle.Glass -> scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.82f else 0.94f)
        NavStyle.Solid -> scheme.surfaceContainerHigh
        NavStyle.Clear -> scheme.surfaceContainerHigh.copy(alpha = 0.45f)
    }
    // While a song is loaded the dock wears the same artwork colour as the mini player
    // above it, a step quieter, so the stack reads as one set instead of two stray pills.
    val body by animateColorAsState(
        tint?.let { androidx.compose.ui.graphics.lerp(bodyBase, it, 0.24f) } ?: bodyBase,
        OrisMotion.slow(), label = "dock body",
    )
    val none = selected < 0
    val sel = selected.coerceAtLeast(0)
    var previous by remember { mutableIntStateOf(sel) }
    val right = sel >= previous
    LaunchedEffect(sel) { previous = sel }
    val stretch = effect == NavEffect.Stretch
    val haptic = LocalHapticFeedback.current
    // Compact tabs: each is just wide enough for its icon (and label), the width setting
    // still shapes how wide the lens sits in its tab. Embedded in the dock cluster the
    // tabs instead share the cluster's full width, so the nav edges align with the
    // mini player above them.
    val tabW = (if (showLabels) 76 else 60).dp + (NavSettings.size.extra / 2).dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    var measuredW by remember { mutableStateOf(0.dp) }
    val effTabW = if (embedded && measuredW > 0.dp) (measuredW - 8.dp) / count else tabW
    Box(
        (if (embedded) modifier.fillMaxWidth() else modifier.width(tabW * count + 8.dp))
            .then(
                if (embedded) Modifier.onGloballyPositioned { measuredW = with(density) { it.size.width.toDp() } }
                else Modifier,
            ),
    ) {
        val lensInset = (12 - NavSettings.width.extra / 4).dp
        val left by animateDpAsState(effTabW * sel, spring(0.6f, if (stretch && right) 220f else if (stretch) 1000f else 420f), label = "lens left")
        val end by animateDpAsState(effTabW * (sel + 1), spring(0.6f, if (stretch && right) 1000f else if (stretch) 220f else 420f), label = "lens right")
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (embedded) Modifier
                else Modifier
                    .clip(shape)
                    .background(body)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (dark) Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)))
                            else Brush.verticalGradient(listOf(scheme.surfaceContainerLowest.copy(alpha = 0.9f), scheme.outlineVariant.copy(alpha = 0.5f))),
                        ),
                        shape,
                    ),
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
                Modifier.offset(x = left + (effTabW - 6.dp) / 2, y = tabH - 7.dp).size(6.dp).clip(CircleShape).background(accent),
            )
        }
        Row {
            repeat(count) { i ->
                val active = i == sel && !none
                val pop by animateFloatAsState(if (active) 1.08f else 1f, OrisMotion.press(), label = "pop")
                // A finger on a tab dips the icon, so taps answer before the lens arrives.
                val pressSrc = remember { MutableInteractionSource() }
                val pressed by pressSrc.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (pressed) 0.82f else 1f, OrisMotion.press(), label = "tab press")
                val activeTint = if (effect == NavEffect.Dot) accent else onAccent
                val tint by animateColorAsState(if (active) activeTint else scheme.onSurfaceVariant, label = "tint")
                Column(
                    Modifier.width(effTabW).height(tabH)
                        // The tab itself carries the accessible name: a clickable control must
                        // not depend on a child icon's node reaching the a11y tree.
                        .semantics { contentDescription = label(i) }
                        .clickable(interactionSource = pressSrc, indication = null) {
                            if (NavSettings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(i)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        icon(i), contentDescription = null, tint = tint,
                        modifier = Modifier.size(NavSettings.size.icon.dp).graphicsLayer { scaleX = pop * pressScale; scaleY = pop * pressScale },
                    )
                    if (showLabels) Text(label(i), color = tint, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}
}
