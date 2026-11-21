package com.orismusic.client.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.currentSavedSong
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.settings.PlayerSettings

/**
 * The player below the cover: the words with three quiet circles (like, lyrics, collapse),
 * then the seek bar and the big plain transport spread over the rest of the screen, and a
 * sliver of the tray peeking over the bottom edge. Lyrics get their own full-screen view.
 */
@Composable
internal fun NowPlayingContent(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    setLyricsOpen: (Boolean) -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val metadata = connection.currentMetadata.collectAsState().value
    val isPlaying by connection.isPlaying.collectAsState()
    val duration by connection.duration.collectAsState()
    val canSkipPrevious by connection.canSkipPrevious.collectAsState()
    val canSkipNext by connection.canSkipNext.collectAsState()
    val index by connection.currentMediaItemIndex.collectAsState()
    val library = LibraryStore.get(androidx.compose.ui.platform.LocalContext.current)
    val liked = library.liked.collectAsState().value
    val nowSong = remember(metadata, index) { connection.currentSavedSong() }
    val isLiked = nowSong != null && liked.any { it.id == nowSong.id }
    var trayOpen by remember { mutableStateOf(false) }

    val artUrl = metadata?.artworkUri?.toString()
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = com.orismusic.client.ui.components.rememberArtworkColor(artUrl) ?: Color(0xFFE9E4DC),
        animationSpec = tween(700), label = "player accent",
    )
    // Lighten the artwork colour so the wave stays readable on dark backgrounds.
    val accent = androidx.compose.ui.graphics.lerp(tint, Color.White, 0.55f)

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The cover is as wide as the screen, so the title block starts over its lower fade.
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val zoneHeight = (screenH * 0.47f - statusTop).coerceAtLeast(120.dp)

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
    ) {
        // Cover zone: this space stays clear for the hero art; swiping down folds the player away.
        Box(
            Modifier.fillMaxWidth().height(zoneHeight)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onVerticalDrag = { _, dy -> total += dy },
                        onDragEnd = { if (total > 140f) onCollapse() },
                    )
                },
        )

        // Title and artist on the left, the quiet circles (like, lyrics, collapse) on the right.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val artistId = metadata?.extras?.getString("artist_id")
            NowPlayingTitle(
                metadata?.title?.toString().orEmpty(), metadata?.artist?.toString().orEmpty(), Modifier.weight(1f),
                onArtistClick = artistId?.let { id -> { onOpenArtist(id, metadata?.artist?.toString().orEmpty()) } },
            )
            val likeView = androidx.compose.ui.platform.LocalView.current
            val likePress = remember { MutableInteractionSource() }
            val likePressed by likePress.collectIsPressedAsState()
            val likeScale by animateFloatAsState(if (isLiked) 1f else if (likePressed) 0.8f else 1f,
                spring(dampingRatio = 0.35f, stiffness = 400f), label = "like pop")
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))
                    .clickable(interactionSource = likePress, indication = null, enabled = nowSong != null) { if (PlayerSettings.haptics) likeView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); nowSong?.let { library.toggleLike(it) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from liked songs" else "Like song",
                    tint = if (isLiked) accent else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
            Spacer(Modifier.width(10.dp))
            CircleKey(onClick = { setLyricsOpen(true) }, desc = "Lyrics", icon = Icons.Rounded.Lyrics)
            Spacer(Modifier.width(10.dp))
            CircleKey(onClick = onCollapse, desc = "Collapse player", icon = Icons.Rounded.KeyboardArrowDown)
        }

        // Share the rest of the screen evenly between the gaps, so the seek bar and the
        // controls spread over the full height instead of bunching up under the title.
        Spacer(Modifier.weight(1f))
        WaveSeekBar(
            player = connection.player,
            durationMs = duration.coerceAtLeast(0L),
            isPlaying = isPlaying,
            onSeek = { connection.seekTo(it) },
            accent = accent,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            NowPlayingTransport(
                isPlaying = isPlaying, canPrevious = canSkipPrevious, canNext = canSkipNext, accent = accent,
                onPrevious = { connection.seekToPrevious() },
                onToggle = { if (isPlaying) connection.pause() else connection.play() },
                onNext = { connection.seekToNext() },
            )
        }
        Spacer(Modifier.weight(1f))

        // A sliver of the tray peeking over the bottom edge; tap to open everything else.
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                    // Reach past the column's side padding so the peek sits on the screen edges.
                    val extra = 22.dp.roundToPx()
                    val wide = constraints.maxWidth + extra * 2
                    val placeable = measurable.measure(constraints.copy(minWidth = wide, maxWidth = wide))
                    layout(wide, placeable.height) { placeable.place(-extra, 0) }
                }.clickable { trayOpen = true },
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
            }
        }
    }
    if (trayOpen) PlayerTray(connection, onQueue = onQueue, onDismiss = { trayOpen = false })
}

/** A small quiet circle for the actions around the title row. */
@Composable
private fun CircleKey(onClick: () -> Unit, desc: String, icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.85f))
    }
}
