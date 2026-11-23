package com.orismusic.client.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.orismusic.client.settings.AppIcons
import com.orismusic.client.settings.PlayButtonStyle
import com.orismusic.client.ui.player.OrbitPlayButton
import com.orismusic.client.ui.theme.OrisTheme

/** Real production controls and icon resources, no mocked player UI. */
class ControlStylesCheckActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContent {
            OrisTheme {
                Surface(color = androidx.compose.ui.graphics.Color(0xFF101010), contentColor = MaterialTheme.colorScheme.onSurface) {
                    var playing by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Player styles", style = MaterialTheme.typography.titleLarge)
                        PlayButtonStyle.entries.forEach { style ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OrbitPlayButton(playing, { playing = !playing }, icon = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = style.label, style = style)
                                Text(style.label, Modifier.padding(start = 18.dp))
                            }
                        }
                        Text("New app icons", style = MaterialTheme.typography.titleLarge)
                        Row {
                            AppIcons.variants.filter { it.id in listOf("wobble", "sprout", "ribbon") }.forEach { variant ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(painterResource(variant.preview), variant.label, Modifier.size(88.dp))
                                    Text(variant.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
