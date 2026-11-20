package com.orismusic.client.ui.screens

import com.orismusic.client.settings.HomeSection
import com.orismusic.client.settings.JumpStyle
import com.orismusic.client.settings.ShelfStyle
import com.orismusic.client.settings.HomeSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.SongItem
import com.orismusic.client.library.SavedSong
import com.orismusic.client.library.toPlayableItem
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.ListQueue
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.OrbitRow
import com.orismusic.client.ui.components.SectionHeader
import com.orismusic.client.ui.components.entrance
import com.orismusic.client.ui.components.pressClickable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What the phone knows about the listener: ranked songs and artists, from real plays and likes. */
class Taste(val mostPlayed: List<Pair<SavedSong, Int>>, val artists: List<String>) {
    companion object {
        fun from(history: List<SavedSong>, liked: List<SavedSong>, counts: Map<String, Int>): Taste {
            val known = (history + liked).distinctBy { it.id }
            val ranked = known.withIndex()
                .sortedWith(compareByDescending<IndexedValue<SavedSong>> { counts[it.value.id] ?: 1 }.thenBy { it.index })
                .map { it.value to (counts[it.value.id] ?: 1) }
            val weight = linkedMapOf<String, Int>()
            val shown = HashMap<String, String>()
            known.forEach { s ->
                val n = counts[s.id] ?: 1
                s.artist.split(", ").map { it.trim() }.filter { it.isNotBlank() }
                    .forEach { name ->
                        val k = name.lowercase()
                        shown.getOrPut(k) { name }
                        weight[k] = (weight[k] ?: 0) + n
                    }
            }
            val artists = weight.entries.sortedByDescending { it.value }.map { shown.getValue(it.key) }.take(8)
            return Taste(ranked.take(5), artists)
        }
    }
}

class ArtistShelf(val artist: ArtistItem, val songs: List<SongItem>, val albums: List<AlbumItem>)

private val shelfCache = HashMap<String, ArtistShelf?>()

