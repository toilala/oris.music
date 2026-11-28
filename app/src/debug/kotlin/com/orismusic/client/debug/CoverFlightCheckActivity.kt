package com.orismusic.client.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.innertube.models.PlaylistItem
import com.orismusic.client.ui.components.LoadedCovers
import com.orismusic.client.ui.screens.CommunityPlaylistsSection
import com.orismusic.client.ui.screens.DetailContent
import com.orismusic.client.ui.screens.DetailPage
import com.orismusic.client.ui.screens.DetailState
import com.orismusic.client.ui.screens.DetailTarget
import com.orismusic.client.ui.theme.DetailStage
import com.orismusic.client.ui.theme.OrisTheme
import kotlinx.coroutines.delay
import java.io.File

/**
 * Debug-only fixture for the card-to-page cover flight. The Home row is the production
 * CommunityPlaylistsSection and the stage is the production DetailStage, so the card, the
 * shared-element wiring and the motion specs are the app's own. Only the data is local: covers
 * drawn into the cache, and a ready page with no songs. It is not the real MainActivity journey.
 */
class CoverFlightCheckActivity : ComponentActivity() {
    private fun cover(index: Int): String {
        val colors = intArrayOf(0xFFC0392B.toInt(), 0xFF2E86C1.toInt(), 0xFF28B463.toInt(), 0xFFD4AC0D.toInt())
        val file = File(cacheDir, "flightcover$index.png")
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(colors[index % colors.size])
        val line = Paint().apply { color = 0x99FFFFFF.toInt(); strokeWidth = 6f }
        for (step in 1 until 6) {
            canvas.drawLine(step * 100f, 0f, step * 100f, 600f, line)
            canvas.drawLine(0f, step * 100f, 600f, step * 100f, line)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "file://" + file.absolutePath
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val playlists = (0 until 4).map { i ->
            PlaylistItem("FX$i", "Fixture playlist $i", null, "10 songs", cover(i), null, null, null)
        }
        setContent {
            OrisTheme {
                var detail by rememberSaveable { mutableStateOf<DetailTarget?>(null) }
                BackHandler(enabled = detail != null) { detail = null }
                val loaded by produceState("") {
                    while (true) {
                        value = playlists.indices.filter { LoadedCovers.has(playlists[it].thumbnail) }.joinToString(",")
                        delay(200)
                    }
                }
                Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onSurface) {
                    DetailStage(
                        detail = detail,
                        detailPage = { d ->
                            val ready = DetailState.Ready(DetailContent(d.title, "Fixture", d.thumbnail, emptyList(), emptyList()))
                            DetailPage(d, ready, null, onBack = { detail = null }, onOpen = {}, onRetry = {},
                                modifier = Modifier.fillMaxSize().statusBarsPadding())
                        },
                    ) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(top = 28.dp)) {
                            Text("Cover flight fixture", modifier = Modifier.padding(horizontal = 20.dp))
                            Text("covers loaded [$loaded]", modifier = Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(20.dp))
                            CommunityPlaylistsSection(playlists, onOpen = { detail = it })
                        }
                    }
                }
            }
        }
    }
}
