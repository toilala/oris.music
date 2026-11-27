package com.orismusic.client.playback

import com.metrolist.innertubex.extraction.StreamResolveException
import com.orismusic.client.constants.AudioQuality
import com.orismusic.client.utils.GuestAccessRefused
import com.orismusic.client.utils.InnerTubeXResolver
import com.orismusic.client.utils.YTPlayerUtils
import com.orismusic.client.utils.YTPlayerUtils.PlaybackData
import com.orismusic.client.utils.hasGuestRefusal
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** Stage-ordering coverage for the resolve chain: a refusal of the direct guest stage
 *  falls through to the maintained catalog, and a final error after a refusal stays
 *  recognizable so retry suppression still engages. No network is touched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ResolveStageOrderTest {
    private val ctx get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUpSeams() = restoreDefaults()

    @After
    fun tearDownSeams() = restoreDefaults()

    private fun restoreDefaults() {
        YTPlayerUtils.resolveTimeoutMs = 30_000L
        YTPlayerUtils.networkOnline = { true }
        YTPlayerUtils.directStage = YTPlayerUtils.defaultDirectStage
        YTPlayerUtils.extractStage = YTPlayerUtils.defaultExtractStage
        YTPlayerUtils.resolveStage = YTPlayerUtils.defaultResolveStage
        YTPlayerUtils.resolved.clear()
    }

    private fun extracted(url: String) = InnerTubeXResolver.Extracted(
        videoId = "vid",
        url = url,
        kbps = 128,
        mimeType = "audio/mp4",
        loudnessDb = null,
        clientName = "WEB_REMIX",
        profileId = "web_remix.botguard",
        headers = emptyMap(),
    )

    @Test
    fun `direct success never touches the catalog`() = runTest {
        YTPlayerUtils.directStage = { _, _, _ ->
            PlaybackData(streamUrl = "https://direct/stream", cacheKey = "key", mimeType = "audio/mp4", bitrate = 128000)
        }
        YTPlayerUtils.extractStage = { _, _ -> error("catalog must not run after direct success") }
        val data = YTPlayerUtils.getPlaybackData("vid-direct", AudioQuality.AUTO, ctx)
        assertEquals("https://direct/stream", data.streamUrl)
    }

    @Test
    fun `guest refusal falls back to the catalog`() = runTest {
        val order = mutableListOf<String>()
        YTPlayerUtils.directStage = { _, _, _ ->
            order += "direct"
            throw GuestAccessRefused("bot check")
        }
        YTPlayerUtils.extractStage = { _, _ ->
            order += "catalog"
            extracted("https://catalog/stream")
        }
        val data = YTPlayerUtils.getPlaybackData("vid-fallback", AudioQuality.AUTO, ctx)
        assertEquals(listOf("direct", "catalog"), order)
        assertEquals("https://catalog/stream", data.streamUrl)
    }

    @Test
    fun `empty catalog after refusal stays retry-suppressed`() = runTest {
        YTPlayerUtils.directStage = { _, _, _ -> throw GuestAccessRefused("bot check") }
        YTPlayerUtils.extractStage = { _, _ -> null }
        try {
            YTPlayerUtils.getPlaybackData("vid-empty", AudioQuality.AUTO, ctx)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue("final error must carry the refusal", hasGuestRefusal(e))
        }
    }

    @Test
    fun `catalog failure after refusal stays retry-suppressed`() = runTest {
        YTPlayerUtils.directStage = { _, _, _ -> throw GuestAccessRefused("bot check") }
        YTPlayerUtils.extractStage = { _, _ ->
            throw StreamResolveException(StreamResolveException.Reason.NETWORK, "Unable to fetch stream data from YouTube.")
        }
        try {
            YTPlayerUtils.getPlaybackData("vid-exhausted", AudioQuality.AUTO, ctx)
            fail("expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("Cannot resolve"))
            assertTrue("final error must carry the refusal", hasGuestRefusal(e))
        }
    }

    @Test
    fun `plain failure without refusal is not retry-suppressed`() = runTest {
        YTPlayerUtils.directStage = { _, _, _ -> null }
        YTPlayerUtils.extractStage = { _, _ -> null }
        try {
            YTPlayerUtils.getPlaybackData("vid-plain", AudioQuality.AUTO, ctx)
            fail("expected IOException")
        } catch (e: IOException) {
            assertFalse(hasGuestRefusal(e))
        }
    }
}
