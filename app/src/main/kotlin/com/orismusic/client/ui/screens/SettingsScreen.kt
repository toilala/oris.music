package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.orismusic.client.settings.PlaybackPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context) { PlaybackPreferences(context) }
    var keepPlaying by remember { mutableStateOf(preferences.keepPlayingAfterTaskRemoval) }
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        }) },
    ) { padding ->
        Column(Modifier.padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Playback", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Keep playing after app is removed from recents", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (keepPlaying) "On: music can continue after you swipe the app away."
                        else "Off: swiping the app away stops playback and removes the player notification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(modifier = Modifier.semantics { contentDescription = "Keep playing after app is removed from recents" }, checked = keepPlaying, onCheckedChange = {
                    preferences.keepPlayingAfterTaskRemoval = it
                    keepPlaying = it
                })
            }
            HorizontalDivider()
            Text("Using Home or switching apps does not stop music. This setting only controls removal from recents.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
