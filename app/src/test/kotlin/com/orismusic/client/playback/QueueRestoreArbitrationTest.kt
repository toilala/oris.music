package com.orismusic.client.playback

import androidx.media3.common.MediaItem
import org.robolectric.RuntimeEnvironment
import com.orismusic.client.models.MediaMetadata
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

/**
 * Pins cold-start restore arbitration: the snapshot restore must be fully applied before
 * onCreate returns, so playback that begins right after startup (controller bind, media
 * button, quick tap) is never replaced by a late apply.
 *
 * Pre-fix discrimination is EXPECTED, not observed on CI: on the pre-fix code the restore
 * rode playQueue's coroutine on Dispatchers.Main, which under this PAUSED looper only runs
 * at idle time, so the two stomp cases would see the fresh item replaced at idle. The
 * no-snapshot and clear cases pin current behaviour and pass on either ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = com.orismusic.client.OrisApp::class)
@LooperMode(LooperMode.Mode.PAUSED)
class QueueRestoreArbitrationTest {

    private var controller: ServiceController<OrisMediaService>? = null

    @org.junit.Before
    fun setUp() {
        // Explicit fixture clearing: no assumption about sandbox reuse between methods.
        val ctx = RuntimeEnvironment.getApplication() as android.content.Context
        java.io.File(ctx.filesDir, "queue_snapshot.json").delete()
        ctx.getSharedPreferences("playback_behavior", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @After
    fun tearDown() {
        // Never let cleanup failure mask the assertion that mattered.
        runCatching { controller?.destroy() }
    }

    private fun seedSnapshot() {
        QueueSnapshotStore.save(
            RuntimeEnvironment.getApplication(),
            "Mix",
            listOf(
                MediaMetadata(
                    id = "restored-song",
                    title = "Restored Song",
                    artists = listOf(MediaMetadata.Artist(id = null, name = "Someone")),
                    duration = 200,
                ),
            ),
            0,
            0L,
        )
    }

    private fun startService(): OrisMediaService {
        val c = Robolectric.buildService(OrisMediaService::class.java)
        controller = c
        return c.create().get()
    }

    private fun freshItem() = MediaItem.Builder()
        .setMediaId("fresh-song")
        .setUri("file:///data/local/tmp/fresh.mp3")
        .build()

    @Test
    fun idleRestoreAppliesSynchronouslyPaused() {
        seedSnapshot()
        val service = startService()
        // No looper idle: the restore must already be on the player when onCreate returns.
        assertEquals(1, service.player.mediaItemCount)
        assertEquals("restored-song", service.player.currentMediaItem?.mediaId)
        assertFalse(service.player.playWhenReady)
    }

    @Test
    fun freshPlayAfterCreateIsNotStompedByPendingRestore() {
        seedSnapshot()
        val service = startService()
        service.player.setMediaItem(freshItem())
        service.player.playWhenReady = true
        service.player.prepare()
        ShadowLooper.idleMainLooper()
        assertEquals("fresh-song", service.player.currentMediaItem?.mediaId)
        assertEquals(1, service.player.mediaItemCount)
        assertTrue(service.player.playWhenReady)
    }

    @Test
    fun pausedNewQueueIsNotStompedByPendingRestore() {
        seedSnapshot()
        val service = startService()
        service.player.setMediaItem(freshItem())
        ShadowLooper.idleMainLooper()
        assertEquals("fresh-song", service.player.currentMediaItem?.mediaId)
        assertFalse(service.player.playWhenReady)
    }

    @Test
    fun clearIntentAfterCreateStaysEmpty() {
        seedSnapshot()
        val service = startService()
        service.player.clearMediaItems()
        ShadowLooper.idleMainLooper()
        assertEquals(0, service.player.mediaItemCount)
    }

    @Test
    fun noSnapshotStaysEmpty() {
        val service = startService()
        ShadowLooper.idleMainLooper()
        assertEquals(0, service.player.mediaItemCount)
    }
}
