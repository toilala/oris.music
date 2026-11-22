package com.orismusic.client.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import com.orismusic.client.settings.LyricsPreferences
import com.orismusic.client.ui.screens.LyricsProviderSettings
import com.orismusic.client.lyrics.*
import com.orismusic.client.playback.PlayerConnection
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPane(connection: PlayerConnection, onClose: () -> Unit, embedded: Boolean = false) {
    val metadata by connection.currentMetadata.collectAsState()
    val position by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    val durationSeconds = (duration.coerceAtLeast(0) / 1000).toInt()
    val id = connection.player.currentMediaItem?.mediaId.orEmpty()
    val title = metadata?.title?.toString().orEmpty()
    val artist = metadata?.artist?.toString().orEmpty()
    val album = metadata?.albumTitle?.toString().orEmpty()
    var source by rememberSaveable { mutableStateOf("automatic") }
    val context = LocalContext.current
    val preferences = remember(context) { LyricsPreferences(context) }
    var options by remember { mutableStateOf(preferences.read()) }
    var showProviderSettings by rememberSaveable { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    if (showProviderSettings) {
        LyricsProviderSettings(onBack = { options = preferences.read(); showProviderSettings = false })
        return
    }
    val sourceName = LyricsSources.find(source)?.name ?: "Automatic"
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var document by remember(id, title, artist, album, durationSeconds, source, options) {
        mutableStateOf(LyricsRepository.cached(id, title, artist, album, durationSeconds, source, options))
    }
    var expanded by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    BackHandler { onClose() }
    LaunchedEffect(id, title, artist, album, durationSeconds, source, retry, options) {
        if (document != null) return@LaunchedEffect
        if (id.isBlank() || title.isBlank()) return@LaunchedEffect
        loading = true
        try {
            document = LyricsRepository.load(id, title, artist, album, durationSeconds, source, options)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { loading = false }
    }
    val lyricOffset = com.orismusic.client.settings.LyricTiming.offset(id)
    val active = document?.let { activeLyric(it.lines, position + lyricOffset) } ?: -1
    LaunchedEffect(active) {
        if (!com.orismusic.client.settings.PlayerSettings.lyricsAutoScroll) return@LaunchedEffect
        if (active >= 0 && !list.isScrollInProgress) list.animateScrollToItem(active, scrollOffset = -(list.layoutInfo.viewportSize.height / 3))
    }
    val artUrl = metadata?.artworkUri?.toString()
    val glow by androidx.compose.animation.animateColorAsState(
        com.orismusic.client.ui.components.rememberArtworkColor(artUrl) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "lyrics glow")
    val look = com.orismusic.client.settings.AppearanceSettings
    val wash = if (look.adaptiveBackground && look.backgroundMode != com.orismusic.client.settings.BackgroundMode.Solid) look.intensity else 0f
        val sourceMenu: @Composable (String) -> Unit = { label ->
                Box {
                    TextButton(onClick = { expanded = true }) { Text(label, style = if (embedded) MaterialTheme.typography.labelLarge else LocalTextStyle.current, color = if (embedded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("Automatic") }, onClick = { source = "automatic"; expanded = false })
                        DropdownMenuItem(text = { Text("Provider settings") }, onClick = { expanded = false; showProviderSettings = true })
                        HorizontalDivider()
                        options.ordered().forEach { provider ->
                            val enabled = provider.available && provider.id !in options.disabled
                            DropdownMenuItem(text = { Text(provider.name + if (enabled) "" else " (disabled)") },
                                enabled = enabled, onClick = { source = provider.id; expanded = false })
                        }
                    }
                }
        }
    Surface(Modifier.fillMaxSize(), color = if (embedded) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()
            .then(if (embedded) Modifier else Modifier
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(glow.copy(alpha = 0.8f * wash), androidx.compose.ui.graphics.Color.Transparent)))
                .statusBarsPadding().navigationBarsPadding())) {
            if (embedded) {
                // Player's lyrics view: only the words. The player screen draws the header.
            } else Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("Back to player") }
                sourceMenu(sourceName)
            }
            if (!embedded) {
            Text("Lyrics", Modifier.padding(horizontal=24.dp), style=MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Text(title.ifBlank { "No song selected" }, Modifier.padding(horizontal=24.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(artist, Modifier.padding(horizontal=24.dp, vertical=2.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
                document == null -> Column(Modifier.padding(24.dp)) {
                    Text(if (id.isBlank()) "Choose a song to see its lyrics." else "Lyrics unavailable from this source. Try Automatic or another source.")
                    if (source == "better" || source == "kugou") Text("Better Lyrics may only serve songs already in its public cache. Source access and coverage can vary.", Modifier.padding(top=12.dp), style=MaterialTheme.typography.bodySmall)
                    if (id.isNotBlank()) TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                else -> {
                    val lyrics = document!!
                    if (!embedded) {
                        Text("${lyrics.source} · ${if (lyrics.timed) "Synced lyrics" else "Plain lyrics"}",
                            Modifier.padding(horizontal=24.dp, vertical=8.dp), style=MaterialTheme.typography.labelMedium)
                        lyrics.credits.forEach { credit ->
                            TextButton(onClick = { uri.openUri(credit.url) }, contentPadding = PaddingValues(horizontal=24.dp, vertical=0.dp)) {
                                Text(credit.label, style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    LazyColumn(
                        modifier = (if (embedded) Modifier.weight(1f) else Modifier)
                            .then(if (embedded) Modifier
                                .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    // One gradient: clear at both ends, solid through the middle. Two separate
                                    // full-size fades would multiply each other and erase the whole list.
                                    val fade = (64.dp.toPx() / size.height).coerceAtMost(0.5f)
                                    drawRect(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            0f to androidx.compose.ui.graphics.Color.Transparent,
                                            fade to androidx.compose.ui.graphics.Color.Black,
                                            (1f - fade) to androidx.compose.ui.graphics.Color.Black,
                                            1f to androidx.compose.ui.graphics.Color.Transparent,
                                        ),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                                    )
                                } else Modifier),
                        state=list,
                        contentPadding=PaddingValues(top = if (embedded) 8.dp else 24.dp, bottom = if (embedded) 48.dp else 320.dp),
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            LyricRow(
                                line = line, timed = lyrics.timed, selected = index == active,
                                distance = if (!lyrics.timed || active < 0) 0 else kotlin.math.abs(index - active),
                                position = position + lyricOffset, embedded = embedded,
                                onSeek = { ms -> connection.player.seekTo((ms - lyricOffset).coerceAtLeast(0)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One lyric line: bold white words that dim with distance; karaoke sweep on the active line.
 * Line styling follows Echo Music's MetroLyricsLine (GPL-3.0); code is Oris's own. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LyricRow(
    line: LyricLine,
    timed: Boolean,
    selected: Boolean,
    distance: Int,
    position: Long,
    embedded: Boolean,
    onSeek: (Long) -> Unit,
) {
    val targetAlpha = when {
        !timed -> 1f
        selected -> 1f
        distance <= 2 -> 0.2f
        distance == 3 -> 0.15f
        distance == 4 -> 0.1f
        else -> 0.08f
    }
    val alpha by animateFloatAsState(targetAlpha, tween(300), label = "line alpha")
    val scale by animateFloatAsState(if (selected) 1f else 0.85f, tween(400), label = "line scale")
    val settings = com.orismusic.client.settings.PlayerSettings
    val sizeSp = when (settings.lyricSize) {
        com.orismusic.client.settings.LyricSize.Compact -> 20
        com.orismusic.client.settings.LyricSize.Regular -> 24
        com.orismusic.client.settings.LyricSize.Large -> 28
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val view = androidx.compose.ui.platform.LocalView.current
    val lineStyle = androidx.compose.ui.text.TextStyle(
        fontSize = sizeSp.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        lineHeight = (sizeSp * 1.3f).sp,
        letterSpacing = (-0.5).sp,
        textAlign = if (settings.lyricsCentered) TextAlign.Center else TextAlign.Start,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
        ),
    )
    val lineModifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
            transformOrigin = TransformOrigin(if (settings.lyricsCentered) 0.5f else 0f, 0.5f)
            scaleX = scale; scaleY = scale
        }
        .clip(RoundedCornerShape(16.dp))
        .combinedClickable(
            onClick = { if (settings.lyricsTapSeek) line.timeMs?.let(onSeek) },
            onLongClick = {
                if (settings.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(line.text))
            },
        )
        .padding(horizontal = 24.dp, vertical = (8 * 1.3f).dp)
    val lineWords = line.words
    if (selected && timed && !lineWords.isNullOrEmpty()) {
        // Karaoke: the sung words sweep through the active line.
        val currentWord = lineWords.indexOfLast { position >= it.startMs }
        val karaoke = buildAnnotatedString {
            lineWords.forEachIndexed { wi, w ->
                withStyle(SpanStyle(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = if (wi <= currentWord) 1f else 0.3f),
                    fontWeight = if (wi == currentWord) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Bold,
                )) { append(w.text) }
                if (wi < lineWords.lastIndex) append(" ")
            }
        }
        Text(karaoke, style = lineStyle, modifier = lineModifier)
    } else {
        Text(
            line.text.ifBlank { "\u266a" },
            style = lineStyle,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
            modifier = lineModifier,
        )
    }
}

/**
 * LYRICS mode: a compact header (collapse, mini artwork, title, artist), a scrollable
 * lyrics viewport faded at both ends, and a pinned bottom bar with the same seek bar and
 * transport as the player. Lyrics and controls never share a zone, so nothing overlaps.
 */
@Composable
fun LyricsMode(connection: PlayerConnection, onClose: () -> Unit) {
    // --- state: the words, the position, the accent ---
    val metadata by connection.currentMetadata.collectAsState()
    val position by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    val canSkipPrevious by connection.canSkipPrevious.collectAsState()
    val canSkipNext by connection.canSkipNext.collectAsState()
    val durationSeconds = (duration.coerceAtLeast(0) / 1000).toInt()
    val id = connection.player.currentMediaItem?.mediaId.orEmpty()
    val title = metadata?.title?.toString().orEmpty()
    val artist = metadata?.artist?.toString().orEmpty()
    val album = metadata?.albumTitle?.toString().orEmpty()
    val source = "automatic"
    val context = LocalContext.current
    val options = remember(context) { LyricsPreferences(context).read() }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var document by remember(id, title, artist, album, durationSeconds, options) {
        mutableStateOf(LyricsRepository.cached(id, title, artist, album, durationSeconds, source, options))
    }
    val list = rememberLazyListState()
    BackHandler { onClose() }
    LaunchedEffect(id, title, artist, album, durationSeconds, retry, options) {
        if (document != null) return@LaunchedEffect
        if (id.isBlank() || title.isBlank()) return@LaunchedEffect
        loading = true
        try {
            document = LyricsRepository.load(id, title, artist, album, durationSeconds, source, options)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { loading = false }
    }
    val lyricOffset = com.orismusic.client.settings.LyricTiming.offset(id)
    val active = document?.let { activeLyric(it.lines, position + lyricOffset) } ?: -1
    LaunchedEffect(active) {
        if (active >= 0 && !list.isScrollInProgress) list.animateScrollToItem(active, scrollOffset = -(list.layoutInfo.viewportSize.height / 3))
    }
    val artUrl = metadata?.artworkUri?.toString()
    val tint by androidx.compose.animation.animateColorAsState(
        com.orismusic.client.ui.components.rememberArtworkColor(artUrl) ?: Color(0xFFE9E4DC),
        animationSpec = tween(700), label = "lyrics accent")
    val accent = lerp(tint, Color.White, 0.55f)

    // --- layout ---
    Column(Modifier.fillMaxSize()) {
        // Compact header: drag handle, then collapse + mini artwork + the song's words.
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f)))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Back to player", Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.85f))
            }
            Spacer(Modifier.width(12.dp))
            com.orismusic.client.ui.components.Artwork(artUrl, Modifier.size(44.dp), RoundedCornerShape(8.dp), null, px = 200)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title.ifBlank { "No song selected" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1,
                    color = Color.White, overflow = TextOverflow.Ellipsis)
                Text(artist, style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The viewport: lyrics scroll between the header and the bottom bar, faded at both ends.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading && document == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent)
                }
                document == null -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (id.isBlank()) "Choose a song to see its lyrics." else "Lyrics unavailable from this source. Try Automatic or another source.",
                        color = Color.White.copy(alpha = 0.75f), textAlign = TextAlign.Center,
                    )
                    if (id.isNotBlank()) TextButton(onClick = { retry++ }) { Text("Try again", color = accent) }
                }
                else -> {
                    val lyrics = document!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = list,
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            LyricRow(
                                line = line, timed = lyrics.timed, selected = index == active,
                                distance = if (!lyrics.timed || active < 0) 0 else kotlin.math.abs(index - active),
                                position = position + lyricOffset, embedded = true,
                                onSeek = { ms -> connection.player.seekTo((ms - lyricOffset).coerceAtLeast(0)) },
                            )
                        }
                    }
                    // The words melt away before they reach the header or the bottom bar.
                    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                }
            }
        }
        // Pinned controls: the same seek bar and transport as the player, on their own scrim.
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.62f))))
                .navigationBarsPadding()
                .padding(horizontal = 22.dp),
        ) {
            WaveSeekBar(
                player = connection.player,
                durationMs = duration.coerceAtLeast(0L),
                isPlaying = isPlaying,
                onSeek = { connection.seekTo(it) },
                accent = accent,
            )
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                NowPlayingTransport(
                    isPlaying = isPlaying, canPrevious = canSkipPrevious, canNext = canSkipNext, accent = accent,
                    onPrevious = { connection.seekToPrevious() },
                    onToggle = { if (isPlaying) connection.pause() else connection.play() },
                    onNext = { connection.seekToNext() },
                )
            }
        }
    }
}
