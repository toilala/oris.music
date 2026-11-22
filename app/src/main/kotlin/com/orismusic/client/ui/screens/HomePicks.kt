package com.orismusic.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.pages.HomePage
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.SectionHeader
import com.orismusic.client.ui.components.pressClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ROWS_PER_PAGE = 4

/** The home feed's own song shelf to show as the Quick picks list: the shelf the feed
 * labels for quick listening when it carries one, else its strongest song shelf.
 * Returns the picks and the consumed shelf's title so it is not repeated as cards. */
fun chooseQuickPicks(page: HomePage): Pair<List<SongItem>, String>? {
    val shelves = page.sections.filter { s -> s.items.count { it is SongItem } >= ROWS_PER_PAGE * 2 }
    val chosen = shelves.firstOrNull { it.title.contains("quick", ignoreCase = true) }
        ?: shelves.maxByOrNull { s -> s.items.count { it is SongItem } } ?: return null
    val picks = MusicCandidates.select(chosen.items.filterIsInstance<SongItem>())
        .distinctBy { it.id }.take(ROWS_PER_PAGE * 4)
    if (picks.size < ROWS_PER_PAGE * 2) return null
    return picks to chosen.title
}

/** Anonymous feeds can carry only playlists. Use actual songs from one of those
 * recommendations rather than making Quick picks disappear for new listeners. */
suspend fun loadQuickPicks(page: HomePage): Pair<List<SongItem>, String>? {
    chooseQuickPicks(page)?.let { return it }
    val playlists = page.sections.flatMap { it.items.filterIsInstance<PlaylistItem>() }
        .distinctBy { it.id }.take(3)
    for (playlist in playlists) {
        val songs = YouTube.playlist(playlist.id).getOrNull()?.songs.orEmpty()
        val picks = MusicCandidates.select(songs).distinctBy { it.id }.take(16)
        if (picks.size >= ROWS_PER_PAGE) return picks to ""
    }
    return null
}

private fun mmss(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Quick picks: pages of song rows with a Play all pill, straight from the home feed. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickPicksSection(
    songs: List<SongItem>,
    connection: PlayerConnection?,
    scope: CoroutineScope,
) {
    fun playFrom(index: Int) {
        connection ?: return
        scope.launch {
            connection.playQueue(ListQueue("Quick picks", songs.map { it.toMediaItem() }, index))
        }
        com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Quick picks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.tertiary)
                    .pressClickable({ playFrom(0) }, 0.94f)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Play all", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiary)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageCount = (songs.size + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE
            val pager = rememberPagerState(pageCount = { pageCount })
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(maxWidth - 40.dp),
                contentPadding = PaddingValues(start = 20.dp),
                pageSpacing = 8.dp,
                modifier = Modifier.padding(top = 6.dp),
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    songs.drop(page * ROWS_PER_PAGE).take(ROWS_PER_PAGE).forEachIndexed { row, song ->
                        QuickPickRow(
                            song = song,
                            onClick = { playFrom(page * ROWS_PER_PAGE + row) },
                            onPlayNext = connection?.let { c -> { c.playNext(song.toMediaItem()) } },
                            onAddToQueue = connection?.let { c -> { c.addToQueue(song.toMediaItem()) } },
                            onStartRadio = connection?.let { c ->
                                {
                                    scope.launch { c.playQueue(MusicRadioQueue("Quick picks", songs, song.id)) }
                                    com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** One quick pick: art, title, artist and length, and a real menu. */
@Composable
private fun QuickPickRow(
    song: SongItem,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onStartRadio: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .pressClickable(onClick, 0.98f)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.thumbnail, Modifier.size(52.dp), RoundedCornerShape(10.dp), song.title, px = 160)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(song.artists.joinToString { it.name })
                song.duration?.let { append(" \u00b7 ").append(mmss(it)) }
            }
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onPlayNext != null) {
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${song.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { onPlayNext(); menu = false })
                    if (onAddToQueue != null) DropdownMenuItem(text = { Text("Add to queue") }, onClick = { onAddToQueue(); menu = false })
                    if (onStartRadio != null) DropdownMenuItem(text = { Text("Start radio") }, onClick = { onStartRadio(); menu = false })
                }
            }
        }
    }
}

private val communityCache = HashMap<String, List<PlaylistItem>>()

/** Community playlists about the listener's top artists, from real playlist search. */
suspend fun loadCommunityPlaylists(artists: List<String>): List<PlaylistItem> = coroutineScope {
    artists.take(3).map { name ->
        async {
            communityCache[name] ?: runCatching {
                YouTube.search(name, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrNull()
                    ?.items?.filterIsInstance<PlaylistItem>()?.take(6).orEmpty()
            }.getOrDefault(emptyList()).also { communityCache[name] = it }
        }
    }.awaitAll().flatten().distinctBy { it.id }.take(10)
}

/** A row of community playlists tied to what the listener actually plays. */
@Composable
fun CommunityPlaylistsSection(
    playlists: List<PlaylistItem>,
    onOpen: (DetailTarget) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title = "Community playlists")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(16.dp))
                        .clickable { playlist.toDetailTarget()?.let(onOpen) },
                ) {
                    Artwork(playlist.thumbnail, Modifier.size(150.dp), RoundedCornerShape(16.dp), playlist.title, px = 300)
                    Text(
                        playlist.title, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val byline = playlist.author?.name ?: playlist.songCountText.orEmpty()
                    if (byline.isNotEmpty()) Text(
                        byline, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
