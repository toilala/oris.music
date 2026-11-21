package com.orismusic.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
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
import androidx.compose.foundation.layout.statusBarsPadding
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

    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private fun runningOnEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
            Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu") || Build.MODEL.contains("sdk_gphone")

    private fun shouldWelcome(): Boolean {
        val prefs = getSharedPreferences("oris_first_run", MODE_PRIVATE)
        if (prefs.getBoolean("welcomed", false)) return false
        // Automated emulator checks would be blocked by the dialog; a debug extra can still show it.
        return intent?.getBooleanExtra("oris_show_welcome", false) == true || !runningOnEmulator()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.orismusic.client.ui.navigation.PlayerLaunch.activityRecreated = savedInstanceState != null
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        com.orismusic.client.settings.AppearanceSettings.init(this)

        // Like Echo and Mochy: ask for notification access on every start until granted, so the
        // player notification (and background survival) never depends on a dialog shown once.
        // Android itself limits how often the system dialog can appear. The first-run welcome
        // explains it and asks instead, so skip while that shows.
        if (!shouldWelcome() && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        setContent {
            OrisTheme {
                var showWelcome by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(shouldWelcome()) }
                if (showWelcome) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { },
                        title = { androidx.compose.material3.Text("Welcome to Oris") },
                        text = {
                            androidx.compose.material3.Text(
                                "Search any song and tap it to play. Open the player and tap Lyrics for synced words. " +
                                    "Make it yours in Settings: app icon, colours, lyrics sources. " +
                                    "Everything you like or play stays on this phone.\n\nNext, Android will ask to show the playback controls in your notifications.",
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                getSharedPreferences("oris_first_run", MODE_PRIVATE).edit().putBoolean("welcomed", true).apply()
                                showWelcome = false
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }) { androidx.compose.material3.Text("Got it") }
                        },
                    )
                }
                val connection = connectionState.value
                val hideBar = com.orismusic.client.settings.AppearanceSettings.hideStatusBar
                androidx.compose.runtime.SideEffect {
                    val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    bars.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (hideBar) bars.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    else bars.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                }
                val openScope = androidx.compose.runtime.rememberCoroutineScope()
                var navigation by rememberSaveable { mutableStateOf(NavigationState()) }
                com.orismusic.client.ui.navigation.PlayerLaunch.request = { navigation = navigation.openPlayer() }
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
                            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
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
                            androidx.compose.runtime.CompositionLocalProvider(com.orismusic.client.ui.LocalBottomInset provides innerPadding.calculateBottomPadding()) {
                            if (openDetail != null) {
                                com.orismusic.client.ui.screens.DetailScreen(
                                    target = openDetail,
                                    connection = connection,
                                    onBack = { detail = detailBack; detailBack = null },
                                    onOpen = { next -> openScope.launch { com.orismusic.client.ui.screens.openOrPlay(next, connection) { detailBack = openDetail; detail = it } } },
                                    modifier = Modifier.fillMaxSize().statusBarsPadding().padding(top = innerPadding.calculateTopPadding()),
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
                                    onOpen = { t -> openScope.launch { com.orismusic.client.ui.screens.openOrPlay(t, connection) { detail = it; detailBack = null } } },
                                    onSettings = { settingsOpen = true },
                                    onSearch = { navigation = navigation.select(MainTab.Search.ordinal) },
                                    modifier = Modifier.fillMaxSize(),
                                    bottomInset = innerPadding.calculateBottomPadding(),
                                    playerConnection = connection,
                                )
                                MainTab.Queue -> QueueScreen(
                                    onBack = { navigation = navigation.back() },
                                    onOpenPlayer = { navigation = navigation.openPlayer() },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(top = innerPadding.calculateTopPadding()),
                                    connection = connection,
                                )
                                MainTab.Search -> SearchScreen(
                                    connection = connection,
                                    onOpen = { t -> openScope.launch { com.orismusic.client.ui.screens.openOrPlay(t, connection) { detail = it; detailBack = null } } },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(top = innerPadding.calculateTopPadding()),
                                )
                                MainTab.Library -> com.orismusic.client.ui.screens.LibraryScreen(
                                    connection = connection,
                                    onQueue = { navigation = navigation.select(MainTab.Queue.ordinal) },
                                    onSettings = { settingsOpen = true },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .padding(top = innerPadding.calculateTopPadding()),
                                )
                            }
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
    val hidden = collapsed && com.orismusic.client.settings.NavSettings.hideOnScroll
    // Quick low-overshoot spring: the bar tucks away under the finger instead of bouncing out.
    val slide by androidx.compose.animation.core.animateFloatAsState(
        if (hidden) 1f else 0f, spring(0.9f, 700f), label = "dock hide",
    )
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().navigationBarsPadding()
            .padding(top = 4.dp, bottom = com.orismusic.client.settings.NavSettings.lift.bottom.dp)
            .graphicsLayer { translationY = slide * (size.height + 80.dp.toPx()) },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        com.orismusic.client.ui.components.OrisDock(
            count = tabs.size,
            selected = tabs.indexOfFirst { it.ordinal == selected },
            label = { tabs[it].label },
            icon = { icon(tabs[it]) },
            onSelect = { onSelect(tabs[it]) },
        )
    }
}

@androidx.compose.runtime.Composable
private fun NavItem(
    tab: MainTab, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean,
    showLabel: Boolean, onSelect: (MainTab) -> Unit, modifier: Modifier,
) {
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.tertiary else androidx.compose.ui.graphics.Color.Transparent,
        androidx.compose.animation.core.tween(260), label = "nav fill",
    )
    val tint = if (selected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
    val expanded = selected && showLabel
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .background(fill)
            .clickable { onSelect(tab) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = if (expanded) null else tab.label, tint = tint)
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandHorizontally(androidx.compose.animation.core.spring(0.8f, 380f)) + fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally(androidx.compose.animation.core.spring(0.8f, 380f)) + fadeOut(),
        ) {
            Text(tab.label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp))
        }
    }
}
