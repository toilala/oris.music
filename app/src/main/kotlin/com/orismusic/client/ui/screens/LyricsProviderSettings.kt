package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.orismusic.client.lyrics.*
import com.orismusic.client.settings.LyricsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { LyricsPreferences(context) }
    var options by remember { mutableStateOf(preferences.read()) }
    val uri = LocalUriHandler.current
    fun save(next: LyricsOptions) { preferences.save(next); options = next }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Lyrics providers") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Back") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Automatic tries enabled sources in this order and prefers synced lyrics over plain text. Turn a source off to stop requests to it.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { save(restoreLyricsOptions(emptyList(), null)) }) { Text("Restore default order and sources") }
            }
            itemsIndexed(options.ordered(), key = { _, source -> source.id }) { index, source ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${index+1}. ${source.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = source.available && source.id !in options.disabled,
                                enabled = source.available,
                                modifier = Modifier.semantics { contentDescription = "${source.name} enabled" },
                                onCheckedChange = { save(options.enable(source.id, it)) })
                        }
                        if (source.note.isNotBlank()) Text(source.note, style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { uri.openUri(source.website) }) { Text("Source website") }
                            Spacer(Modifier.weight(1f))
                            IconButton(enabled = index > 0, onClick = { save(options.move(source.id, -1)) }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move ${source.name} up")
                            }
                            IconButton(enabled = index < options.order.lastIndex, onClick = { save(options.move(source.id, 1)) }) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move ${source.name} down")
                            }
                        }
                    }
                }
            }
        }
    }
}
