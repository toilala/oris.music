package com.orismusic.client.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.*
import com.orismusic.client.ui.player.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("Player") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayerLookPreview()
            Text("Preview · tap play or try the seek bar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SettingsGroup("Look") {
                ChoiceRow("Artwork", ArtworkStyle.entries, PlayerSettings.artworkStyle, { it.label }) { PlayerSettings.artworkStyle = it; PlayerSettings.save() }
                ChoiceRow("Background", PlayerBackgroundStyle.entries, PlayerSettings.backgroundStyle, { it.label }) { PlayerSettings.backgroundStyle = it; PlayerSettings.save() }
                ChoiceRow("Play button", PlayButtonStyle.entries, PlayerSettings.playButton, { it.label }) { PlayerSettings.playButton = it; PlayerSettings.save() }
                ChoiceRow("Seek bar", PlayerSeekBar.entries, PlayerSettings.seekBar, { it.label }) { PlayerSettings.seekBar = it; PlayerSettings.save() }
            }
            SettingsFold("Gestures and playback display") {
                ChoiceRow("Artwork swipe sensitivity", SwipeSensitivity.entries, PlayerSettings.swipeSensitivity, { it.label }) { PlayerSettings.swipeSensitivity = it; PlayerSettings.save() }
                ChoiceRow("Tapping a song", TapOpen.entries, PlayerSettings.tapOpen, { it.label }) { PlayerSettings.tapOpen = it; PlayerSettings.save() }
                ToggleRow("Time left", "Show remaining time.", PlayerSettings.remaining) { PlayerSettings.remaining = it; PlayerSettings.save() }
                ToggleRow("Vibration", "A short tick on controls.", PlayerSettings.haptics) { PlayerSettings.haptics = it; PlayerSettings.save() }
            }
            TextButton(onClick = { PlayerSettings.reset() }) { Text("Reset player") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
internal fun PlayerLookPreview() {
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(.38f) }
    val gradient = when(PlayerSettings.backgroundStyle) {
        PlayerBackgroundStyle.Gradient -> listOf(Color(0xFF4C3835), Color(0xFF20272F), Color(0xFF151515))
        PlayerBackgroundStyle.Blur -> listOf(Color(0xFF635B4B), Color(0xFF3B4549), Color(0xFF161E22))
        PlayerBackgroundStyle.Solid -> listOf(Color.Black, Color.Black)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(gradient)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StyledArtwork(null, "Artwork preview", Modifier.size(142.dp), sample = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your next favourite", style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text("Artwork preview", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .55f))
            }
            OrbitPlayButton(playing, { playing = !playing }, icon = if(playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Preview play", style = PlayerSettings.playButton)
        }
        if (PlayerSettings.seekBar == PlayerSeekBar.Wave) SeekPreview(progress, MaterialTheme.colorScheme.primary) { progress = it }
        else Slider(progress, { progress = it }, modifier = Modifier.height(32.dp))
    }
}
