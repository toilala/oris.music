package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.orismusic.client.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { LyricsPreferences(context) }
    var options by remember { mutableStateOf(preferences.read()) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    fun save(next: LyricsOptions) { preferences.save(next); options = next }
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Lyrics") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val align = if(PlayerSettings.lyricsCentered) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start
                    Text("Let the night unfold", Modifier.fillMaxWidth(), style = com.orismusic.client.ui.player.lyricReadingStyle(),
                        color = MaterialTheme.colorScheme.onSurface, textAlign = align)
                    Text("One song at a time", Modifier.fillMaxWidth(), style = com.orismusic.client.ui.player.lyricReadingStyle(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .25f), textAlign = align)
                }
            }
            Text("Lyrics preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup("Reading") {
                ChoiceRow("Text size", LyricSize.entries, PlayerSettings.lyricSize, { it.label }) { PlayerSettings.lyricSize = it; PlayerSettings.save() }
                ToggleRow("Centre lyrics", "", PlayerSettings.lyricsCentered) { PlayerSettings.lyricsCentered = it; PlayerSettings.save() }
                ToggleRow("Follow the song", "Scroll with the music.", PlayerSettings.lyricsAutoScroll) { PlayerSettings.lyricsAutoScroll = it; PlayerSettings.save() }
                ToggleRow("Tap a line to seek", "Synced lyrics only.", PlayerSettings.lyricsTapSeek) { PlayerSettings.lyricsTapSeek = it; PlayerSettings.save() }
            }
            SettingsFold("Sources and priority") {
                Text("Enabled sources are tried in order. Synced lyrics are preferred.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                options.ordered().forEachIndexed { index, source ->
                    Column {
                        Row(Modifier.fillMaxWidth().clickable { expanded = if(expanded == source.id) null else source.id }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index+1}", Modifier.width(26.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            Text(source.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = source.available && source.id !in options.disabled, enabled = source.available,
                                modifier = Modifier.semantics { contentDescription = "${source.name} enabled" },
                                onCheckedChange = { save(options.enable(source.id, it)) })
                        }
                        if(expanded == source.id) {
                            if(source.note.isNotBlank()) Text(source.note, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { uri.openUri(source.website) }) { Text("Website") }
                                Spacer(Modifier.weight(1f))
                                IconButton(enabled = index > 0, onClick = { save(options.move(source.id, -1)) }) { Icon(Icons.Default.ArrowUpward, "Move ${source.name} up") }
                                IconButton(enabled = index < options.order.lastIndex, onClick = { save(options.move(source.id, 1)) }) { Icon(Icons.Default.ArrowDownward, "Move ${source.name} down") }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))
                    }
                }
                TextButton(onClick = { save(restoreLyricsOptions(emptyList(), null)) }) { Text("Restore sources") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