/** Looks each artist up on YouTube Music. Only an exact name match counts, so a shelf is never about someone else. */
suspend fun loadArtistShelves(names: List<String>): List<ArtistShelf> = coroutineScope {
    names.map { n ->
        async {
            if (shelfCache.containsKey(n)) return@async shelfCache[n]
            val found = YouTube.search(n, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()?.items
                ?.filterIsInstance<ArtistItem>()?.firstOrNull { it.title.equals(n, ignoreCase = true) }
            val page = found?.let { YouTube.artist(it.id).getOrNull() }
            val all = page?.sections?.flatMap { it.items }.orEmpty()
            val shelf = if (found == null || page == null) null else
                ArtistShelf(found, MusicCandidates.select(all.filterIsInstance<SongItem>()), all.filterIsInstance<AlbumItem>())
            if (found != null) shelfCache[n] = shelf
            shelf
        }
    }.awaitAll().filterNotNull().distinctBy { it.artist.id }
}

private fun playHistory(c: PlayerConnection?, title: String, songs: List<SavedSong>, song: SavedSong) {
    c?.playQueue(ListQueue(title = title, items = songs.map { it.toPlayableItem() }, startIndex = songs.indexOf(song)))
    com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
}

fun LazyListScope.listeningShelves(
    history: List<SavedSong>,
    taste: Taste,
    shelves: List<ArtistShelf>,
    connection: PlayerConnection?,
    onOpen: (DetailTarget) -> Unit,
    scope: CoroutineScope,
) {
    val blocks = HashMap<HomeSection, LazyListScope.() -> Unit>()
    val uniqueShelves = shelves.distinctBy { it.artist.id }
    val lively = HomeSettings.motion == com.orismusic.client.settings.HomeMotion.Lively
    blocks[HomeSection.Jump] = {
    if (history.isNotEmpty()) item(key = "jump") {
        Column(if (lively) Modifier.entrance(0) else Modifier) {
            SectionHeader(title = "Jump back in")
            val tiles = history.take(4)
            if (HomeSettings.jumpStyle == JumpStyle.Strip) LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(tiles, key = { "jb-${it.id}" }) { s ->
                    Column(Modifier.width(HomeSettings.coverSize.dp(104)).pressClickable({ playHistory(connection, "Jump back in", tiles, s) }, 0.96f)) {
                        Artwork(s.thumbnail, Modifier.size(HomeSettings.coverSize.dp(104)), HomeSettings.corners.shape(16), s.title)
                        Text(s.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            } else Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { s ->
                            Row(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.07f))
                                    .pressClickable({ playHistory(connection, "Jump back in", tiles, s) }, 0.97f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(s.thumbnail, Modifier.size(54.dp), RoundedCornerShape(0.dp), s.title)
                                Text(s.title, style = MaterialTheme.typography.labelLarge, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
    }
    blocks[HomeSection.Recent] = {
    val older = history.drop(4).take(12)
    if (older.isNotEmpty()) item(key = "recent") {
        Column {
            SectionHeader(title = "Recently played")
            if (HomeSettings.shelfStyle == ShelfStyle.Scatter) LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy((-22).dp),
            ) {
                itemsIndexed(older, key = { _, it -> "rs-${it.id}" }) { i, s ->
                    val size = HomeSettings.coverSize.dp(150)
                    Column((if (lively) Modifier.entrance(i) else Modifier).width(size).graphicsLayer {
                        rotationZ = if (i % 2 == 0) -3.5f else 3f
                        translationY = if (i % 2 == 0) 0f else 12.dp.toPx()
                    }.pressClickable({ playHistory(connection, "Recently played", older, s) }, 0.95f)) {
                        Artwork(s.thumbnail, Modifier.size(size), HomeSettings.corners.shape(24), s.title)
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                    }
                }
            } else OrbitRow(items = older, key = { "rp-${it.id}" }) { s, _, orbit ->
                Column(orbit.width(HomeSettings.coverSize.dp(150)).pressClickable({ playHistory(connection, "Recently played", older, s) })) {
                    Artwork(s.thumbnail, Modifier.size(HomeSettings.coverSize.dp(150)), HomeSettings.corners.shape(24), s.title)
                    Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(s.artist, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
    blocks[HomeSection.Most] = {
    val top = taste.mostPlayed
    // A ranking only means something once at least one song has been played more than once.
    if (top.isNotEmpty() && top.first().second > 1) item(key = "most") {
        Column {
            SectionHeader(title = "Most played", subtitle = "Counted on this phone")
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(vertical = 6.dp),
            ) {
                top.forEachIndexed { i, (s, n) ->
                    Row(
                        Modifier.fillMaxWidth().pressClickable({
                            playHistory(connection, "Most played", top.map { it.first }, s)
                        }, 0.98f).padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.headlineSmall,
                            color = if (i == 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(30.dp))
                        Artwork(s.thumbnail, Modifier.size(52.dp), RoundedCornerShape(12.dp), s.title)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.artist, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (n > 1) Text("$n plays", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    }
    blocks[HomeSection.Because] = {
    uniqueShelves.take(HomeSettings.becauseCount).filter { it.songs.isNotEmpty() }.forEach { shelf ->
        item(key = "because-${shelf.artist.id}") {
            Column {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(shelf.artist.thumbnail, Modifier.size(46.dp), CircleShape, shelf.artist.title)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Because you listen to", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(shelf.artist.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(shelf.songs.distinctBy { it.id }.take(12), key = { _, it -> "bc-${shelf.artist.id}-${it.id}" }) { i, s ->
                        Column((if (lively) Modifier.entrance(i) else Modifier).width(HomeSettings.coverSize.dp(140)).pressClickable({
                            val c = connection
                            if (c != null) scope.launch { c.playQueue(MusicRadioQueue(shelf.artist.title, shelf.songs, s.id)) }
                            com.orismusic.client.ui.navigation.PlayerLaunch.maybeOpen()
                        })) {
                            Artwork(s.thumbnail, Modifier.size(HomeSettings.coverSize.dp(140)), HomeSettings.corners.shape(16), s.title)
                            Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            Text(s.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    }
    blocks[HomeSection.History] = {
    val rows = history.drop(16).take(5)
    if (rows.isNotEmpty()) item(key = "history") {
        Column {
            SectionHeader(title = "History")
            rows.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().pressClickable({ playHistory(connection, "History", rows, s) }, 0.98f)
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(s.thumbnail, Modifier.size(48.dp), RoundedCornerShape(10.dp), s.title)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.artist, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    }
    blocks[HomeSection.FromArtists] = {
    val releases = uniqueShelves.flatMap { it.albums }.distinctBy { it.browseId }.take(12)
    if (releases.isNotEmpty()) item(key = "from-artists") {
        Column {
            SectionHeader(title = "New from your artists", subtitle = "Albums and singles from artists you play")
            OrbitRow(items = releases, key = { "na-${it.browseId}" }) { a, _, orbit ->
                Column(orbit.width(HomeSettings.coverSize.dp(150)).pressClickable({ a.toDetailTarget()?.let(onOpen) })) {
                    Artwork(a.thumbnail, Modifier.size(HomeSettings.coverSize.dp(150)), HomeSettings.corners.shape(16), a.title)
                    Text(a.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(a.artists?.joinToString { it.name }.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
    blocks[HomeSection.Artists] = {
    if (uniqueShelves.isNotEmpty()) item(key = "artist-list") {
        Column {
            SectionHeader(title = "Your artists")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(uniqueShelves, key = { "al-${it.artist.id}" }) { shelf ->
                    Column(
                        Modifier.width(96.dp).pressClickable({ onOpen(DetailTarget("artist", shelf.artist.id, shelf.artist.title, shelf.artist.thumbnail)) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(shelf.artist.thumbnail, Modifier.size(96.dp), CircleShape, shelf.artist.title)
                        Text(shelf.artist.title, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
    }
    HomeSettings.order.toList().forEach { sec -> if (HomeSettings.visible(sec)) blocks[sec]?.invoke(this) }
}
