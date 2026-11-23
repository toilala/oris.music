package com.orismusic.client.debug
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orismusic.client.ui.theme.OrisTheme
import com.orismusic.client.ui.screens.*
import com.orismusic.client.ui.player.StyledArtwork
import com.orismusic.client.settings.*
class SettingsCheckActivity: ComponentActivity() {
 override fun onCreate(state: Bundle?) { super.onCreate(state)
  val page=intent.getStringExtra("page") ?: "settings"
  setContent { OrisTheme { Surface(color=MaterialTheme.colorScheme.background, contentColor=MaterialTheme.colorScheme.onSurface) {
   when(page) {
    "player" -> PlayerSettingsPage({ finish() })
    "lyrics" -> LyricsProviderSettings({ finish() })
    "home" -> HomeSettingsPage({ finish() })
    "appearance" -> AppearanceSettingsPage({ finish() })
    "artwork" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Text("Artwork styles", style=MaterialTheme.typography.titleLarge)
     ArtworkStyle.entries.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth()) {
      row.forEach { style -> Column(Modifier.weight(1f)) { StyledArtwork(null, style.label, Modifier.size(145.dp), style, sample=true); Text(style.label) } }
     } }
    }
    else -> SettingsScreen({ finish() })
   }
  } } }
 }
}
