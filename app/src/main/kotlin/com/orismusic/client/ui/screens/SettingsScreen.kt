package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

/**
 * Settings home: one card per folder. Detail lives inside the folders so this page
 * stays a short, scannable list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    connection: PlayerConnection? = null,
) {
    var openPage by remember { mutableStateOf(SettingsFolder.None) }
    when (openPage) {
        SettingsFolder.Appearance -> { AppearanceSettingsPage(onBack = { openPage = SettingsFolder.None }); return }
        SettingsFolder.Navigation -> { NavigationSettings(onBack = { openPage = SettingsFolder.None }); return }
        SettingsFolder.Player -> { PlayerSettingsPage(onBack = { openPage = SettingsFolder.None }); return }
        SettingsFolder.Home -> { HomeSettingsPage(onBack = { openPage = SettingsFolder.None }); return }
        SettingsFolder.Playback -> { PlaybackSettingsPage(onBack = { openPage = SettingsFolder.None }, connection = connection); return }
        SettingsFolder.Lyrics -> { LyricsProviderSettings(onBack = { openPage = SettingsFolder.None }); return }
        SettingsFolder.None -> Unit
    }
    BackHandler(onBack = onBack)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = {
            TextButton(onClick = onBack) { Text("Back") }
        }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SettingsFolder.entries.filter { it != SettingsFolder.None }.forEach { folder ->
                SettingsLink(folder.title, folder.detail) { openPage = folder }
            }
            Spacer(Modifier.height(4.dp))
            val context = LocalContext.current
            SettingsFold("Storage and history") {
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
            var imagesCleared by remember { mutableStateOf(false) }
            OutlinedButton(onClick = {
                val loader = coil3.SingletonImageLoader.get(context)
                loader.memoryCache?.clear(); loader.diskCache?.clear(); imagesCleared = true
            }) { Text("Clear image cache") }
            Text(if (imagesCleared) "Image cache cleared." else "Covers are cached so they load faster. Clearing is safe.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            }
            SettingsFold("About Oris") {
            val versionName = remember(context) {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "dev"
            }
            Text("Oris Music $versionName", style = MaterialTheme.typography.bodyMedium)
            Text("Made by ${com.orismusic.client.settings.Support.AUTHOR}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Free software under GPL-3.0. No ads, no tracking, no account.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            var showCredits by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/toilala/oris.music"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Source code") }
                OutlinedButton(onClick = { showCredits = !showCredits }) { Text(if (showCredits) "Hide credits" else "Credits") }
            }
            if (showCredits) {
                Text(
                    "Oris builds on open source work: the innertube client and parts of the queue and theme code come from Mochy Music, " +
                        "stream extraction from Metrolist / InnerTubeX. Lyrics come from LRCLIB, Better Lyrics, Unison, YouLyPlus, PaxSenix " +
                        "and YouTube where available; lyrics belong to their owners. Full notices are in the repository (LICENSE and README).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (com.orismusic.client.settings.Support.TIP_URL.isNotBlank()) {
                FilledTonalButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.orismusic.client.settings.Support.TIP_URL))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text("Buy me a momo") }
            }
            }
        }
    }
}

private enum class SettingsFolder(val title: String, val detail: String) {
    None("", ""),
    Appearance("Appearance", "Colours, backgrounds and app icon"),
    Navigation("Navigation", "Bottom bar: effect, style, accent, size, hiding"),
    Player("Player", "Artwork, controls and gestures"),
    Home("Home", "A quieter feed, your way"),
    Playback("Playback", "Background play, silence, Bluetooth, speed, sleep timer"),
    Lyrics("Lyrics", "Look, reading and sources"),
}

@Composable
private fun SettingsLink(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}
