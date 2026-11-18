package com.orismusic.client.ui.screens

import androidx.compose.foundation.background
import com.orismusic.client.library.toSavedSong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
                        enabled = connection != null,
                    ) { Text(if (saved) "Saved" else "Save") }
                    IconButton(onClick = onOpenPlayer, enabled = connection != null) {
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

        val queueTitle = connection.queueTitle.collectAsState().value
        val currentIndex = connection.currentWindowIndex.collectAsState().value
        val player = connection.player
        val count = try { player.mediaItemCount } catch (e: Exception) { 0 }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!queueTitle.isNullOrBlank()) {
                Text(
                    text = queueTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = (0 until count).map { index ->
                            try { player.getMediaItemAt(index) } catch (e: Exception) { null }
                        },
                        key = { index, item -> item?.mediaId?.plus("#$index") ?: index.toString() },
                    ) { index, item ->
                        if (item == null) return@itemsIndexed
                        val isCurrent = index == currentIndex
                        com.orismusic.client.ui.components.SongRow(
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            subtitle = item.mediaMetadata.artist?.toString().orEmpty(),
                            thumbnail = item.mediaMetadata.artworkUri?.toString(),
                            onClick = { connection.seekToIndex(index) },
                            highlight = isCurrent,
                        )
                    }
                }
            }
        }
    }
}
