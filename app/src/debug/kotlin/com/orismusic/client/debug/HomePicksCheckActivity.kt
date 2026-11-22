package com.orismusic.client.debug

import android.content.*
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.orismusic.client.di.MusicDatabase
import com.orismusic.client.playback.OrisMediaService
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.screens.QuickPicksSection
import com.orismusic.client.ui.screens.CommunityPlaylistsSection
import com.orismusic.client.ui.screens.loadCommunityPlaylists
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.orismusic.client.ui.theme.OrisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Debug-only rendering of the production Home sections using real catalog results. */
@AndroidEntryPoint
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class HomePicksCheckActivity : ComponentActivity() {
    @Inject lateinit var database: MusicDatabase
    private var playerConnection: PlayerConnection? = null
    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as OrisMediaService.MusicBinder
            val connection = PlayerConnection(this@HomePicksCheckActivity, local, database, lifecycleScope)
            playerConnection = connection
            setContent {
                OrisTheme {
                    val songs by produceState<List<SongItem>>(emptyList()) {
                        value = YouTube.search("The Beatles", YouTube.SearchFilter.FILTER_SONG).getOrNull()
                            ?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }?.take(16).orEmpty()
                    }
                    val playlists by produceState(emptyList<com.music.innertube.models.PlaylistItem>()) {
                        value = loadCommunityPlaylists(listOf("The Beatles"))
                    }
                    val scope = rememberCoroutineScope()
                    Column(Modifier.fillMaxSize().background(Color(0xFF0B0B0B))
                        .verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp)) {
                        if (songs.isEmpty()) Text("Loading picks")
                        else QuickPicksSection(songs, connection, scope)
                        Spacer(Modifier.height(20.dp))
                        if (playlists.isNotEmpty()) CommunityPlaylistsSection(playlists, onOpen = {})
                    }
                }
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {}
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        startService(Intent(this, OrisMediaService::class.java))
        bound = bindService(Intent(this, OrisMediaService::class.java), connection, Context.BIND_AUTO_CREATE)
    }
    override fun onDestroy() {
        playerConnection?.dispose()
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}
