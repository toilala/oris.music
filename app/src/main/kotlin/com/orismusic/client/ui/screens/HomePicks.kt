package com.orismusic.client.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.orismusic.client.library.DiscoveryFilters
import com.orismusic.client.library.rankedByTaste
import com.orismusic.client.library.withoutFiltered
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.SectionHeader
import com.orismusic.client.ui.components.entrance
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.theme.OrisDimens
import com.orismusic.client.ui.theme.OrisShapes
import com.orismusic.client.ui.components.pressCombinedClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val ROWS_PER_PAGE = 4
private const val PICKS_PER_PAGE = 8

/** The home feed's own song shelf to show as the Quick picks list: the shelf the feed
 * labels for quick listening when it carries one, else its strongest song shelf.
 * Returns the picks and the consumed shelf's title so it is not repeated as cards. */
fun chooseQuickPicks(page: HomePage, filters: DiscoveryFilters, tasteArtists: List<String>): Pair<List<SongItem>, String>? {
    val shelves = page.sections.filter { s -> s.items.count { it is SongItem } >= ROWS_PER_PAGE * 2 }
    val chosen = shelves.firstOrNull { it.title.contains("quick", ignoreCase = true) }
        ?: shelves.maxByOrNull { s -> s.items.count { it is SongItem } } ?: return null
    val raw = MusicCandidates.select(chosen.items.filterIsInstance<SongItem>()).distinctBy { it.id }
    if (raw.size < ROWS_PER_PAGE * 2) return null
    // The listener's filters run on the raw shelf; whatever survives, even nothing, is
    // what the section shows - filtered-out songs never come back through a fallback.
    val picks = raw
        .withoutFiltered(filters, { it.id }, { s -> s.artists.map { it.name } })
        .rankedByTaste(tasteArtists) { s -> s.artists.map { it.name } }
        .take(ROWS_PER_PAGE * 4)
    return picks to chosen.title
}

/** Anonymous feeds can carry only playlists. Use actual songs from one of those
 * recommendations rather than making Quick picks disappear for new listeners. */
suspend fun loadQuickPicks(page: HomePage, filters: DiscoveryFilters, tasteArtists: List<String>): Pair<List<SongItem>, String>? {
    chooseQuickPicks(page, filters, tasteArtists)?.let { return it }
    val playlists = page.sections.flatMap { it.items.filterIsInstance<PlaylistItem>() }
        .distinctBy { it.id }.take(3)
    for (playlist in playlists) {
        val songs = YouTube.playlist(playlist.id).getOrNull()?.songs.orEmpty()
        val picks = MusicCandidates.select(songs).distinctBy { it.id }
            .withoutFiltered(filters, { it.id }, { s -> s.artists.map { it.name } })
            .rankedByTaste(tasteArtists) { s -> s.artists.map { it.name } }
            .take(16)
        if (picks.size >= ROWS_PER_PAGE) return picks to ""
    }
    return null
}

private fun mmss(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Quick picks: pages of artwork chips with a Play all pill, straight from the home feed. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickPicksSection(
    songs: List<SongItem>,
    connection: PlayerConnection?,
    scope: CoroutineScope,
    onHide: (SongItem) -> Unit = {},
    onDislike: (SongItem) -> Unit = {},
    onBlockArtist: (String) -> Unit = {},
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary))
                Spacer(Modifier.width(9.dp))
                Text("Quick picks", style = MaterialTheme.typography.titleLarge,
                    fontFamily = com.orismusic.client.ui.theme.DisplaySerif)
            }
            if (songs.isNotEmpty()) Row(
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
        if (songs.isEmpty()) {
            Text(
                "Your filters hide these picks. Manage them in Home settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageCount = (songs.size + PICKS_PER_PAGE - 1) / PICKS_PER_PAGE
            val pager = rememberPagerState(pageCount = { pageCount })
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(maxWidth - 40.dp),
                contentPadding = PaddingValues(start = 20.dp),
                pageSpacing = 10.dp,
                modifier = Modifier.padding(top = 8.dp),
            ) { page ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    songs.drop(page * PICKS_PER_PAGE).take(PICKS_PER_PAGE).chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEachIndexed { col, song ->
                                QuickPickChip(
                                    song = song,
                                    modifier = Modifier.weight(1f).entrance(page * PICKS_PER_PAGE + row * 2 + col),
                                    onClick = { playFrom(page * PICKS_PER_PAGE + row * 2 + col) },
                                    onPlayNext = connection?.let { c -> { c.playNext(song.toMediaItem()) } },
                                    onAddToQueue = connection?.let { c -> { c.addToQueue(song.toMediaItem()) } },
                                    onStartRadio = connection?.let { c ->
                                        {
                                            scope.launch { c.playQueue(MusicRadioQueue("Quick picks", songs, song.id)) }
                                            com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                                        }
                                    },
                                    onHide = { onHide(song) },
                                    onDislike = { onDislike(song) },
                                    onBlockArtist = song.artists.firstOrNull()?.name?.let { name -> { onBlockArtist(name) } },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** One quick pick: an artwork chip. Tap plays it; a long press opens its menu. */
@Composable
private fun QuickPickChip(
    song: SongItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onStartRadio: (() -> Unit)?,
    onHide: (() -> Unit)?,
    onDislike: (() -> Unit)?,
    onBlockArtist: (() -> Unit)?,
) {
    Box(modifier) {
        var menu by remember { mutableStateOf(false) }
        Row(
            Modifier.fillMaxWidth().clip(OrisShapes.Row)
                .pressCombinedClickable(
                    onClick,
                    onLongClick = { menu = true },
                    pressedScale = 0.97f,
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumbnail, Modifier.size(OrisDimens.ListThumb), OrisShapes.Thumb, song.title, px = 160)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append(song.artists.joinToString { it.name })
                    song.duration?.let { append(" \u00b7 ").append(mmss(it)) }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (menu) {
            com.orismusic.client.ui.components.SongActionsSheet(
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                thumbnail = song.thumbnail,
                actions = mutableListOf<com.orismusic.client.ui.components.SongAction>().apply {
                    if (onPlayNext != null) add(com.orismusic.client.ui.components.SongAction("Play next") { onPlayNext(); menu = false })
                    if (onAddToQueue != null) add(com.orismusic.client.ui.components.SongAction("Add to queue") { onAddToQueue(); menu = false })
                    if (onStartRadio != null) add(com.orismusic.client.ui.components.SongAction("Start radio") { onStartRadio(); menu = false })
                    if (onHide != null) add(com.orismusic.client.ui.components.SongAction("Hide song") { onHide(); menu = false })
                    if (onDislike != null) add(com.orismusic.client.ui.components.SongAction("Dislike song") { onDislike(); menu = false })
                    if (onBlockArtist != null) add(com.orismusic.client.ui.components.SongAction("Block ${song.artists.first().name}",
                        tint = MaterialTheme.colorScheme.error) { onBlockArtist(); menu = false })
                },
                onDismiss = { menu = false },
            )
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
            itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
                Column(
                    Modifier.entrance(index).width(OrisDimens.GridCard).clip(OrisShapes.Card)
                        .pressClickable({ playlist.toDetailTarget()?.let(onOpen) }),
                ) {
                    Artwork(playlist.thumbnail, Modifier.size(OrisDimens.GridCard), OrisShapes.Card, playlist.title, px = 300)
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
