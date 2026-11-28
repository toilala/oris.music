@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.orismusic.client.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.orismusic.client.ui.screens.DetailTarget

private val LocalCoverScope = compositionLocalOf<SharedTransitionScope?> { null }
private val LocalCoverVisibility = compositionLocalOf<AnimatedVisibilityScope?> { null }

private val CloseEasing = CubicBezierEasing(0.4f, 0f, 0.1f, 1f)

// Growing out of the card is a soft spring; going back is a fixed-length ease, so a slow
// first frame of the page underneath costs nothing you can see.
private val CoverBounds = BoundsTransform { from, to ->
    if (to.width < from.width) tween(OrisMotion.Slow - 90, easing = CloseEasing)
    else spring(dampingRatio = 0.9f, stiffness = 170f, visibilityThreshold = Rect.VisibilityThreshold)
}

/** Covers of albums and playlists carry one key on the card and on the page it opens. */
fun coverKey(target: DetailTarget): String? =
    if (target.kind == "artist") null else "cover-${target.kind}-${target.id}"

@Composable
fun SharedCoverHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalCoverScope provides this) { content() }
    }
}

/** Home and a detail page, one in front of the other, sharing covers. The detail page keeps its last target so its exit still has content to move. */
@Composable
fun DetailStage(detail: DetailTarget?, detailPage: @Composable (DetailTarget) -> Unit, home: @Composable () -> Unit) {
    var lastDetail by remember { mutableStateOf<DetailTarget?>(null) }
    if (detail != null) lastDetail = detail
    SharedCoverHost(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = detail != null,
            enter = fadeIn(OrisMotion.standard()) + slideInVertically(OrisMotion.smooth()) { it / 40 },
            exit = fadeOut(OrisMotion.quick()) + slideOutVertically(OrisMotion.smooth()) { it / 40 },
        ) {
            CoverVisibility { lastDetail?.let { detailPage(it) } }
        }
        AnimatedVisibility(
            visible = detail == null,
            enter = fadeIn(OrisMotion.quick(delayMillis = 60)),
            exit = fadeOut(OrisMotion.micro()),
        ) {
            CoverVisibility { home() }
        }
    }
}

@Composable
fun AnimatedVisibilityScope.CoverVisibility(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCoverVisibility provides this) { content() }
}

/** Put before size and clip, so the cover flies as the finished, clipped picture. */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val scope = LocalCoverScope.current
    val visibility = LocalCoverVisibility.current
    if (key == null || scope == null || visibility == null) return this
    return with(scope) {
        this@sharedCover.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = CoverBounds,
        )
    }
}
