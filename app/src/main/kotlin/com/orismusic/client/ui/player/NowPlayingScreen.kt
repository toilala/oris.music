package com.orismusic.client.ui.player

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.PlaylistStore
import com.orismusic.client.library.currentSavedSong
import android.os.SystemClock
import com.orismusic.client.playback.OfflineAudio
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.SleepTimer
import com.orismusic.client.utils.formatTime

/**
 * Full now-playing screen: artwork, title/artist, draggable seek bar, and
 * transport controls (shuffle, previous, play/pause, next, repeat).
 * Genuine Oris code.
 */
@Composable
fun NowPlayingScreen(
    connection: PlayerConnection,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onQueue: () -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
) {
    val look = com.orismusic.client.settings.AppearanceSettings
    val mode = look.backgroundMode
    val adaptive = look.adaptiveBackground
    val art = connection.currentMetadata.collectAsState().value?.artworkUri?.toString()
    var lyricsOpen by rememberSaveable { mutableStateOf(false) }
    // Fetch lyrics as soon as a song starts so the Lyrics view opens already filled.
    val lyricContext = androidx.compose.ui.platform.LocalContext.current
    val lyricMeta = connection.currentMetadata.collectAsState().value
    val lyricDuration = connection.duration.collectAsState().value
    val lyricId = connection.player.currentMediaItem?.mediaId.orEmpty()
    // If playback died while the app was away, the service restarts with an empty queue and
    // this screen would sit blank. Give metadata a moment, then fold back to the miniplayer.
    val noSong = connection.currentMetadata.collectAsState().value == null
    // Only a player restored after process death may fold itself back when the queue is
    // empty; one the user opened by hand stays open (PlayerLaunch.consumeRecreatedFlag).
    val restoredOpen = remember { com.orismusic.client.ui.navigation.PlayerLaunch.consumeRecreatedFlag() }
    androidx.compose.runtime.LaunchedEffect(noSong, connection) {
        if (!noSong || !restoredOpen) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (connection.currentMetadata.value == null && connection.player.mediaItemCount == 0) onCollapse()
    }
    androidx.compose.runtime.LaunchedEffect(lyricId, lyricMeta?.title, lyricDuration > 0) {
        val title = lyricMeta?.title?.toString().orEmpty()
        if (lyricId.isBlank() || title.isBlank() || lyricDuration <= 0) return@LaunchedEffect
        runCatching {
            com.orismusic.client.lyrics.LyricsRepository.load(
                lyricId, title, lyricMeta?.artist?.toString().orEmpty(), lyricMeta?.albumTitle?.toString().orEmpty(),
                (lyricDuration.coerceAtLeast(0) / 1000).toInt(), "automatic",
                com.orismusic.client.settings.LyricsPreferences(lyricContext).read(),
            )
        }
    }
    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = com.orismusic.client.ui.components.rememberArtworkColor(art) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = androidx.compose.animation.core.tween(700),
        label = "player tint",
    )
    val base = MaterialTheme.colorScheme.background
    val k = look.intensity
    val floor = if (!adaptive || mode == com.orismusic.client.settings.BackgroundMode.Solid) base
        else androidx.compose.ui.graphics.lerp(base, tint, 0.38f * k)
    val heroBlur by androidx.compose.animation.core.animateDpAsState(if (lyricsOpen) 36.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(450), label = "hero blur")
    val heroAlpha by animateFloatAsState(if (lyricsOpen) 0.35f else 1f,
        animationSpec = androidx.compose.animation.core.tween(450), label = "hero alpha")
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
    Box(modifier.fillMaxSize().background(base)) {
        // Glass: the same cover, blurred and enlarged, fills the whole screen behind everything.
        if (adaptive && mode == com.orismusic.client.settings.BackgroundMode.Ambient) {
            // Ambient: artwork colours drift behind everything instead of the still blur.
            AmbientBackground(
                floor,
                tint,
                androidx.compose.ui.graphics.lerp(tint, base, 0.45f),
                Modifier.fillMaxSize(),
            )
        } else {
            Crossfade(targetState = art, label = "glass art", modifier = Modifier.fillMaxSize()) { url ->
                com.orismusic.client.ui.components.Artwork(
                    url,
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }.blur(70.dp),
                    androidx.compose.ui.graphics.RectangleShape,
                    null,
                    px = 300,
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.15f), 1f to Color.Black.copy(alpha = 0.6f))))
        // Full-bleed cover over the top of the screen, melting into the glass below it.
        Crossfade(targetState = art, label = "hero art", modifier = Modifier.fillMaxWidth().height(screenH * 0.62f).align(Alignment.TopCenter)) { url ->
            com.orismusic.client.ui.components.Artwork(
                url,
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = heroAlpha; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                    .blur(heroBlur)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0.68f to Color.Black, 1f to Color.Transparent),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                        )
                    },
                androidx.compose.ui.graphics.RectangleShape,
                null,
                px = 1200,
            )
        }
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        NowPlayingContent(connection, onCollapse, onQueue, lyricsOpen, setLyricsOpen = { lyricsOpen = it }, onOpenArtist = onOpenArtist)
    }
}
