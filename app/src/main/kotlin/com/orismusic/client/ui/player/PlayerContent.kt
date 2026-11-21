package com.orismusic.client.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.currentSavedSong
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.settings.PlayerSettings

/**
 * The player below the cover: title over the cover's fade, a seek bar that flows, three big
 * controls, and one bar at the bottom with the lyrics icon and a swipe-up tray for everything else.
 */
@Composable
internal fun NowPlayingContent(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    onQueue: () -> Unit,
    lyricsOpen: Boolean,
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
    val library = LibraryStore.get(LocalContext.current)
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

    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // The cover is as wide as the screen, so the title block starts over its lower fade.
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val zoneHeight = (screenH * 0.47f - statusTop).coerceAtLeast(120.dp)
    val nextIndex = connection.player.nextMediaItemIndex
    val nextTitle = if (nextIndex != C.INDEX_UNSET) connection.player.getMediaItemAt(nextIndex).mediaMetadata.title?.toString() else null

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Cover zone: swipe down to close, or lyrics on top of the dimmed cover.
        Box(
            Modifier.fillMaxWidth().height(zoneHeight)
                .pointerInput(lyricsOpen) {
                    if (!lyricsOpen) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onVerticalDrag = { _, dy -> total += dy },
                            onDragEnd = { if (total > 140f) onCollapse() },
                        )
                    }
                },
        ) {
            androidx.compose.animation.Crossfade(
                targetState = lyricsOpen, animationSpec = tween(300),
                modifier = Modifier.padding(top = 28.dp), label = "lyrics",
            ) { open ->
                if (open) LyricsPane(connection, onClose = { setLyricsOpen(false) }, embedded = true)
            }
        }

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
                Modifier.size(46.dp).clip(CircleShape)
                    .clickable(interactionSource = likePress, indication = null, enabled = nowSong != null) { if (PlayerSettings.haptics) likeView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); nowSong?.let { library.toggleLike(it) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from liked songs" else "Like song",
                    tint = if (isLiked) accent else Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp).graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                )
            }
        }

        MiniSyncedLyrics(connection, metadata)

        Spacer(Modifier.height(10.dp))
        WaveSeekBar(
            player = connection.player,
            durationMs = duration.coerceAtLeast(0L),
            isPlaying = isPlaying,
            onSeek = { connection.seekTo(it) },
            accent = accent,
        )
        Spacer(Modifier.height(6.dp))

        Spacer(Modifier.weight(0.55f))
        NowPlayingTransport(
            isPlaying = isPlaying, canPrevious = canSkipPrevious, canNext = canSkipNext, accent = accent,
            onPrevious = { connection.seekToPrevious() },
            onToggle = { if (isPlaying) connection.pause() else connection.play() },
            onNext = { connection.seekToNext() },
        )
        Spacer(Modifier.weight(0.45f))

        // A quiet peek at the next song; tapping it opens the queue.
        if (nextTitle != null && PlayerSettings.upNext) {
            Text(
                "Up next  \u00B7  " + nextTitle,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onQueue() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
        }

        // One compact pill at the bottom: lyrics, queue, and everything else. The lyrics half
        // lights up while lyrics are open.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lyricsBg by animateFloatAsState(if (lyricsOpen) 0.20f else 0f, tween(300), label = "lyrics bg")
                PillHalf(
                    icon = { tint -> Icon(Icons.Rounded.Lyrics, contentDescription = "Lyrics", tint = tint, modifier = Modifier.size(19.dp)) },
                    label = "Lyrics",
                    active = lyricsOpen,
                    accent = accent,
                    backgroundAlpha = lyricsBg,
                    onClick = { setLyricsOpen(!lyricsOpen) },
                )
                Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.14f)))
                PillHalf(
                    icon = { tint -> Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = tint, modifier = Modifier.size(19.dp)) },
                    label = "Queue",
                    active = false,
                    accent = accent,
                    backgroundAlpha = 0f,
                    onClick = onQueue,
                )
                Box(Modifier.width(1.dp).height(22.dp).background(Color.White.copy(alpha = 0.14f)))
                PillHalf(
                    icon = { tint -> Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = tint, modifier = Modifier.size(19.dp)) },
                    label = "More",
                    active = false,
                    accent = accent,
                    backgroundAlpha = 0f,
                    onClick = { trayOpen = true },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (trayOpen) PlayerTray(connection, onQueue = onQueue, onDismiss = { trayOpen = false })
}

/** One half of the bottom pill: a small icon and a word. Lights up in the accent colour when active. */
@Composable
private fun PillHalf(
    icon: @Composable (Color) -> Unit,
    label: String,
    active: Boolean,
    accent: Color,
    backgroundAlpha: Float,
    onClick: () -> Unit,
) {
    val tint = if (active) accent else Color.White.copy(alpha = 0.85f)
    Row(
        Modifier.clip(RoundedCornerShape(28.dp)).background(accent.copy(alpha = backgroundAlpha))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(tint)
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** One or two lines of the synced lyric currently being sung, just above the seek bar.
 *  Hidden when the song has no synced lyrics, so the layout stays exactly as before. */
@Composable
private fun MiniSyncedLyrics(connection: PlayerConnection, metadata: androidx.media3.common.MediaMetadata?) {
    val context = LocalContext.current
    val id = connection.player.currentMediaItem?.mediaId.orEmpty()
    val title = metadata?.title?.toString().orEmpty()
    val artist = metadata?.artist?.toString().orEmpty()
    val album = metadata?.albumTitle?.toString().orEmpty()
    val duration by connection.duration.collectAsState()
    val durationSeconds = (duration.coerceAtLeast(0) / 1000).toInt()
    val options = remember(context) { com.orismusic.client.settings.LyricsPreferences(context).read() }
    var document by remember(id, title, artist, album, durationSeconds) {
        mutableStateOf(com.orismusic.client.lyrics.LyricsRepository.cached(id, title, artist, album, durationSeconds, "automatic", options))
    }
    LaunchedEffect(id, title, artist, album, durationSeconds) {
        if (document != null || id.isBlank() || title.isBlank() || durationSeconds <= 0) return@LaunchedEffect
        document = runCatching {
            com.orismusic.client.lyrics.LyricsRepository.load(id, title, artist, album, durationSeconds, "automatic", options)
        }.getOrNull()
    }
    val position by connection.currentPosition.collectAsState()
    val lines = document?.lines?.filter { it.timeMs != null && it.text.isNotBlank() }.orEmpty()
    if (lines.isEmpty()) return
    val active = com.orismusic.client.lyrics.activeLyric(lines, position)
    if (active < 0) return
    Crossfade(targetState = active, animationSpec = tween(250), label = "mini lyric") { idx ->
        Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                lines.getOrNull(idx)?.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val next = lines.getOrNull(idx + 1)?.text
            if (!next.isNullOrBlank()) {
                Text(
                    next, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
