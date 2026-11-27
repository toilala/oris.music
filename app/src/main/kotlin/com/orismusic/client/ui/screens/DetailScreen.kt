package com.orismusic.client.ui.screens

import com.orismusic.client.ui.theme.OrisMotion
import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import com.orismusic.client.ui.components.shimmer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import com.orismusic.client.ui.theme.OrisShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
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
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue
import kotlinx.coroutines.CancellationException

/** What a detail page is about. Serializable so it survives rotation. */
data class DetailTarget(
    val kind: String,
    val id: String,
    val title: String,
    val thumbnail: String?,
) : java.io.Serializable

fun YTItem.toDetailTarget(): DetailTarget? = when (this) {
    is AlbumItem -> DetailTarget("album", browseId, title, thumbnail)
    is ArtistItem -> DetailTarget("artist", id, title, thumbnail)
    is PlaylistItem -> DetailTarget("playlist", id, title, thumbnail)
    else -> null
}

private class DetailContent(
    val title: String,
    val subtitle: String?,
    val thumbnail: String?,
    val songs: List<SongItem>,
    val more: List<Pair<String, List<YTItem>>>,
)

private sealed interface DetailState {
    data object Loading : DetailState
    class Ready(val content: DetailContent) : DetailState
    data object Failed : DetailState
}

