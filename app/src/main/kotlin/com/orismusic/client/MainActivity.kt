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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Home
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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
                        var navCollapsed by remember { mutableStateOf(false) }
                        val navScroll = remember {
                            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                                override fun onPreScroll(
                                    available: androidx.compose.ui.geometry.Offset,
                                    source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
                                ): androidx.compose.ui.geometry.Offset {
                                    if (available.y < -3f) navCollapsed = true
                                    else if (available.y > 3f) navCollapsed = false
                                    return androidx.compose.ui.geometry.Offset.Zero
                                }
                            }
                        }
                        Scaffold(
                            modifier = Modifier.fillMaxSize().nestedScroll(navScroll),
                            bottomBar = {
                                Column {
                                    connection?.let {
                                        MiniPlayer(
                                            connection = it,
                                            onExpand = { navigation = navigation.openPlayer() },
                                        )
                                    }
                                    FloatingNav(
                                        selected = selectedTab,
                                        collapsed = navCollapsed,
                                        onSelect = { tab ->
                                            detail = null; detailBack = null; navCollapsed = false
                                            navigation = navigation.select(tab.ordinal)
                                        },
                                    )
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
                                    onQueue = { navigation = navigation.select(MainTab.Queue.ordinal) },
                                    onSettings = { settingsOpen = true },
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

                        val playerDrag = remember { androidx.compose.animation.core.Animatable(0f) }
                        val dragScope = androidx.compose.runtime.rememberCoroutineScope()
                        androidx.compose.runtime.LaunchedEffect(showPlayer) { if (showPlayer) playerDrag.snapTo(0f) }
                        AnimatedVisibility(
                            visible = showPlayer && connection != null,
                            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.9f, stiffness = 320f), initialOffsetY = { it * 2 / 3 }) +
                                fadeIn(androidx.compose.animation.core.tween(220)) +
                                androidx.compose.animation.scaleIn(spring(0.9f, 320f), initialScale = 0.9f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                            exit = slideOutVertically(androidx.compose.animation.core.tween(260), targetOffsetY = { it * 2 / 3 }) +
                                fadeOut(androidx.compose.animation.core.tween(220)) +
                                androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(260), targetScale = 0.9f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)),
                        ) {
                            connection?.let {
                                androidx.compose.foundation.layout.Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationY = playerDrag.value
                                            val f = (playerDrag.value / size.height).coerceIn(0f, 1f)
                                            scaleX = 1f - 0.08f * f; scaleY = 1f - 0.08f * f
                                            alpha = 1f - 0.5f * f
                                        }
                                        .pointerInput(Unit) {
                                            detectVerticalDragGestures(
                                                onDragEnd = {
                                                    dragScope.launch {
                                                        if (playerDrag.value > 220f) navigation = navigation.closePlayer()
                                                        else playerDrag.animateTo(0f, spring(0.7f, 400f))
                                                    }
                                                },
                                                onDragCancel = { dragScope.launch { playerDrag.animateTo(0f) } },
                                                onVerticalDrag = { change, amount ->
                                                    change.consume()
                                                    dragScope.launch { playerDrag.snapTo((playerDrag.value + amount).coerceAtLeast(0f)) }
                                                },
                                            )
                                        },
                                ) {
                                    NowPlayingScreen(
                                        connection = it,
                                        onCollapse = { navigation = navigation.closePlayer() },
                                        onQueue = { navigation = navigation.select(MainTab.Queue.ordinal).closePlayer() },
                                    )
                                }
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


/**
 * The floating bar. At rest it is one pill with labels. When the page scrolls
 * down it splits: the current tab pops out as a circle and the other tabs
 * gather in a small pill beside it.
 */
@androidx.compose.runtime.Composable
private fun FloatingNav(selected: Int, collapsed: Boolean, onSelect: (MainTab) -> Unit) {
    val tabs = MainTab.entries.filter { it != MainTab.Queue }
    fun icon(tab: MainTab) = when (tab) {
        MainTab.Home -> Icons.Rounded.Home
        MainTab.Search -> Icons.Rounded.Search
        MainTab.Library -> Icons.Rounded.LibraryMusic
        MainTab.Queue -> Icons.Rounded.QueueMusic
    }
    androidx.compose.animation.AnimatedContent(
        targetState = collapsed,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
        transitionSpec = {
            (fadeIn(androidx.compose.animation.core.tween(220, delayMillis = 60)) +
                androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(0.7f, 380f), initialScale = 0.8f)) togetherWith
                (fadeOut(androidx.compose.animation.core.tween(120)) +
                    androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(160), targetScale = 0.85f))
        },
        label = "nav shape",
    ) { isCollapsed ->
        if (!isCollapsed) {
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                ) {
                    tabs.forEach { tab ->
                        NavItem(tab, icon(tab), selected == tab.ordinal, showLabel = true, onSelect, Modifier.weight(1f))
                    }
                }
            }
        } else {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                val current = tabs.firstOrNull { it.ordinal == selected } ?: tabs.first()
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(56.dp).clickable { onSelect(current) },
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Icon(icon(current), contentDescription = current.label, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    ) {
                        tabs.filter { it.ordinal != selected }.forEach { tab ->
                            NavItem(tab, icon(tab), false, showLabel = false, onSelect, Modifier)
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun NavItem(
    tab: MainTab, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean,
    showLabel: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onSelect(tab) }
            .padding(horizontal = if (showLabel) 12.dp else 16.dp, vertical = if (showLabel) 8.dp else 12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        val tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = tab.label, tint = tint)
        if (showLabel) Text(tab.label, style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
