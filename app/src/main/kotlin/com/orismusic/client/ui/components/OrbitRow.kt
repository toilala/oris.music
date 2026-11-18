package com.orismusic.client.ui.components

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A row whose cards ride an arc: the card in the middle sits tall and bright,
 * the ones toward the edges sink, shrink, fade and turn away. Scrolling snaps
 * card to card.
 */
@Composable
fun <T> OrbitRow(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    content: @Composable (item: T, index: Int, orbit: Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    LazyRow(
        modifier = modifier,
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items = items, key = { _, it -> key(it) }) { index, item ->
            content(item, index, Modifier.orbit(state, index))
        }
    }
}

private fun Modifier.orbit(state: LazyListState, index: Int): Modifier = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val width = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val center = info.viewportStartOffset + width / 2f
    val d = (((item.offset + item.size / 2f) - center) / width).coerceIn(-1f, 1f)
    val a = kotlin.math.abs(d)
    val s = 1f - 0.1f * a
    scaleX = s
    scaleY = s
    translationY = a * a * 36f
    alpha = 1f - 0.4f * a
    rotationY = -d * 16f
    cameraDistance = 16f * density
}
