package com.orismusic.client.ui.screens

import androidx.compose.foundation.background
import com.orismusic.client.library.toSavedSong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.orismusic.client.ui.components.artworkTintBackdrop
import com.orismusic.client.ui.components.entrance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orismusic.client.playback.PlayerConnection

/**
 * Up-next queue: every loaded queue item, current one highlighted, tap any
 * row to jump to it. Genuine Oris code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    connection: PlayerConnection?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var saving by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var saveName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var saved by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (saving) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text("Save queue as playlist") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = saveName, onValueChange = { saveName = it }, singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = saveName.isNotBlank(),
                    onClick = {
                        val store = com.orismusic.client.library.PlaylistStore.get(context)
                        val id = store.create(saveName)
                        val p = connection?.player
                        val n = try { p?.mediaItemCount ?: 0 } catch (e: Exception) { 0 }
                        for (i in 0 until n) {
                            val song = try { p?.getMediaItemAt(i)?.toSavedSong() } catch (e: Exception) { null }
                            if (song != null) store.add(id, song)
                        }
                        saving = false; saveName = ""; saved = true
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { saving = false }) { Text("Cancel") }
            },
        )
    }
    val player = connection?.player
    var version by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val count = try { version.let { player?.mediaItemCount ?: 0 } } catch (e: Exception) { 0 }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back from queue")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = { saving = true },
                        enabled = count > 0,
                    ) { Text(if (saved) "Saved" else "Save") }
                    IconButton(onClick = onOpenPlayer, enabled = count > 0) {
                        Icon(Icons.Filled.ExpandLess, "Open player")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (connection == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                Text(
                    text = "Player is starting up",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        if (player == null) return@Scaffold

        val queueTitle = connection.queueTitle.collectAsState().value
        val currentIndex = connection.currentWindowIndex.collectAsState().value
        val view = androidx.compose.ui.platform.LocalView.current

        val nowArt = try { version.let { player.getMediaItemAt(currentIndex).mediaMetadata.artworkUri?.toString() } } catch (e: Exception) { null }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .artworkTintBackdrop(nowArt)
                .padding(innerPadding),
        ) {
            if (!queueTitle.isNullOrBlank()) {
                Text(
                    text = queueTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            if (count == 0) {
                Text(
                    text = "Nothing queued yet. Pick a song on Home.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                androidx.compose.runtime.LaunchedEffect(currentIndex) {
                    val header = if (currentIndex > 0) 1 + currentIndex else 0
                    listState.scrollToItem((header - 1).coerceAtLeast(0))
                }
                @androidx.compose.runtime.Composable
                fun Header(text: String) = Text(
                    text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                )
                @androidx.compose.runtime.Composable
                fun QueueRow(index: Int, dim: Boolean) {
                    val item = try { player.getMediaItemAt(index) } catch (e: Exception) { null } ?: return
                    val isNow = index == currentIndex
                    androidx.compose.runtime.key(version, index) {
                        val dismiss = androidx.compose.material3.rememberSwipeToDismissBoxState(
                            confirmValueChange = { v ->
                                if (v == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart && !isNow) {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                                    try { player.removeMediaItem(index) } catch (e: Exception) { }
                                    version++
                                    true
                                } else false
                            },
                        )
                        androidx.compose.material3.SwipeToDismissBox(
                            state = dismiss,
                            enableDismissFromStartToEnd = false,
                            enableDismissFromEndToStart = !isNow,
                            backgroundContent = {
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                    Text("Remove", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        ) {
                            Box(Modifier.alpha(if (dim) 0.55f else 1f)) {
                                com.orismusic.client.ui.components.SongRow(
                                    title = item.mediaMetadata.title?.toString().orEmpty(),
                                    subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                                    thumbnail = item.mediaMetadata.artworkUri?.toString(),
                                    onClick = { connection.seekToIndex(index) },
                                    highlight = isNow,
                                )
                            }
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = com.orismusic.client.ui.bottomInsetPadding()) {
                    if (currentIndex > 0) {
                        item(key = "h-prev") { Header("Previously played") }
                        items(count = currentIndex, key = { "p$it" }) { Box(Modifier.entrance(it)) { QueueRow(it, dim = true) } }
                    }
                    if (currentIndex in 0 until count) {
                        item(key = "h-now") { Header("Now playing") }
                        item(key = "now") { QueueRow(currentIndex, dim = false) }
                    }
                    if (currentIndex + 1 < count) {
                        item(key = "h-next") { Header("Next in queue") }
                        items(count = count - currentIndex - 1, key = { "n$it" }) { Box(Modifier.entrance(it)) { QueueRow(currentIndex + 1 + it, dim = false) } }
                    }
                }
            }
        }
    }
}
