/*
 * Oris Music home screen.
 *
 * Genuine Oris implementation: loads the real YouTube Music home feed through
 * the app's innertube engine and queues tapped songs through the app's own
 * playback layer.
 */
package com.orismusic.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.SectionHeader
import com.orismusic.client.ui.components.SkeletonCard
import com.orismusic.client.ui.components.entrance
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.components.shimmer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.orismusic.client.utils.PlaybackDiagnostic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HomePage
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.library.LibraryStore
import com.orismusic.client.library.toPlayableItem
import com.orismusic.client.playback.queues.ListQueue
import timber.log.Timber
import kotlinx.coroutines.launch

private sealed interface HomeFeedState {
    data object Loading : HomeFeedState
    data class Ready(val page: HomePage) : HomeFeedState
    data class Failed(val message: String) : HomeFeedState
}

private suspend fun loadHomeFeed(): HomeFeedState =
    YouTube.home().fold(
        onSuccess = { page ->
            // YouTube rotates anonymous home shelves; some rounds carry no song
            // shelves at all. Show whatever came back - playlists included -
            // and only call it empty when nothing parseable arrived.
            if (page.sections.any { section -> section.items.isNotEmpty() }) {
                HomeFeedState.Ready(page)
            } else {
                HomeFeedState.Failed("The feed came back empty")
            }
        },
        onFailure = { HomeFeedState.Failed(it.message ?: "Could not reach YouTube Music") },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpen: (DetailTarget) -> Unit = {},
    modifier: Modifier = Modifier,
    playerConnection: PlayerConnection? = null,
    onSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    var showDiagnostic by remember { androidx.compose.runtime.mutableStateOf(false) }
    val diagnostic = PlaybackDiagnostic.message.collectAsState().value
    if (showDiagnostic) AlertDialog(
        onDismissRequest = { showDiagnostic = false },
        title = { Text("Playback diagnostic") },
        text = { Text(diagnostic) },
        confirmButton = { TextButton(onClick = { showDiagnostic = false }) { Text("Close") } },
    )
    var refreshTick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val feed by produceState<HomeFeedState>(HomeFeedState.Loading, refreshTick) {
        value = loadHomeFeed()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OrisMusic",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    IconButton(onClick = { refreshTick++ }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh home feed",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { outerPadding ->
        val innerPadding = PaddingValues(0.dp)
        Column(Modifier.fillMaxSize().padding(outerPadding)) {
        JumpBackIn(playerConnection)
        Box(Modifier.weight(1f).fillMaxWidth()) {
        when (val state = feed) {
            HomeFeedState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                repeat(2) {
                    Column {
                        Box(
                            Modifier.padding(horizontal = 20.dp, vertical = 10.dp).width(150.dp).height(18.dp)
                                .clip(RoundedCornerShape(8.dp)).shimmer(),
                        )
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) { repeat(3) { SkeletonCard() } }
                    }
                }
            }
            is HomeFeedState.Failed -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Could not load your home feed",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "Tap retry",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { refreshTick++ }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            is HomeFeedState.Ready -> HomeFeed(
                page = state.page,
                playerConnection = playerConnection,
                contentPadding = innerPadding,
                onOpen = onOpen,
            )
        }
        }
        }
    }
}

/** Recently played songs kept on the phone, so Home is useful even when the feed is not. */
@Composable
private fun JumpBackIn(connection: PlayerConnection?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by LibraryStore.get(context).history.collectAsState()
    if (history.isEmpty()) return
    val songs = history.take(12)
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        SectionHeader(title = "Jump back in")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = songs, key = { "back-${it.id}" }) { song ->
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            connection?.playQueue(
                                ListQueue(
                                    title = "Jump back in",
                                    items = songs.map { it.toPlayableItem() },
                                    startIndex = songs.indexOf(song),
                                ),
                            )
                        },
                ) {
                    Artwork(song.thumbnail, Modifier.size(110.dp), RoundedCornerShape(14.dp), song.title)
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(song.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HomeFeed(
    page: HomePage,
    playerConnection: PlayerConnection?,
    contentPadding: PaddingValues,
    onOpen: (DetailTarget) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "new-releases") { NewReleases(onOpen) }
        itemsIndexed(
            items = page.sections.filter { it.items.isNotEmpty() },
            key = { _, it -> it.title },
        ) { sectionIndex, section ->
            Column(Modifier.entrance(sectionIndex)) {
                SectionHeader(title = section.title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val songs = MusicCandidates.select(section.items.filterIsInstance<SongItem>())
                    items(items = section.items.filter { it !is SongItem || it in songs }, key = { "${section.title}-${it.id}" }) { item ->
                        when (item) {
                            is SongItem -> SongCard(
                                song = item,
                                onClick = {
                                    Timber.tag("OrisTap").w(
                                        "SONG TAPPED ${item.title} conn=${playerConnection != null}",
                                    )
                                    playerConnection?.let { connection ->
                                        scope.launch {
                                            connection.playQueue(
                                                MusicRadioQueue(section.title, songs, item.id),
                                            )
                                        }
                                    }
                                },
                            )
                            else -> GenericCard(
                                title = item.title,
                                thumbnail = item.thumbnail,
                                onClick = item.toDetailTarget()?.let { target -> { onOpen(target) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: SongItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .pressClickable(onClick),
    ) {
        Artwork(song.thumbnail, Modifier.size(150.dp), RoundedCornerShape(16.dp), song.title)
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = song.artists.joinToString { it.name },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GenericCard(
    title: String,
    thumbnail: String?,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.width(150.dp)
            .then(if (onClick != null) Modifier.pressClickable(onClick) else Modifier),
    ) {
        Artwork(thumbnail, Modifier.size(150.dp), RoundedCornerShape(16.dp), title)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Fresh albums from the YouTube Music explore page. Hidden when it cannot load. */
@Composable
private fun NewReleases(onOpen: (DetailTarget) -> Unit) {
    val albums by produceState<List<com.music.innertube.models.AlbumItem>>(emptyList()) {
        value = YouTube.explore().getOrNull()?.newReleaseAlbums.orEmpty()
    }
    if (albums.isEmpty()) return
    Column {
        SectionHeader(title = "New releases")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = albums, key = { "new-${it.browseId}" }) { album ->
                GenericCard(
                    title = album.title,
                    thumbnail = album.thumbnail,
                    onClick = { album.toDetailTarget()?.let(onOpen) },
                )
            }
        }
    }
}
