package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.LyricSize
import com.orismusic.client.settings.PlayButtonStyle
import com.orismusic.client.settings.PlayerBackgroundStyle
import com.orismusic.client.settings.PlayerSeekBar
import com.orismusic.client.settings.PlayerSettings
import com.orismusic.client.settings.SwipeSensitivity
import com.orismusic.client.settings.TapOpen

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
            ChoiceRow("Background", PlayerBackgroundStyle.entries, PlayerSettings.backgroundStyle, { it.label }) { PlayerSettings.backgroundStyle = it; PlayerSettings.save() }
            ChoiceRow("Play button", PlayButtonStyle.entries, PlayerSettings.playButton, { it.label }) { PlayerSettings.playButton = it; PlayerSettings.save() }
            ChoiceRow("Seek bar", PlayerSeekBar.entries, PlayerSettings.seekBar, { it.label }) { PlayerSettings.seekBar = it; PlayerSettings.save() }
            ChoiceRow("Artwork swipe sensitivity", SwipeSensitivity.entries, PlayerSettings.swipeSensitivity, { it.label }) { PlayerSettings.swipeSensitivity = it; PlayerSettings.save() }
            ChoiceRow("Tapping a song", TapOpen.entries, PlayerSettings.tapOpen, { it.label }) { PlayerSettings.tapOpen = it; PlayerSettings.save() }
            ToggleRow("Time left", "Show the time left instead of the full length on the right.", PlayerSettings.remaining) {
                PlayerSettings.remaining = it; PlayerSettings.save()
            }
            ChoiceRow("Lyrics size", LyricSize.entries, PlayerSettings.lyricSize, { it.label }) { PlayerSettings.lyricSize = it; PlayerSettings.save() }
            ToggleRow("Centre lyrics", "Centre the lines instead of aligning them left.", PlayerSettings.lyricsCentered) {
                PlayerSettings.lyricsCentered = it; PlayerSettings.save()
            }
            ToggleRow("Tap a line to seek", "Tapping a synced line jumps the song to it.", PlayerSettings.lyricsTapSeek) {
                PlayerSettings.lyricsTapSeek = it; PlayerSettings.save()
            }
            ToggleRow("Follow the song", "The lyrics list scrolls with the music.", PlayerSettings.lyricsAutoScroll) {
                PlayerSettings.lyricsAutoScroll = it; PlayerSettings.save()
            }
            ToggleRow("Vibration", "A short tick on the keys, the seek bar and the like button.", PlayerSettings.haptics) {
                PlayerSettings.haptics = it; PlayerSettings.save()
            }
            TextButton(onClick = { PlayerSettings.reset() }) { Text("Reset player") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
