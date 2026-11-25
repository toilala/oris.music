package com.orismusic.client.ui.screens

import com.orismusic.client.ui.theme.OrisMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.orismusic.client.ui.components.SongRow
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.components.entrance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.PlaylistStore
import com.orismusic.client.library.SavedSong
import com.orismusic.client.library.toPlayableItem
import com.orismusic.client.playback.OfflineAudio
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue

/** Liked songs and recently played, both kept on the phone. */
@Composable
fun LibraryScreen(
    connection: PlayerConnection?,
    modifier: Modifier = Modifier,
    onQueue: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val store = LibraryStore.get(LocalContext.current)
    val liked by store.liked.collectAsState()
    val history by store.history.collectAsState()
    val appContext = LocalContext.current
    remember { OfflineAudio.load(appContext) }
    val saved by OfflineAudio.downloaded.collectAsState()
    val playlistStore = PlaylistStore.get(LocalContext.current)
    val playlists by playlistStore.playlists.collectAsState()
    var section by rememberSaveable { mutableIntStateOf(0) }
    var openPlaylist by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val opened = playlists.firstOrNull { it.id == openPlaylist }
    val songs = when {
        section == 0 -> liked
        section == 1 -> history
        section == 3 -> saved
        else -> opened?.songs.orEmpty()
    }
    val title = when {
        section == 0 -> "Liked songs"
        section == 1 -> "Recently played"
        section == 3 -> "Saved for offline"
        else -> opened?.name ?: "Playlists"
    }

    fun play(list: List<SavedSong>, start: Int, shuffle: Boolean = false) {
        val c = connection ?: return
        val ordered = if (shuffle) list.shuffled() else list
        c.playQueue(ListQueue(title = title, items = ordered.map { it.toPlayableItem() },
            startIndex = if (shuffle) 0 else start))
        com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false; newName = "" },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true,
                    label = { Text("Name") })
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = playlistStore.create(newName)
                        creating = false; newName = ""
                        openPlaylist = id
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { creating = false; newName = "" }) { Text("Cancel") } },
        )
    }
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onQueue) {
                Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = section == 0, onClick = { section = 0 },
                label = { Text("Liked (${liked.size})") })
            FilterChip(selected = section == 1, onClick = { section = 1 },
                label = { Text("Recent (${history.size})") })
            FilterChip(selected = section == 2, onClick = { section = 2; openPlaylist = null },
                label = { Text("Playlists (${playlists.size})") })
            FilterChip(selected = section == 3, onClick = { section = 3 },
                label = { Text("Saved (${saved.size})") })
        }
        if (section == 2 && opened == null) {
            TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text("New playlist")
            }
            if (playlists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Make a playlist, then add songs from the player.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = com.orismusic.client.ui.bottomInsetPadding()) {
                    itemsIndexed(playlists, key = { _, p -> p.id }) { i, p ->
                        Row(
                            modifier = Modifier.fillMaxWidth().entrance(i).padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .pressClickable({ openPlaylist = p.id }, 0.98f)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(p.songs.firstOrNull()?.thumbnail, Modifier.size(58.dp), RoundedCornerShape(14.dp), px = 300)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text("${p.songs.size} songs", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (section == 2 && opened != null) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { openPlaylist = null }) { Text("All playlists") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { playlistStore.delete(opened.id); openPlaylist = null }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete playlist")
                }
            }
            Text(opened.name, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (songs.isEmpty()) {
                Text("Nothing here yet. Open a song's player and add it to this playlist.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { play(songs, 0) }, enabled = connection != null) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(onClick = { play(songs, 0, shuffle = true) }, enabled = connection != null) {
                        Icon(Icons.Filled.Shuffle, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = com.orismusic.client.ui.bottomInsetPadding()) {
                    itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                        Row(
                            modifier = Modifier.fillMaxWidth().entrance(index).clickable { play(songs, index) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { playlistStore.remove(opened.id, song.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove from playlist")
                            }
                        }
                    }
                }
            }
        } else if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = when (section) {
                        0 -> "Tap the heart on the player to keep a song here."
                        3 -> "Tap the download button on the player to save a song for offline."
                        else -> "Songs you play will show up here."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            Text(
                text = "$title  \u00b7  ${songs.size} ${if (songs.size == 1) "song" else "songs"}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { play(songs, 0) }, enabled = connection != null) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
                OutlinedButton(onClick = { play(songs, 0, shuffle = true) }, enabled = connection != null) {
                    Icon(Icons.Filled.Shuffle, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle")
                }
                Spacer(Modifier.weight(1f))
                if (section == 1) TextButton(onClick = { store.clearHistory() }) { Text("Clear") }
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = com.orismusic.client.ui.bottomInsetPadding()) {
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    Box(Modifier.entrance(index)) {
                    SavedSongRow(
                        song = song,
                        liked = liked.any { it.id == song.id },
                        onClick = { play(songs, index) },
                        onToggleLike = { store.toggleLike(song) },
                        playlists = playlists.map { it.id to it.name },
                        onAddToPlaylist = { id -> playlistStore.add(id, song) },
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedSongRow(
    song: SavedSong,
    liked: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    playlists: List<Pair<String, String>> = emptyList(),
    onAddToPlaylist: (String) -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    SongRow(title = song.title, subtitle = song.artist, thumbnail = song.thumbnail, onClick = onClick) {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add to playlist",
                    tint = MaterialTheme.colorScheme.outline)
            }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (playlists.isEmpty()) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Make a playlist first") }, onClick = { menu = false })
                }
                playlists.forEach { (id, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(name) },
                        onClick = { onAddToPlaylist(id); menu = false },
                    )
                }
            }
        }
        val heart by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (liked) 1.15f else 1f,
            animationSpec = OrisMotion.press(),
            label = "heart",
        )
        IconButton(onClick = onToggleLike) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = if (liked) "Remove from liked songs" else "Like song",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.graphicsLayer { scaleX = heart; scaleY = heart },
            )
        }
    }
}
