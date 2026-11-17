package com.orismusic.client.ui.screens

import android.content.Context
import org.json.JSONArray
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.orismusic.client.search.rememberQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummaryPage
import com.orismusic.client.extensions.toMediaItem
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.queues.MusicRadioQueue
import com.orismusic.client.music.MusicCandidates
import com.music.innertube.pages.SearchSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Ready(val page: SearchSummaryPage) : SearchState
    data class Failed(val message: String) : SearchState
}

/**
 * Online YouTube Music search: one query field, results grouped the way the
 * service summarizes them. Tapping a song queues its result group from that
 * song. Album/artist/playlist detail pages arrive with stage 4, so those
 * rows are display-only for now. Genuine Oris code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    connection: PlayerConnection?,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submittedQuery by rememberSaveable { mutableStateOf("") }
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
    LaunchedEffect(query, submittedQuery) {
        suggestions = emptyList()
        if (query.trim().length >= 2 && query.trim() != submittedQuery) {
            delay(350)
            val response = YouTube.searchSuggestions(query.trim())
            response.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            suggestions = response.getOrNull()?.queries.orEmpty().take(5)
        }
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

    val results by produceState<SearchState>(SearchState.Idle, submittedQuery, attempts) {
        if (submittedQuery.isBlank()) {
            value = SearchState.Idle
        } else {
            value = SearchState.Loading
            val response = coroutineScope {
                val summary = async { YouTube.searchSummary(submittedQuery) }
                val catalog = async { YouTube.search(submittedQuery, YouTube.SearchFilter.FILTER_SONG) }
                val summaryResult = summary.await()
                val songResult = catalog.await()
                summaryResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                songResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                if (summaryResult.isFailure && songResult.isFailure) summaryResult else {
                    val groups = summaryResult.getOrNull()?.summaries.orEmpty()
                    val songs = MusicCandidates.select(songResult.getOrNull()?.items.orEmpty().filterIsInstance<SongItem>() +
                        groups.flatMap { it.items }.filterIsInstance<SongItem>())
                    Result.success(SearchSummaryPage(
                        listOf(SearchSummary("Songs", songs)) + groups.map { group ->
                            group.copy(items = group.items.filter { it is AlbumItem || it is ArtistItem })
                        }.filter { it.items.isNotEmpty() }
                    ))
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
                label = { Text("Search music") },
                placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = {
                        query = ""; submittedQuery = ""; suggestions = emptyList()
                        keyboard?.hide(); focus.clearFocus()
                    }) { Icon(Icons.Filled.Clear, "Clear search") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { submit(query) },
                ),
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = { submit(query) }, enabled = query.isNotBlank()) { Text("Search") }
            }
            if (suggestions.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    suggestions.forEach { suggestion ->
                        TextButton(onClick = { submit(suggestion) }, modifier = Modifier.fillMaxWidth()) {
                            Text(suggestion, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (submittedQuery.isBlank() && recent.isNotEmpty() && suggestions.isEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        recent = emptyList(); preferences.edit().remove("recent").apply()
                    }) { Text("Clear history") }
                }
                recent.take(6).forEach { text ->
                    TextButton(onClick = { submit(text) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(text, Modifier.fillMaxWidth())
                    }
                }
            }
            when (val state = results) {
                SearchState.Idle -> Text(
                    text = "Search YouTube Music",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                SearchState.Loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Searching...", style = MaterialTheme.typography.bodyLarge)
                }
                is SearchState.Failed -> Column(Modifier.padding(24.dp)) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { submit(submittedQuery) }) { Text("Try again") }
                }
                is SearchState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    state.page.summaries.forEachIndexed { groupIndex, summary ->
                        if (summary.items.isEmpty()) return@forEachIndexed
                        item(key = "header-$groupIndex") {
                            Text(
                                text = summary.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        val songs = summary.items.filterIsInstance<SongItem>()
                        summary.items.forEachIndexed { rowIndex, result ->
                            item(key = "result-$groupIndex-$rowIndex") {
                                SearchResultRow(
                                    item = result,
                                    onClick = if (result is SongItem && connection != null) {
                                        {
                                            scope.launch {
                                                connection.playQueue(
                                                    MusicRadioQueue(summary.title, songs, result.id),
                                                )
                                            }
                                        }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: YTItem,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (item) {
                    is SongItem -> item.artists.joinToString { it.name }
                        .ifBlank { "Song" }
                    is AlbumItem -> "Album" + (item.artists?.firstOrNull()?.name?.let { " - $it" } ?: "")
                    is ArtistItem -> "Artist"
                    is PlaylistItem -> "Playlist" + (item.author?.name?.let { " - $it" } ?: "")
                    else -> "Result"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
