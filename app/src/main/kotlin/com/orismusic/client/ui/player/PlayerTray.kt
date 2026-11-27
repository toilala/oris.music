package com.orismusic.client.ui.player

import com.orismusic.client.ui.theme.OrisMotion
import com.orismusic.client.ui.theme.OrisShapes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.orismusic.client.library.PlaylistStore
import com.orismusic.client.library.currentSavedSong
import com.orismusic.client.playback.PlayerConnection
import com.orismusic.client.playback.SleepTimer
import com.orismusic.client.ui.components.Artwork
import com.orismusic.client.ui.components.DiagnosticHistoryDialog

/**
 * Everything that is not play, skip or lyrics lives here, behind one swipe up.
 * Big tiles for what you reach for most (shuffle, radio), smaller ones for the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTray(
    connection: PlayerConnection,
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
    stopAfterSongActive: Boolean = false,
    onStopAfterSong: () -> Unit = {},
    onCancelStopAfterSong: () -> Unit = {},
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val shuffle by connection.shuffleModeEnabled.collectAsState()
    val repeat by connection.repeatMode.collectAsState()
    val index by connection.currentMediaItemIndex.collectAsState()
    val metadata by connection.currentMetadata.collectAsState()
    val song = remember(metadata, index) { connection.currentSavedSong() }
    val player = connection.player
    val prev = remember(index) { player.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it) } }
    val next = remember(index) { player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = OrisShapes.Sheet,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PeekCard("Previous", prev, Modifier.weight(1f)) {
                    player.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { connection.seekToIndex(it) }; onDismiss()
                }
                PeekCard("Up next", next, Modifier.weight(1f)) {
                    player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { connection.seekToIndex(it) }; onDismiss()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(Icons.Rounded.Shuffle, if (shuffle) "Shuffle on" else "Shuffle", shuffle, 88.dp, Modifier.weight(1.25f)) {
                    connection.toggleShuffle()
                }
                ActionTile(Icons.Rounded.Radio, "Radio", false, 88.dp, Modifier.weight(1f)) {
                    connection.startRadioSeamlessly()
                    android.widget.Toast.makeText(context, "Radio started from this song", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(
                    if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (repeat) { Player.REPEAT_MODE_ONE -> "Repeat one"; Player.REPEAT_MODE_ALL -> "Repeat all"; else -> "Repeat" },
                    repeat != Player.REPEAT_MODE_OFF, 68.dp, Modifier.weight(1f),
                ) { connection.toggleRepeatMode() }
                ActionTile(Icons.Rounded.QueueMusic, "Queue", false, 68.dp, Modifier.weight(1f)) { onDismiss(); onQueue() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var playlistMenu by remember { mutableStateOf(false) }
                val store = PlaylistStore.get(context)
                val playlists by store.playlists.collectAsState()
                Box(Modifier.weight(1f)) {
                    ActionTile(Icons.Rounded.PlaylistAdd, "Playlist", false, 56.dp, Modifier.fillMaxWidth()) { playlistMenu = true }
                    if (playlistMenu) {
                        com.orismusic.client.ui.components.SongActionsSheet(
                            title = song?.title ?: "Nothing playing",
                            subtitle = song?.artist,
                            thumbnail = song?.thumbnail,
                            actions = mutableListOf<com.orismusic.client.ui.components.SongAction>().apply {
                                if (playlists.isEmpty()) add(com.orismusic.client.ui.components.SongAction("Make a playlist in Library first",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant) { playlistMenu = false })
                                playlists.forEach { p ->
                                    add(com.orismusic.client.ui.components.SongAction(p.name,
                                        enabled = song != null) { song?.let { store.add(p.id, it) }; playlistMenu = false })
                                }
                            },
                            onDismiss = { playlistMenu = false },
                        )
                    }
                }
                var timerMenu by remember { mutableStateOf(false) }
                val timerEnd by SleepTimer.endsAt.collectAsState()
                Box(Modifier.weight(1f)) {
                    ActionTile(Icons.Rounded.Timer, "Sleep", timerEnd != null || stopAfterSongActive, 56.dp, Modifier.fillMaxWidth()) { timerMenu = true }
                    DropdownMenu(timerMenu, { timerMenu = false }) {
                        if (timerEnd != null || stopAfterSongActive) DropdownMenuItem({ Text("Turn off timer") }, { SleepTimer.cancel(); onCancelStopAfterSong(); timerMenu = false })
                        DropdownMenuItem({ Text("Stop when this song ends") }, { onStopAfterSong(); timerMenu = false })
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            DropdownMenuItem({ Text("Stop after $minutes min") }, { onCancelStopAfterSong(); SleepTimer.start(minutes) { connection.pause() }; timerMenu = false })
                        }
                    }
                }
                ActionTile(Icons.Rounded.Share, "Share", false, 56.dp, Modifier.weight(1f)) {
                    val id = player.currentMediaItem?.mediaId.orEmpty()
                    if (id.isNotBlank()) runCatching {
                        val text = (metadata?.title?.toString().orEmpty() + " https://music.youtube.com/watch?v=" + id).trim()
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share song").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                var diagnosticOpen by remember { mutableStateOf(false) }
                val failureActive by com.orismusic.client.utils.PlaybackDiagnostic.activeFailure.collectAsState()
                ActionTile(Icons.Rounded.Info, if (failureActive != null) "Issue found" else "Diagnostics", failureActive != null, 56.dp, Modifier.weight(1f)) { diagnosticOpen = true }
                if (diagnosticOpen) DiagnosticHistoryDialog { diagnosticOpen = false }
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    on: Boolean,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, OrisMotion.press(), label = "tile press")
    val container = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest
    val tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = OrisShapes.Panel,
        color = container,
        modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (height >= 80.dp) 30.dp else 24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PeekCard(label: String, item: androidx.media3.common.MediaItem?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = item != null,
        shape = OrisShapes.Panel,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.height(64.dp),
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(item?.mediaMetadata?.artworkUri?.toString(), Modifier.size(48.dp), OrisShapes.Thumb, px = 200)
            Column(Modifier.padding(start = 10.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    item?.mediaMetadata?.title?.toString() ?: "Nothing yet",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
