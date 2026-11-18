package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.horizontalScroll
import com.orismusic.client.playback.EqualizerController
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.settings.PlaybackPreferences

/** Applies saved playback settings to the live player. Safe to call any time. */
fun PlaybackPreferences.applyTo(connection: PlayerConnection?) {
    val c = connection ?: return
    runCatching {
        c.player.skipSilenceEnabled = skipSilence
        c.player.setPlaybackSpeed(playbackSpeed)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connection: PlayerConnection? = null,
) {
    var lyricsSettings by remember { mutableStateOf(false) }
    if (lyricsSettings) {
        LyricsProviderSettings(onBack = { lyricsSettings = false })
        return
    }
    val context = LocalContext.current
    val preferences = remember(context) { PlaybackPreferences(context) }
    var keepPlaying by remember { mutableStateOf(preferences.keepPlayingAfterTaskRemoval) }
    var skipSilence by remember { mutableStateOf(preferences.skipSilence) }
    var speed by remember { mutableStateOf(preferences.playbackSpeed) }
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Playback", style = MaterialTheme.typography.titleMedium)
            SettingSwitch(
                title = "Keep playing after app is removed from recents",
                detail = if (keepPlaying) "On: music can continue after you swipe the app away."
                else "Off: swiping the app away stops playback and removes the player notification.",
                checked = keepPlaying,
            ) {
                preferences.keepPlayingAfterTaskRemoval = it
                keepPlaying = it
            }
            SettingSwitch(
                title = "Skip silence",
                detail = "Jumps over quiet gaps in songs and between tracks.",
                checked = skipSilence,
            ) {
                preferences.skipSilence = it
                skipSilence = it
                preferences.applyTo(connection)
            }
            Column {
                Text("Playback speed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaybackPreferences.SPEEDS.forEach { option ->
                        FilterChip(
                            selected = speed == option,
                            onClick = {
                                preferences.playbackSpeed = option
                                speed = option
                                preferences.applyTo(connection)
                            },
                            label = { Text(if (option == 1f) "1x" else "${option}x") },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Look", style = MaterialTheme.typography.titleMedium)
            val look = com.orismusic.client.settings.AppearanceSettings
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                SettingSwitch(
                    title = "Wallpaper colours",
                    detail = if (look.dynamicColor) "On: colours follow your Android wallpaper (Material You)." else "Off: the Oris plum and lime palette.",
                    checked = look.dynamicColor,
                ) { look.dynamicColor = it; look.save() }
            }
            SettingSwitch(
                title = "Pure black",
                detail = "Black backgrounds for OLED screens.",
                checked = look.amoled,
            ) { look.amoled = it; look.save() }
            SettingSwitch(
                title = "Adaptive player background",
                detail = "The player takes its colours from the song artwork.",
                checked = look.adaptiveBackground,
            ) { look.adaptiveBackground = it; look.save() }
            if (look.adaptiveBackground) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.orismusic.client.settings.BackgroundMode.entries.forEach { mode ->
                        FilterChip(
                            selected = look.backgroundMode == mode,
                            onClick = { look.backgroundMode = mode; look.save() },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Text("Intensity", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = look.intensity,
                    onValueChange = { look.intensity = it },
                    onValueChangeFinished = { look.save() },
                    valueRange = 0.2f..1f,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("Equalizer", style = MaterialTheme.typography.titleMedium)
            val eq by EqualizerController.state.collectAsState()
            if (!eq.available) {
                Text("The equalizer shows up once a song has played.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                SettingSwitch(title = "Equalizer", detail = if (eq.enabled) "On" else "Off",
                    checked = eq.enabled) { EqualizerController.setEnabled(it) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqualizerController.presets.forEach { (name, shape) ->
                        AssistChip(onClick = { EqualizerController.applyPreset(shape) }, label = { Text(name) })
                    }
                }
                eq.levels.forEachIndexed { i, level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val hz = eq.centerHz.getOrNull(i) ?: 0
                        Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", modifier = Modifier.width(64.dp),
                            style = MaterialTheme.typography.bodySmall)
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { EqualizerController.setLevel(i, it.toInt()) },
                            valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                            enabled = eq.enabled,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Lyrics", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { lyricsSettings = true }) { Text("Lyrics providers") }
            Text("Choose sources and their priority order.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("Your data", style = MaterialTheme.typography.titleMedium)
            val libraryStore = remember(context) { com.orismusic.client.library.LibraryStore.get(context) }
            val historyCount by libraryStore.history.collectAsState()
            var cleared by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { libraryStore.clearHistory(); cleared = true },
                enabled = historyCount.isNotEmpty(),
            ) { Text("Clear play history") }
            Text(
                if (cleared && historyCount.isEmpty()) "Play history cleared. Liked songs and playlists are untouched."
                else "${historyCount.size} recently played songs are kept on this phone only.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text("About", style = MaterialTheme.typography.titleMedium)
            val versionName = remember(context) {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
            }
            Text("Oris Music $versionName", style = MaterialTheme.typography.bodyMedium)
            Text("Free software under GPL-3.0. Built on Mochy Music and InnerTubeX; credits are in the README.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text("Using Home or switching apps does not stop music. The recents setting only controls removal from recents.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            modifier = Modifier.semantics { contentDescription = title },
            checked = checked,
            onCheckedChange = onChange,
        )
    }
    }
}
