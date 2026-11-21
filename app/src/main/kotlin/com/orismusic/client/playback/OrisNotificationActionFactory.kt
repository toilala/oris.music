/*
 * Equivalent of media3's package-private DefaultActionFactory (Apache-2.0,
 * androidx.media3:media3-session 1.7.1), carried so Oris can build the media
 * notification itself instead of waiting on MediaNotificationManager, whose
 * deferred post never fires in Oris. Action pending intents are
 * ACTION_MEDIA_BUTTON key events routed back to the service, exactly like
 * media3's own factory.
 */

package com.orismusic.client.playback

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand

@UnstableApi
internal class OrisNotificationActionFactory(
    private val service: Service,
) : MediaNotification.ActionFactory {

    private var customActionPendingIntentRequestCode = 0

    // The uri only differentiates this session's pending intents; media3 builds the
    // same shape in MediaSessionImpl but keeps it package-private.
    private val sessionUri: Uri =
        Uri.Builder()
            .scheme("androidx.media3.session.MediaSessionImpl")
            .appendPath("oris")
            .appendPath(SystemClock.elapsedRealtime().toString())
            .build()

    override fun createMediaAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        command: Int,
    ): NotificationCompat.Action =
        NotificationCompat.Action
            .Builder(icon, title, createMediaActionPendingIntent(mediaSession, command.toLong()))
            .build()

    override fun createCustomAction(
        mediaSession: MediaSession,
        icon: IconCompat,
        title: CharSequence,
        customAction: String,
        extras: Bundle,
    ): NotificationCompat.Action =
        NotificationCompat.Action
            .Builder(icon, title, createCustomActionPendingIntent(customAction, extras))
            .build()

    override fun createCustomActionFromCustomCommandButton(
        mediaSession: MediaSession,
        commandButton: CommandButton,
    ): NotificationCompat.Action {
        val customCommand =
            requireNotNull(commandButton.sessionCommand) { "command button must carry a session command" }
        require(customCommand.commandCode == SessionCommand.COMMAND_CODE_CUSTOM) {
            "command button must be custom"
        }
        return NotificationCompat.Action
            .Builder(
                IconCompat.createWithResource(service, commandButton.iconResId),
                commandButton.displayName,
                createCustomActionPendingIntent(
                    customCommand.customAction,
                    customCommand.customExtras,
                ),
            ).build()
    }

    override fun createMediaActionPendingIntent(
        mediaSession: MediaSession,
        command: Long,
    ): PendingIntent {
        val keyCode = toKeyCode(command)
        val intent = mediaButtonIntent(keyCode)
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            command == Player.COMMAND_PLAY_PAUSE.toLong() &&
            !mediaSession.player.playWhenReady
        ) {
            PendingIntent.getForegroundService(service, keyCode, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getService(service, keyCode, intent, PendingIntent.FLAG_IMMUTABLE)
        }
    }

    private fun mediaButtonIntent(mediaKeyCode: Int): Intent =
        Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            data = sessionUri
            component = ComponentName(service, service.javaClass)
            putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, mediaKeyCode))
        }

    private fun createCustomActionPendingIntent(action: String, extras: Bundle): PendingIntent {
        val intent =
            Intent(ACTION_CUSTOM).apply {
                data = sessionUri
                component = ComponentName(service, service.javaClass)
                putExtra(EXTRAS_KEY_ACTION_CUSTOM, action)
                putExtra(EXTRAS_KEY_ACTION_CUSTOM_EXTRAS, extras)
            }
        return PendingIntent.getService(
            service,
            ++customActionPendingIntentRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun toKeyCode(command: Long): Int =
        when (command) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toLong(),
            Player.COMMAND_SEEK_TO_NEXT.toLong(),
            -> KeyEvent.KEYCODE_MEDIA_NEXT
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toLong(),
            Player.COMMAND_SEEK_TO_PREVIOUS.toLong(),
            -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            Player.COMMAND_STOP.toLong() -> KeyEvent.KEYCODE_MEDIA_STOP
            Player.COMMAND_SEEK_FORWARD.toLong() -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            Player.COMMAND_SEEK_BACK.toLong() -> KeyEvent.KEYCODE_MEDIA_REWIND
            Player.COMMAND_PLAY_PAUSE.toLong() -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> KeyEvent.KEYCODE_UNKNOWN
        }

    private companion object {
        const val ACTION_CUSTOM = "androidx.media3.session.CUSTOM_NOTIFICATION_ACTION"
        const val EXTRAS_KEY_ACTION_CUSTOM =
            "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION"
        const val EXTRAS_KEY_ACTION_CUSTOM_EXTRAS =
            "androidx.media3.session.EXTRAS_KEY_CUSTOM_NOTIFICATION_ACTION_EXTRAS"
    }
}
