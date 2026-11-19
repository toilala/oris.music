package com.orismusic.client.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.models.SongItem
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.pressClickable
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Home banner as a fanned deck: the covers sit in a row, the centre one is full size and its
 * neighbours tilt back and shrink. The backdrop is the centre cover, blurred, and fades into
 * the next one as you swipe. Title and Play change with the card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeDeck(songs: List<SongItem>, connection: PlayerConnection?, scroll: () -> Int) {
    val pager = rememberPagerState(pageCount = { songs.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(songs.size) {
        while (true) {
            delay(7000)
            if (!pager.isScrollInProgress && songs.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % songs.size, animationSpec = tween(700))
            }
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(Modifier.fillMaxWidth().height(500.dp + topInset)) {
        Crossfade(
            targetState = pager.currentPage, animationSpec = tween(700), label = "deck-bg",
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Fade the backdrop out instead of blending toward a colour, so no edge shows on any background.
                    drawRect(Brush.verticalGradient(0f to Color.Black, 0.45f to Color.Black, 1f to Color.Transparent),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                },
        ) { page ->
            Artwork(
                songs[page.coerceIn(0, songs.lastIndex)].thumbnail,
                Modifier.fillMaxSize().graphicsLayer { translationY = scroll() * 0.2f; scaleX = 1.3f; scaleY = 1.3f }
                    .blur(48.dp).alpha(0.55f),
                androidx.compose.ui.graphics.RectangleShape, null, 400,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.3f to Color.Transparent),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 78.dp),
                modifier = Modifier.fillMaxWidth().height(268.dp),
            ) { page ->
                val s = songs[page]
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        val a = abs(off).coerceAtMost(1.5f)
                        val sc = 1f - 0.14f * a
                        scaleX = sc; scaleY = sc
                        rotationZ = -off * 7f
                        translationX = off * size.width * 0.12f
                        translationY = a * 14.dp.toPx()
                        this.alpha = 1f - 0.35f * a.coerceAtMost(1f)
                        shadowElevation = 18.dp.toPx() * (1f - a.coerceAtMost(1f))
                        shape = RoundedCornerShape(26.dp)
                        clip = true
                    }.pressClickable({ startFrom(connection, scope, songs, s) }, 0.97f),
                ) {
                    Artwork(s.thumbnail, Modifier.fillMaxSize(), RoundedCornerShape(26.dp), s.title, 800)
                }
            }
            val cur = songs[pager.currentPage.coerceIn(0, songs.lastIndex)]
            Crossfade(targetState = cur, animationSpec = tween(350), label = "deck-title") { song ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Picked for you", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
                    Text(song.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                    Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.tertiary)
                    .pressClickable({ startFrom(connection, scope, songs, cur) }, 0.94f).padding(start = 18.dp, end = 24.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
                Spacer(Modifier.width(6.dp))
                Text("Play", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiary)
            }
            Spacer(Modifier.height(14.dp))
            Row {
                repeat(songs.size) { i ->
                    val active = pager.currentPage == i
                    val w by animateDpAsState(if (active) 20.dp else 6.dp, spring(0.6f, 400f), label = "dot")
                    Box(
                        Modifier.padding(horizontal = 2.dp).height(6.dp).width(w).clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = if (active) 0.95f else 0.35f)),
                    )
                }
            }
        }
    }
}

/** Tapping the song that is already loaded resumes it. Starting the same song again only made it load twice. */
private fun startFrom(connection: PlayerConnection?, scope: kotlinx.coroutines.CoroutineScope, songs: List<SongItem>, song: SongItem) {
    val c = connection ?: return
    val loaded = runCatching { c.player.currentMediaItem?.mediaId }.getOrNull()
    if (loaded == song.id) c.play() else scope.launch { c.playQueue(MusicRadioQueue("Picked for you", songs, song.id)) }
}
