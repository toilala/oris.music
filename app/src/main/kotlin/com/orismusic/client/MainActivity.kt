package com.orismusic.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.orismusic.client.ui.screens.SearchScreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.orismusic.client.ui.navigation.NavigationState
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.orismusic.client.library.currentSavedSong
import com.orismusic.client.ui.screens.applyTo
import com.orismusic.client.playback.OrisMediaService
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.ui.player.MiniPlayer
import com.orismusic.client.ui.player.NowPlayingScreen
import com.orismusic.client.ui.screens.HomeScreen
import com.orismusic.client.ui.screens.QueueScreen
import com.orismusic.client.ui.theme.OrisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private enum class MainTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Library("Library"),
    Queue("Queue"),
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var database: com.orismusic.client.di.MusicDatabase

    // Compose state so screens see the connection as soon as the service binds.
    private val connectionState = mutableStateOf<PlayerConnection?>(null)
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is OrisMediaService.MusicBinder) {
                try {
                    connectTo(service)
                    Timber.d("PlayerConnection created successfully")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to create PlayerConnection")
                    lifecycleScope.launch {
                        delay(500)
                        try {
                            connectTo(service)
                        } catch (e2: Exception) {
                            Timber.e(e2, "Failed to create PlayerConnection on retry")
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            connectionState.value?.dispose()
            connectionState.value = null
        }
    }

    private fun connectTo(binder: OrisMediaService.MusicBinder) {
        val previous = connectionState.value
        connectionState.value = PlayerConnection(this, binder, database, lifecycleScope).also { connection ->
            lifecycleScope.launch {
                connection.isPlayerInitialized.collect { /* player ready; playback starts from user taps */ }
            }
        }
        previous?.dispose()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OrisTheme {
                val connection = connectionState.value
                var navigation by rememberSaveable { mutableStateOf(NavigationState()) }
                var settingsOpen by rememberSaveable { mutableStateOf(false) }
                var detail by rememberSaveable { mutableStateOf<com.orismusic.client.ui.screens.DetailTarget?>(null) }
                var detailBack by rememberSaveable { mutableStateOf<com.orismusic.client.ui.screens.DetailTarget?>(null) }
                val tabState = rememberSaveableStateHolder()
                val selectedTab = navigation.currentTab
                val showPlayer = navigation.playerOpen
                val library = com.orismusic.client.library.LibraryStore.get(this@MainActivity)
                androidx.compose.runtime.LaunchedEffect(connection) {
                    com.orismusic.client.settings.PlaybackPreferences(this@MainActivity).applyTo(connection)
                }
                androidx.compose.runtime.LaunchedEffect(connection) {
                    connection?.currentMetadata?.collect {
                        connection.currentSavedSong()?.let { song -> library.recordPlay(song) }
                    }
                }
                BackHandler(enabled = navigation.canGoBack) {
                    navigation = navigation.back()
                }
                BackHandler(enabled = detail != null && !navigation.playerOpen && !settingsOpen) {
                    detail = detailBack
                    detailBack = null
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                Column {
                                    connection?.let {
                                        MiniPlayer(
                                            connection = it,
                                            onExpand = { navigation = navigation.openPlayer() },
                                        )
                                    }
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    ) {
                                    NavigationBar(
                                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                        tonalElevation = 0.dp,
                                    ) {
                                        MainTab.entries.forEach { tab ->
                                            NavigationBarItem(
                                                selected = selectedTab == tab.ordinal,
                                                onClick = { detail = null; detailBack = null; navigation = navigation.select(tab.ordinal) },
                                                icon = {
                                                    Icon(
                                                        imageVector = when (tab) {
                                                            MainTab.Home -> Icons.Filled.Home
                                                            MainTab.Search -> Icons.Filled.Search
                                                            MainTab.Library -> Icons.Filled.LibraryMusic
                                                            MainTab.Queue -> Icons.Filled.QueueMusic
                                                        },
                                                        contentDescription = tab.label,
                                                    )
                                                },
                                                label = { Text(tab.label) },
                                                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            )
                                        }
                                    }
                                    }
                                }
                            },
                        ) { innerPadding ->
                            val openDetail = detail
                            if (openDetail != null) {
                                com.orismusic.client.ui.screens.DetailScreen(
                                    target = openDetail,
                                    connection = connection,
                                    onBack = { detail = detailBack; detailBack = null },
                                    onOpen = { next -> detailBack = openDetail; detail = next },
                                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                                )
                            } else
                            androidx.compose.animation.AnimatedContent(
                                targetState = selectedTab,
                                transitionSpec = {
                                    (fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60)) +
                                        androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(260, delayMillis = 60), initialScale = 0.97f))
                                        .togetherWith(fadeOut(androidx.compose.animation.core.tween(120)))
                                },
                                label = "tabs",
                            ) { tabIndex ->
                            tabState.SaveableStateProvider(tabIndex) {
                            when (MainTab.entries[tabIndex]) {
                                MainTab.Home -> HomeScreen(
                                    onOpen = { detail = it; detailBack = null },
                                    onSettings = { settingsOpen = true },
                                    onSearch = { navigation = navigation.select(MainTab.Search.ordinal) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                    playerConnection = connection,
                                )
                                MainTab.Queue -> QueueScreen(
                                    onBack = { navigation = navigation.back() },
                                    onOpenPlayer = { navigation = navigation.openPlayer() },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                    connection = connection,
                                )
                                MainTab.Search -> SearchScreen(
                                    connection = connection,
                                    onOpen = { detail = it; detailBack = null },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                )
                                MainTab.Library -> com.orismusic.client.ui.screens.LibraryScreen(
                                    connection = connection,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding),
                                )
                            }
                            }
                            }
                        }

                        if (settingsOpen) {
                            com.orismusic.client.ui.screens.SettingsScreen(
                                onBack = { settingsOpen = false },
                                connection = connection,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        AnimatedVisibility(
                            visible = showPlayer && connection != null,
                            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.88f, stiffness = 300f), initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        ) {
                            connection?.let {
                                NowPlayingScreen(
                                    connection = it,
                                    onCollapse = { navigation = navigation.closePlayer() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startService(Intent(this, OrisMediaService::class.java))
        bindService(
            Intent(this, OrisMediaService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        isBound = true
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connectionState.value?.dispose()
        connectionState.value = null
    }

}

@Composable
private fun ComingSoonTab(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "$title arrives in a later stage",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
