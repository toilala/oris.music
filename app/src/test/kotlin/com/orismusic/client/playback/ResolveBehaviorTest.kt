package com.orismusic.client.playback

import com.orismusic.client.constants.AudioQuality
import com.orismusic.client.utils.YTPlayerUtils.PlaybackData
import com.orismusic.client.utils.YTPlayerUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** Deterministic behavior coverage for the resolve failure paths. No network is touched:
 *  every test drives the seam YTPlayerUtils exposes for exactly this purpose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ResolveBehaviorTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUpSeams() = restoreDefaults()

    @After
    fun tearDownSeams() = restoreDefaults()

    private fun restoreDefaults() {
        YTPlayerUtils.resolveTimeoutMs = 30_000L
        YTPlayerUtils.networkOnline = { true }
        YTPlayerUtils.resolveStage = { _, _, _, _ -> error("stage not stubbed") }
        YTPlayerUtils.resolved.clear()
    }

    @Test
    fun `slow resolve is cut by the timeout`() = runTest {
        YTPlayerUtils.resolveTimeoutMs = 500
        YTPlayerUtils.resolveStage = { _, _, _, _ -> delay(60_000); error("must never complete") }
        try {
            YTPlayerUtils.getPlaybackData("vid-timeout", AudioQuality.AUTO, ctx)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue("expected timeout, got: ${e.message}", e.message.orEmpty().contains("timed out"))
        }
    }

    @Test
    fun `external cancellation propagates as cancellation`() = runTest {
        YTPlayerUtils.resolveStage = { _, _, _, _ -> awaitCancellation() }
        val job = async(Dispatchers.Default) {
            YTPlayerUtils.getPlaybackData("vid-cancel", AudioQuality.AUTO, ctx)
        }
        delay(100)
        job.cancel()
        try {
            job.await()
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // Correct: a cancelled load must surface as cancellation, not as a playback failure.
        }
    }

    @Test
    fun `no network fails fast without touching the resolver`() = runTest {
        YTPlayerUtils.networkOnline = { false }
        var stageRan = false
        YTPlayerUtils.resolveStage = { _, _, _, _ -> stageRan = true; error("must not run") }
        try {
            YTPlayerUtils.getPlaybackData("vid-offline", AudioQuality.AUTO, ctx)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("no network"))
        }
        assertTrue("resolver stage must not run when the OS reports no network", !stageRan)
    }

    @Test
    fun `fresh cache is served with no network and no resolver call`() = runTest {
        val cached = PlaybackData(streamUrl = "https://cached/stream", cacheKey = "key", mimeType = "audio/mp4", bitrate = 128000)
        YTPlayerUtils.resolved["vid-cached|128"] = System.currentTimeMillis() to cached
        YTPlayerUtils.networkOnline = { false }
        YTPlayerUtils.resolveStage = { _, _, _, _ -> error("must not run") }
        val data = YTPlayerUtils.getPlaybackData("vid-cached", AudioQuality.AUTO, ctx)
        assertEquals("https://cached/stream", data.streamUrl)
    }
}
