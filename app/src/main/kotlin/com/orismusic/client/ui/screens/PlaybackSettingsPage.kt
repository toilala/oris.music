package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.orismusic.client.settings.PlaybackPreferences
import com.orismusic.client.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** How playback behaves: background play, silence, Bluetooth, speed and sleep. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsPage(onBack: () -> Unit, connection: com.orismusic.client.playback.PlayerConnection?) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val preferences = remember(context) { PlaybackPreferences(context) }
    var keepPlaying by remember { mutableStateOf(preferences.keepPlayingAfterTaskRemoval) }
    var skipSilence by remember { mutableStateOf(preferences.skipSilence) }
    var playbackNotification by remember { mutableStateOf(preferences.playbackNotification) }
    var stopOnlyWhenPaused by remember { mutableStateOf(preferences.stopOnlyWhenPaused) }
    var persistentQueue by remember { mutableStateOf(preferences.persistentQueue) }
    var speed by remember { mutableStateOf(preferences.playbackSpeed) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Playback") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ToggleRow(
                "Keep playing after app is removed from recents",
                if (keepPlaying) "On: music can continue after you swipe the app away."
                else "Off: swiping the app away stops playback and removes the player notification.",
                keepPlaying,
            ) {
                preferences.keepPlayingAfterTaskRemoval = it
                keepPlaying = it
            }
            ToggleRow(
                "Swipe away stops only when paused",
                if (stopOnlyWhenPaused) "On: swiping the app away stops music only if it is already paused."
                else "Off: swiping the app away always stops playback.",
                stopOnlyWhenPaused,
            ) {
                preferences.stopOnlyWhenPaused = it
                stopOnlyWhenPaused = it
            }
            run {
                val powerManager = remember(context) { context.getSystemService(android.os.PowerManager::class.java) }
                val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                androidx.compose.foundation.layout.Column(
                    Modifier.clickable(
                        indication = null,
                        interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    ) { openBackgroundBatterySettings(context) },
                ) {
                    Text("Unrestricted battery use", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (batteryExempt) "On: Android will not limit the app in the background."
                        else "Off: strict phones (vivo, Oppo, Xiaomi) can stop music in the background. Tap to allow unrestricted background use.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ToggleRow(
                "Playback notification",
                if (playbackNotification) "On: the player controls show in your notifications."
                else "Off: minimized, tucked at the bottom of the shade. Android still needs it while music plays. Applies on the next play or skip.",
                playbackNotification,
            ) {
                preferences.playbackNotification = it
                playbackNotification = it
            }
            ToggleRow(
                "Remember the queue",
                if (persistentQueue) "On: reopening the app brings back your last queue, paused where you left it."
                else "Off: the app starts empty every time.",
                persistentQueue,
            ) {
                preferences.persistentQueue = it
                persistentQueue = it
            }
            ToggleRow("Skip silence", "Jumps over quiet gaps in songs and between tracks.", skipSilence) {
                preferences.skipSilence = it
                skipSilence = it
                preferences.applyTo(connection)
            }
            val btScope = rememberCoroutineScope()
            val resumeBt by remember(context) {
                context.dataStore.data.map { it[com.orismusic.client.constants.ResumeOnBluetoothConnectKey] ?: false }
            }.collectAsState(initial = false)
            ToggleRow("Resume on Bluetooth", "Start playing again when a Bluetooth speaker or headphones connect.", resumeBt) { v ->
                btScope.launch { context.dataStore.edit { it[com.orismusic.client.constants.ResumeOnBluetoothConnectKey] = v } }
            }
            ToggleRow(
                "Sleep timer",
                if (com.orismusic.client.settings.AppearanceSettings.sleepTimerButton) "On: the player shows a sleep timer button."
                else "Off: no sleep timer button on the player.",
                com.orismusic.client.settings.AppearanceSettings.sleepTimerButton,
            ) {
                com.orismusic.client.settings.AppearanceSettings.sleepTimerButton = it
                com.orismusic.client.settings.AppearanceSettings.save()
                if (!it) com.orismusic.client.playback.SleepTimer.cancel()
            }
            Column {
                Text("Playback speed", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Best-effort path to this phone's background/battery allowlist: vivo/iQOO autostart manager
 *  first on those devices, then the standard battery-optimization exemption, then the list page. */
private fun openBackgroundBatterySettings(context: android.content.Context) {
    val pkg = context.packageName
    val maker = android.os.Build.MANUFACTURER.lowercase()
    val candidates = mutableListOf<android.content.Intent>()
    if (maker.contains("vivo") || maker.contains("iqoo")) {
        candidates += android.content.Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        )
    }
    candidates += android.content.Intent(
        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        android.net.Uri.parse("package:" + pkg),
    )
    candidates += android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    for (intent in candidates) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {
        }
    }
}
