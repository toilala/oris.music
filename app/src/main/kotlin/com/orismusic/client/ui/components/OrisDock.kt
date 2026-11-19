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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact glass dock. One warm lens slides under the active tab. Its two edges
 * run on different springs, so it stretches toward the tab it is going to and
 * then snaps back, like a drop of light pulled across glass. The active icon
 * pops up and lights its label.
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
    val tabW = 74.dp
    val tabH = 54.dp
    val shape = RoundedCornerShape(30.dp)
    val warm = MaterialTheme.colorScheme.tertiary
    val none = selected < 0
    val selected = selected.coerceAtLeast(0)
    var previous by remember { mutableIntStateOf(selected) }
    val right = selected >= previous
    LaunchedEffect(selected) { previous = selected }
    val left by animateDpAsState(tabW * selected, spring(0.6f, if (right) 220f else 1000f), label = "lens left")
    val end by animateDpAsState(tabW * (selected + 1), spring(0.6f, if (right) 1000f else 220f), label = "lens right")
    Box(
        modifier
            .shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Color(0xFF1B1816).copy(alpha = 0.82f))
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f)))),
                shape,
            )
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer { alpha = if (none) 0f else 1f }
                .offset(x = left)
                .width((end - left).coerceAtLeast(20.dp))
                .height(tabH)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(listOf(warm.copy(alpha = 0.40f), Color.Transparent), center = center, radius = size.width * 0.9f),
                        radius = size.width * 0.9f, center = center,
                    )
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(warm, warm.copy(alpha = 0.78f)), Offset.Zero, Offset(300f, 100f)))
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(24.dp)),
        )
        Row {
            repeat(count) { i ->
                val active = i == selected && !none
                val pop by animateFloatAsState(if (active) 1.14f else 1f, spring(0.38f, 520f), label = "pop")
                val lift by animateDpAsState(if (active) (-2).dp else 0.dp, spring(0.5f, 500f), label = "lift")
                val tint by animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tint",
                )
                Column(
                    Modifier.width(tabW).height(tabH)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        icon(i), contentDescription = label(i), tint = tint,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop; scaleY = pop; translationY = lift.toPx() },
                    )
                    Text(label(i), color = tint, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