/** Album, artist and playlist pages: header, play buttons, songs, and related items. */
@Composable
fun DetailScreen(
    target: DetailTarget,
    connection: PlayerConnection?,
    onBack: () -> Unit,
    onOpen: (DetailTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<DetailState>(DetailState.Loading, target, attempt) {
        value = DetailState.Loading
        val result: Result<DetailContent> = when (target.kind) {
            "album" -> YouTube.album(target.id).map { page ->
                val artists = page.album.artists?.joinToString { it.name }.orEmpty()
                DetailContent(
                    title = page.album.title,
                    subtitle = listOf(artists, page.album.year?.toString().orEmpty()).filter { it.isNotBlank() }
                        .joinToString(" - ").ifBlank { null },
                    thumbnail = page.album.thumbnail,
                    songs = page.songs,
                    more = listOf("Other versions" to page.otherVersions).filter { it.second.isNotEmpty() },
                )
            }
            "artist" -> YouTube.artist(target.id).map { page ->
                val songSection = page.sections.firstOrNull { section -> section.items.any { it is SongItem } }
                DetailContent(
                    title = page.artist.title,
                    subtitle = page.subscriberCountText,
                    thumbnail = page.artist.thumbnail,
                    songs = songSection?.items?.filterIsInstance<SongItem>().orEmpty(),
                    more = page.sections.filter { it !== songSection }
                        .map { section -> section.title to section.items.filter { it.toDetailTarget() != null } }
                        .filter { it.second.isNotEmpty() },
                )
            }
            else -> YouTube.playlist(target.id).map { page ->
                DetailContent(
                    title = page.playlist.title,
                    subtitle = listOfNotNull(page.playlist.author?.name, page.playlist.songCountText).joinToString(" - ")
                        .ifBlank { null },
                    thumbnail = page.playlist.thumbnail,
                    songs = page.songs,
                    more = emptyList(),
                )
            }
        }
        value = result.fold(
            onSuccess = { DetailState.Ready(it) },
            onFailure = {
                if (it is CancellationException) throw it
                DetailState.Failed
            },
        )
    }

    val tint by androidx.compose.animation.animateColorAsState(
        targetValue = com.orismusic.client.ui.components.rememberArtworkColor(target.thumbnail)
            ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = OrisMotion.slow(),
        label = "detail tint",
    )
    Column(
        modifier = modifier
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.65f), MaterialTheme.colorScheme.background), endY = 1400f))
            ,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        when (val s = state) {
            DetailState.Loading -> Column(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(220.dp).clip(if (target.kind == "artist") CircleShape else OrisShapes.Panel).shimmer())
                Spacer(Modifier.height(16.dp))
                Box(Modifier.width(200.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                Spacer(Modifier.height(24.dp))
                repeat(5) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(com.orismusic.client.ui.theme.OrisDimens.ListThumb).clip(OrisShapes.Thumb).shimmer())
                        Spacer(Modifier.width(14.dp))
                        Box(Modifier.width(170.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                    }
                }
            }
            DetailState.Failed -> Column(Modifier.padding(24.dp)) {
                Text("This page could not load. Check your connection and try again.",
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { attempt++ }) { Text("Try again") }
            }
            is DetailState.Ready -> {
                val content = s.content
                fun play(start: Int, shuffle: Boolean = false) {
                    val c = connection ?: return
                    val ordered = if (shuffle) content.songs.shuffled() else content.songs
                    c.playQueue(ListQueue(title = content.title, items = ordered.map { it.toMediaItem() },
                        startIndex = if (shuffle) 0 else start))
                    com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                }
                // A single or one-track release is just a song: play it now instead of showing a page.
                androidx.compose.runtime.LaunchedEffect(content) {
                    if (target.kind != "artist" && content.songs.size == 1 && connection != null) {
                        play(0)
                        onBack()
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = com.orismusic.client.ui.bottomInsetPadding()) {
                    item(key = "header") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val artShape = if (target.kind == "artist") CircleShape else OrisShapes.Panel
                            com.orismusic.client.ui.components.Artwork(
                                url = content.thumbnail ?: target.thumbnail,
                                shape = artShape,
                                modifier = Modifier.size(220.dp)
                                    .shadow(20.dp, artShape),
                                px = 800,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(content.title, style = MaterialTheme.typography.headlineSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            content.subtitle?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(12.dp))
                            if (content.songs.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { play(0) }, enabled = connection != null) {
                                        Icon(Icons.Filled.PlayArrow, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play")
                                    }
                                    OutlinedButton(onClick = { play(0, shuffle = true) }, enabled = connection != null) {
                                        Icon(Icons.Filled.Shuffle, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    itemsIndexed(content.songs, key = { index, song -> "song-$index-${song.id}" }) { index, song ->
                        DetailRow(
                            title = song.title,
                            subtitle = song.artists.joinToString { it.name },
                            thumbnail = song.thumbnail,
                            onClick = { play(index) },
                        )
                    }
                    content.more.forEachIndexed { sectionIndex, (heading, items) ->
                        item(key = "heading-$sectionIndex") {
                            Text(heading, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp))
                        }
                        itemsIndexed(items, key = { index, item -> "more-$sectionIndex-$index-${item.id}" }) { _, item ->
                            val next = item.toDetailTarget()
                            DetailRow(
                                title = item.title,
                                subtitle = when (item) {
                                    is AlbumItem -> "Album" + (item.year?.let { " - $it" } ?: "")
                                    is ArtistItem -> "Artist"
                                    is PlaylistItem -> "Playlist"
                                    else -> ""
                                },
                                thumbnail = item.thumbnail,
                                onClick = { next?.let(onOpen) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(title: String, subtitle: String, thumbnail: String?, onClick: () -> Unit) {
    com.orismusic.client.ui.components.SongRow(title = title, subtitle = subtitle, thumbnail = thumbnail, onClick = onClick)
}


/**
 * Opens an album or playlist page, except a one-track release (a single), which just plays.
 * Decided before any page shows, so there is no flash of an empty album.
 */
suspend fun openOrPlay(target: DetailTarget, connection: PlayerConnection?, onOpen: (DetailTarget) -> Unit) {
    if (target.kind == "album" && connection != null) {
        val songs = YouTube.album(target.id).getOrNull()?.songs.orEmpty()
        if (songs.size == 1) {
            connection.playQueue(ListQueue(title = target.title, items = songs.map { it.toMediaItem() }, startIndex = 0))
            return
        }
    }
    onOpen(target)
}
