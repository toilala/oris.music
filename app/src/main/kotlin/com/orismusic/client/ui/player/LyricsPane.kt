package com.orismusic.client.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.lyrics.*
import com.orismusic.client.playback.PlayerConnection
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsPane(connection: PlayerConnection, onClose: () -> Unit) {
    val metadata by connection.currentMetadata.collectAsState()
    val position by connection.currentPosition.collectAsState()
    val duration by connection.duration.collectAsState()
    val id = connection.player.currentMediaItem?.mediaId.orEmpty()
    val title = metadata?.title?.toString().orEmpty()
    val artist = metadata?.artist?.toString().orEmpty()
    val album = metadata?.albumTitle?.toString().orEmpty()
    var source by rememberSaveable { mutableStateOf("Automatic") }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var document by remember { mutableStateOf<LyricsDocument?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    BackHandler { onClose() }
    LaunchedEffect(id, title, artist, album, source, retry) {
        document = null
        if (id.isBlank() || title.isBlank()) return@LaunchedEffect
        loading = true
        try {
            document = LyricsRepository.load(id, title, artist, album, (duration.coerceAtLeast(0)/1000).toInt(), source)
        } catch (cancelled: CancellationException) { throw cancelled }
        finally { loading = false }
    }
    val active = document?.let { activeLyric(it.lines, position) } ?: -1
    LaunchedEffect(active) {
        if (active >= 0 && !list.isScrollInProgress) list.animateScrollToItem((active-2).coerceAtLeast(0))
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose) { Text("Back to player") }
                Box {
                    TextButton(onClick = { expanded = true }) { Text(source) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        LyricsRepository.sources.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { source = name; expanded = false })
                        }
                    }
                }
            }
            Text("Lyrics", Modifier.padding(horizontal=24.dp), style=MaterialTheme.typography.headlineMedium)
            Text(title.ifBlank { "No song selected" }, Modifier.padding(horizontal=24.dp, vertical=8.dp))
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp))
                document == null -> Column(Modifier.padding(24.dp)) {
                    Text(if (id.isBlank()) "Choose a song to see its lyrics." else "Lyrics unavailable from this source. Try Automatic or another source.")
                    if (source.startsWith("Better Lyrics")) Text("Better Lyrics may only serve songs already in its public cache. Source access and coverage can vary.", Modifier.padding(top=12.dp), style=MaterialTheme.typography.bodySmall)
                    if (id.isNotBlank()) TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                else -> {
                    val lyrics = document!!
                    Text("${lyrics.source} · ${if (lyrics.timed) "Synced lyrics" else "Plain lyrics"}",
                        Modifier.padding(horizontal=24.dp, vertical=8.dp), style=MaterialTheme.typography.labelMedium)
                    LazyColumn(state=list, contentPadding=PaddingValues(24.dp), verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        itemsIndexed(lyrics.lines) { index, line ->
                            Text(line.text.ifBlank { "♪" }, style=MaterialTheme.typography.headlineSmall,
                                color=if (index == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier=Modifier.fillMaxWidth().then(if (line.timeMs != null)
                                    Modifier.clickable { connection.player.seekTo(line.timeMs) } else Modifier))
                        }
                    }
                }
            }
        }
    }
}
