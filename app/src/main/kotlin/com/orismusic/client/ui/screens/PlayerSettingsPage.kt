package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.LyricSize
import com.orismusic.client.settings.PlayKey
import com.orismusic.client.settings.PlaySize
import com.orismusic.client.settings.PlayerSettings
import com.orismusic.client.settings.SeekStyle
import com.orismusic.client.settings.SkipIcons

/** Everything about the full-screen player's look and feel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Player") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlayerPreview()
            ChoiceRow("Tapping a song", com.orismusic.client.settings.TapOpen.entries, PlayerSettings.tapOpen, { it.label }) { PlayerSettings.tapOpen = it; PlayerSettings.save() }
            ChoiceRow("Play key style", PlayKey.entries, PlayerSettings.keys, { it.label }) { PlayerSettings.keys = it; PlayerSettings.save() }
            ChoiceRow("Key size", PlaySize.entries, PlayerSettings.size, { it.label }) { PlayerSettings.size = it; PlayerSettings.save() }
            ChoiceRow("Previous and next icons", SkipIcons.entries, PlayerSettings.icons, { it.label }) { PlayerSettings.icons = it; PlayerSettings.save() }
            ChoiceRow("Seek bar", SeekStyle.entries, PlayerSettings.seek, { it.label }) { PlayerSettings.seek = it; PlayerSettings.save() }
            ToggleRow("Time left", "Show the time left instead of the full length on the right.", PlayerSettings.remaining) {
                PlayerSettings.remaining = it; PlayerSettings.save()
            }
            ChoiceRow("Lyrics size", LyricSize.entries, PlayerSettings.lyricSize, { it.label }) { PlayerSettings.lyricSize = it; PlayerSettings.save() }
            ToggleRow("Centre lyrics", "Centre the lines instead of aligning them left.", PlayerSettings.lyricsCentered) {
                PlayerSettings.lyricsCentered = it; PlayerSettings.save()
            }
            ToggleRow("Blur far lines", "Lines away from the current one fade into a soft blur.", PlayerSettings.lyricsBlur) {
                PlayerSettings.lyricsBlur = it; PlayerSettings.save()
            }
            ToggleRow("Play key morph", "The key turns into a rounded square while music plays.", PlayerSettings.morph) {
                PlayerSettings.morph = it; PlayerSettings.save()
            }
            ToggleRow("Title motion", "The title slides when the song changes.", PlayerSettings.titleMotion) {
                PlayerSettings.titleMotion = it; PlayerSettings.save()
            }
            ToggleRow("Vibration", "A short tick on the keys and like button.", PlayerSettings.haptics) {
                PlayerSettings.haptics = it; PlayerSettings.save()
            }
            ToggleRow("Up next in the bottom bar", "Show the next song's name, or just 'Swipe up for more'.", PlayerSettings.upNext) {
                PlayerSettings.upNext = it; PlayerSettings.save()
            }
            TextButton(onClick = { PlayerSettings.reset() }) { Text("Reset player") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A live sample of the player controls. Tap the keys to try them; every option below changes it at once. */
@Composable
private fun PlayerPreview() {
    var playing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    var pos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.38f) }
    val accent = androidx.compose.ui.graphics.Color(0xFFE8B7AE)
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = androidx.compose.ui.graphics.Color(0xFF1B1716),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.orismusic.client.ui.player.NowPlayingTitle("Last Christmas", "Wham!")
            com.orismusic.client.ui.player.SeekPreview(pos, accent) { pos = it }
            com.orismusic.client.ui.player.NowPlayingTransport(
                isPlaying = playing, canPrevious = true, canNext = true, accent = accent,
                onPrevious = { pos = 0f }, onToggle = { playing = !playing }, onNext = { pos = 0f },
            )
        }
    }
}
