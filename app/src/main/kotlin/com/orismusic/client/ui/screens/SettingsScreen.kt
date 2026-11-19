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
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.orismusic.client.R
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.settings.PlaybackPreferences
import androidx.datastore.preferences.core.edit
import com.orismusic.client.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    var navigationPage by remember { mutableStateOf(false) }
    if (navigationPage) {
        NavigationSettings(onBack = { navigationPage = false })
        return
    }
    var playerPage by remember { mutableStateOf(false) }
    if (playerPage) {
        PlayerSettingsPage(onBack = { playerPage = false })
        return
    }
    var homePage by remember { mutableStateOf(false) }
    if (homePage) {
        HomeSettingsPage(onBack = { homePage = false })
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
            SettingsLink("Navigation", "Bottom bar: names, effect, style, size, hiding and more") { navigationPage = true }
            SettingsLink("Home", "Sections, order, hero, shelf look and density") { homePage = true }
            SettingsLink("Player", "Play key style, size, motion, vibration") { playerPage = true }
            SectionTitle("Playback")
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
            val btScope = rememberCoroutineScope()
            val resumeBt by remember(context) {
                context.dataStore.data.map { it[com.orismusic.client.constants.ResumeOnBluetoothConnectKey] ?: false }
            }.collectAsState(initial = false)
            SettingSwitch(
                title = "Resume on Bluetooth",
                detail = "Start playing again when a Bluetooth speaker or headphones connect.",
                checked = resumeBt,
            ) { v -> btScope.launch { context.dataStore.edit { it[com.orismusic.client.constants.ResumeOnBluetoothConnectKey] = v } } }
            SettingSwitch(
                title = "Sleep timer",
                detail = if (com.orismusic.client.settings.AppearanceSettings.sleepTimerButton) "On: the player shows a sleep timer button."
                else "Off: no sleep timer button on the player.",
                checked = com.orismusic.client.settings.AppearanceSettings.sleepTimerButton,
            ) {
                com.orismusic.client.settings.AppearanceSettings.sleepTimerButton = it
                com.orismusic.client.settings.AppearanceSettings.save()
                if (!it) com.orismusic.client.playback.SleepTimer.cancel()
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
            val look = com.orismusic.client.settings.AppearanceSettings
            Spacer(Modifier.height(4.dp))
            SectionTitle("Appearance")
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                SettingSwitch(
                    title = "Wallpaper colours",
                    detail = if (look.dynamicColor) "On: colours follow your Android wallpaper (Material You)." else "Off: the Oris warm neutral palette.",
                    checked = look.dynamicColor,
                ) { look.dynamicColor = it; look.save() }
            }
            SettingSwitch(
                title = "Hide status bar",
                detail = "Full screen. Swipe down from the top edge to peek at the clock and notifications.",
                checked = look.hideStatusBar,
            ) { look.hideStatusBar = it; look.save() }
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
            var chosenIcon by remember { mutableStateOf(com.orismusic.client.settings.AppIcons.current(context)) }
            Column {
                Text("App icon", style = MaterialTheme.typography.titleMedium)
                Text("Pick how Oris looks on your home screen. Your launcher may take a few seconds to update, and the app can close once while it switches.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    com.orismusic.client.settings.AppIcons.variants.forEach { v ->
                        val selected = v == chosenIcon
                        Column(
                            Modifier.width(76.dp).clip(MaterialTheme.shapes.medium)
                                .clickable { chosenIcon = v; com.orismusic.client.settings.AppIcons.select(context, v) }
                                .semantics { contentDescription = "App icon " + v.label },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(64.dp).clip(CircleShape)
                                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(painterResource(v.preview), null, Modifier.requiredSize(104.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(v.label, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            SectionTitle("Lyrics")
            Surface(
                onClick = { lyricsSettings = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Lyrics providers", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Choose sources and their priority order.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            SectionTitle("Your data")
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
            SectionTitle("About")
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

@Composable
private fun SettingsLink(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
