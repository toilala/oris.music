package com.orismusic.client.ui.screens

import android.content.Context
import org.json.JSONArray
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage
import com.orismusic.client.music.MusicCandidates
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.search.rememberQuery
import com.orismusic.client.ui.bottomInsetPadding
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.SongRow
import com.orismusic.client.ui.components.entrance
import com.orismusic.client.ui.components.pressClickable
import com.orismusic.client.ui.components.shimmer
import com.orismusic.client.ui.navigation.PlayerLaunch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(val page: SearchSummaryPage) : SearchState
    data class Failed(val message: String) : SearchState
}

private enum class SearchTab(val label: String) {
    All("All"), Songs("Songs"), Albums("Albums"), Artists("Artists"), Playlists("Playlists"),
}

/** A search another screen wants the Search tab to run. */
object SearchRequest {
    var pending by mutableStateOf<String?>(null)
}

/**
 * Online YouTube Music search. A pill field with live suggestions, filter chips, a
 * top-result card, song rows with a playing marker, and entrance motion. The look
 * follows Metrolist/OuterTune conventions (filters, grouped summary) in genuine Oris code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    connection: PlayerConnection?,
    modifier: Modifier = Modifier,
    onOpen: (DetailTarget) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(SearchTab.All) }
    val scope = rememberCoroutineScope()
    val preferences = LocalContext.current.getSharedPreferences("oris-search", Context.MODE_PRIVATE)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var recent by remember {
        mutableStateOf(runCatching {
            val values = JSONArray(preferences.getString("recent", "[]"))
            (0 until values.length()).map { values.getString(it) }
        }.getOrDefault(emptyList<String>()))
    }
    var attempts by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    // The song that is loaded right now, so its row can wear the playing marker.
    val mediaIndex = connection?.currentMediaItemIndex?.collectAsState()?.value ?: -1
    val isPlaying = connection?.isPlaying?.collectAsState()?.value ?: false
    val currentId = remember(mediaIndex, connection) {
        runCatching { connection?.player?.currentMediaItem?.mediaId }.getOrNull()
    }

    fun submit(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        query = clean
        submittedQuery = clean
        attempts++
        suggestions = emptyList()
        recent = rememberQuery(recent, clean)
        preferences.edit().putString("recent", JSONArray(recent).toString()).apply()
        keyboard?.hide()
        focus.clearFocus()
    }

    LaunchedEffect(query) {
        suggestions = emptyList()
        val clean = query.trim()
        if (clean.length < 2 || clean == submittedQuery) return@LaunchedEffect
        delay(320)
        val response = YouTube.searchSuggestions(clean)
        response.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        suggestions = response.getOrNull()?.queries.orEmpty().take(5)
        // Hands-free search: a short pause after typing runs the query on its own.
        delay(400)
        if (query.trim() == clean && clean != submittedQuery) submit(clean)
    }

    // Mood chips on Home hand their search over through here.
    val handoff = SearchRequest.pending
    LaunchedEffect(handoff) {
        if (handoff != null) { SearchRequest.pending = null; submit(handoff) }
    }

    val results by produceState<SearchState>(SearchState.Idle, submittedQuery, attempts, tab) {
        if (submittedQuery.isBlank()) {
            value = SearchState.Idle
        } else {
            value = SearchState.Loading
            val response: Result<SearchSummaryPage> = coroutineScope {
                when (tab) {
                    SearchTab.All -> {
                        val summary = async { YouTube.searchSummary(submittedQuery) }
                        val catalog = async { YouTube.search(submittedQuery, YouTube.SearchFilter.FILTER_SONG) }
                        val summaryResult = summary.await()
                        val songResult = catalog.await()
                        summaryResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        songResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                        if (summaryResult.isFailure && songResult.isFailure) {
                            summaryResult.map { SearchSummaryPage(emptyList()) }
                        } else {
                            val groups = summaryResult.getOrNull()?.summaries.orEmpty()
                            val songs = MusicCandidates.select(
                                songResult.getOrNull()?.items.orEmpty().filterIsInstance<SongItem>() +
                                    groups.flatMap { it.items }.filterIsInstance<SongItem>(),
                            )
                            Result.success(
                                SearchSummaryPage(
                                    listOf(SearchSummary("Songs", songs)) + groups.map { group ->
                                        group.copy(items = group.items.filter { it is AlbumItem || it is ArtistItem || it is PlaylistItem })
                                    }.filter { it.items.isNotEmpty() },
                                ),
                            )
                        }
                    }
                    else -> {
                        val filter = when (tab) {
                            SearchTab.Songs -> YouTube.SearchFilter.FILTER_SONG
                            SearchTab.Albums -> YouTube.SearchFilter.FILTER_ALBUM
                            SearchTab.Artists -> YouTube.SearchFilter.FILTER_ARTIST
                            else -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                        }
                        YouTube.search(submittedQuery, filter).map { result ->
                            val items: List<YTItem> = when (tab) {
                                SearchTab.Songs -> MusicCandidates.select(result.items.filterIsInstance<SongItem>())
                                SearchTab.Albums -> result.items.filterIsInstance<AlbumItem>()
                                SearchTab.Artists -> result.items.filterIsInstance<ArtistItem>()
                                else -> result.items.filterIsInstance<PlaylistItem>()
                            }
                            SearchSummaryPage(listOf(SearchSummary(tab.label, items)))
                        }
                    }
                }
            }
            value = response.fold(
                onSuccess = { page ->
                    if (page.summaries.any { it.items.isNotEmpty() }) {
                        SearchState.Ready(page)
                    } else {
                        SearchState.Failed("No results for \"$submittedQuery\"")
                    }
                },
                onFailure = {
                    if (it is CancellationException) throw it
                    SearchState.Failed("Search could not load. Check your connection and try again.")
                },
            )
        }
    }

    fun playSong(groupTitle: String, groupSongs: List<SongItem>, song: SongItem) {
        val c = connection ?: return
        scope.launch { c.playQueue(MusicRadioQueue(groupTitle, groupSongs, song.id)) }
        PlayerLaunch.maybeOpen()
    }

    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = {
                        query = ""; submittedQuery = ""; suggestions = emptyList()
                        keyboard?.hide(); focus.clearFocus()
                    }) { Icon(Icons.Filled.Clear, "Clear search") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { submit(query) },
                ),
            )

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchTab.entries.forEach { option ->
                    FilterChip(
                        selected = tab == option,
                        onClick = { tab = option },
                        label = { Text(option.label) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            if (suggestions.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
                    items(suggestions.size, key = { "sug-$it" }) { i ->
                        val suggestion = suggestions[i]
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { submit(suggestion) }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(suggestion, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                return@Column
            }

            when (val state = results) {
                SearchState.Idle -> Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                ) {
                    if (recent.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                recent = emptyList(); preferences.edit().remove("recent").apply()
                            }) { Text("Clear") }
                        }
                        recent.take(6).forEach { text ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { submit(text) }
                                    .padding(horizontal = 4.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(16.dp))
                                Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Search YouTube Music",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Lo-fi beats", "Workout", "Nepali hits", "Chill", "Focus").forEach { tag ->
                            AssistChip(onClick = { submit(tag) }, label = { Text(tag) })
                        }
                    }
                }
                SearchState.Loading -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Searching \"$submittedQuery\"", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(12.dp))
                    repeat(6) {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).shimmer())
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Box(Modifier.width(180.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).shimmer())
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.width(110.dp).height(11.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                            }
                        }
                    }
                }
                is SearchState.Failed -> Column(Modifier.padding(24.dp)) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { attempts++ }) { Text("Try again") }
                }
                is SearchState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = bottomInsetPadding()) {
                    val songsGroup = state.page.summaries.firstOrNull { g -> g.title == "Songs" }
                    val topSong = if (tab == SearchTab.All) songsGroup?.items?.filterIsInstance<SongItem>()?.firstOrNull() else null
                    var rowNumber = 0

                    if (topSong != null && songsGroup != null) {
                        item(key = "top-result") {
                            TopResultCard(
                                song = topSong,
                                playing = currentId == topSong.id,
                                modifier = Modifier.entrance(rowNumber++),
                                onClick = { playSong("Top result", songsGroup.items.filterIsInstance<SongItem>(), topSong) },
                            )
                        }
                    }

                    state.page.summaries.forEachIndexed { groupIndex, summary ->
                        if (summary.items.isEmpty()) return@forEachIndexed
                        val groupSongs = summary.items.filterIsInstance<SongItem>()
                        item(key = "header-$groupIndex") {
                            Text(
                                text = summary.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        summary.items.forEachIndexed { rowIndex, result ->
                            val n = rowNumber++
                            item(key = "result-$groupIndex-$rowIndex-${result.id}") {
                                when (result) {
                                    is SongItem -> SongRow(
                                        title = result.title,
                                        subtitle = result.artists.joinToString { it.name }.ifBlank { "Song" },
                                        thumbnail = result.thumbnail,
                                        highlight = currentId == result.id,
                                        modifier = Modifier.entrance(n),
                                        onClick = { playSong(summary.title, groupSongs, result) },
                                        trailing = {
                                            if (currentId == result.id) PlayingBars(isPlaying)
                                        },
                                    )
                                    else -> SongRow(
                                        title = result.title,
                                        subtitle = when (result) {
                                            is AlbumItem -> "Album" + (result.artists?.firstOrNull()?.name?.let { " - $it" } ?: "")
                                            is ArtistItem -> "Artist"
                                            is PlaylistItem -> "Playlist" + (result.author?.name?.let { " - $it" } ?: "")
                                            else -> "Result"
                                        },
                                        thumbnail = result.thumbnail,
                                        modifier = Modifier.entrance(n),
                                        onClick = { result.toDetailTarget()?.let(onOpen) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Big card for the closest match: larger artwork, the title and a play key. */
@Composable
private fun TopResultCard(song: SongItem, playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Top result", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (playing) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surfaceContainer,
                )
                .pressClickable(onClick, 0.98f)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumbnail, Modifier.size(96.dp), RoundedCornerShape(16.dp), song.title, px = 500)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    song.artists.joinToString { it.name }.ifBlank { "Song" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onTertiary)
            }
        }
    }
}

/** Little equalizer bars on the row of the song that is loaded. */
@Composable
private fun PlayingBars(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "bars")
    Row(
        Modifier.padding(end = 8.dp).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        repeat(3) { i ->
            val level by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(420 + i * 130),
                    androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "bar$i",
            )
            val h = if (playing) level else 0.3f
            Box(
                Modifier
                    .width(3.dp)
                    .height((4 + 12 * h).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
