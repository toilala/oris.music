package com.orismusic.client.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    if (showProviderSettings && !embedded) {
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
    val active = document?.let { activeLyric(it.lines, position) } ?: -1
    LaunchedEffect(active) {
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
                        if (!embedded) {
                            DropdownMenuItem(text = { Text("Provider settings") }, onClick = { expanded = false; showProviderSettings = true })
                            HorizontalDivider()
                        }
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
            if (!embedded) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    Text("${lyrics.source} · ${if (lyrics.timed) "Synced lyrics" else "Plain lyrics"}",
                        Modifier.padding(horizontal=24.dp, vertical=8.dp), style=MaterialTheme.typography.labelMedium)
                    lyrics.credits.forEach { credit ->
                        TextButton(onClick = { uri.openUri(credit.url) }, contentPadding = PaddingValues(horizontal=24.dp, vertical=0.dp)) {
                            Text(credit.label, style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    LazyColumn(modifier = if (embedded) Modifier.weight(1f) else Modifier, state=list, contentPadding=PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (embedded) 60.dp else 320.dp), verticalArrangement=Arrangement.spacedBy(22.dp)) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            val selected = index == active
                            val distance = if (!lyrics.timed || active < 0) 0 else kotlin.math.abs(index - active)
                            val ink by animateColorAsState(
                                when {
                                    !lyrics.timed -> MaterialTheme.colorScheme.onSurface
                                    selected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = (0.55f - 0.1f * distance).coerceAtLeast(0.22f))
                                }, label = "lyric ink")
                            val emphasis by animateFloatAsState(if (selected) 1f else 0.9f,
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f), label = "lyric emphasis")
                            val soften by animateFloatAsState(if (!lyrics.timed || selected || distance == 1) 0f else (distance.coerceAtMost(4) * 1.2f),
                                label = "lyric blur")
                            Text(line.text.ifBlank { "\u266a" }, style=(if (embedded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium).copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color=ink,
                                modifier=Modifier.fillMaxWidth()
                                    .then(if (soften > 0.1f) Modifier.blur(soften.dp) else Modifier)
                                    .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    scaleX = emphasis; scaleY = emphasis
                                }.then(if (line.timeMs != null)
                                    Modifier.clickable { connection.player.seekTo(line.timeMs) } else Modifier))
                        }
                    }
                }
            }
            if (embedded) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                sourceMenu("Source: " + (document?.source ?: sourceName))
            }
        }
    }
}
