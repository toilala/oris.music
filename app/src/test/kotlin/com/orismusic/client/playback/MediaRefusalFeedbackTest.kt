package com.orismusic.client.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.orismusic.client.utils.GuestAccessRefused
import com.orismusic.client.utils.InnerTubeXResolver
import com.orismusic.client.utils.YTPlayerUtils
import com.orismusic.client.utils.defaultMediaRefusalFeedback
import com.orismusic.client.utils.mediaRefusalFeedback
import com.orismusic.client.utils.refusedMediaUrl
import com.orismusic.client.utils.reportMediaRefusal
import java.io.IOException
import java.net.UnknownHostException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Media-fetch refusal feedback: an HTTP 403 on a minted media URL drops that URL from
 *  the resolve cache and excludes the client profile that minted it; anything else
 *  changes no client selection. No network is touched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MediaRefusalFeedbackTest {

    private val url = "https://rr3---sn-fake.googlevideo.com/videoplayback"

    @Before
    fun setUp() = restore()

    @After
    fun tearDown() = restore()

    private fun restore() {
        mediaRefusalFeedback = defaultMediaRefusalFeedback
        YTPlayerUtils.resolved.clear()
        InnerTubeXResolver.clearRefusalStateForTest()
    }

    private fun httpError(code: Int, target: String): HttpDataSource.InvalidResponseCodeException =
        HttpDataSource.InvalidResponseCodeException(
            code, null, null, emptyMap(), DataSpec.Builder().setUri(target).build(), ByteArray(0),
        )

    private fun extracted(target: String, profileId: String) = InnerTubeXResolver.Extracted(
        videoId = "vid",
        url = target,
        kbps = 128,
        mimeType = "audio/mp4",
        loudnessDb = null,
        clientName = "WEB_REMIX",
        profileId = profileId,
        headers = emptyMap(),
    )

    private fun loadErrorInfo(error: IOException): LoadErrorHandlingPolicy.LoadErrorInfo {
        val uri = Uri.parse("https://example.com/watch?v=vid")
        val spec = DataSpec.Builder().setUri(uri).build()
        val event = LoadEventInfo(0L, spec, 0L)
        return LoadErrorHandlingPolicy.LoadErrorInfo(event, MediaLoadData(C.DATA_TYPE_MEDIA), error, 1)
    }

    @Test
    fun `a 403 yields its minted media URL through wrappers`() {
        assertEquals(url, refusedMediaUrl(httpError(403, url)))
        assertEquals(url, refusedMediaUrl(IOException("load failed", httpError(403, url))))
    }

    @Test
    fun `other failures are not media refusals`() {
        assertNull(refusedMediaUrl(httpError(404, url)))
        assertNull(refusedMediaUrl(IOException("gone", UnknownHostException("dns"))))
        assertNull(refusedMediaUrl(null))
    }

    @Test
    fun `a refused catalog URL excludes its client profile once`() {
        InnerTubeXResolver.rememberForTest(extracted(url, "web_remix.botguard"))
        assertEquals("vid", InnerTubeXResolver.onRefused(url))
        assertEquals(setOf("web_remix.botguard"), InnerTubeXResolver.excludedForTest("vid"))
        // A repeated refusal of the same URL does not re-exclude or crash.
        assertNull(InnerTubeXResolver.onRefused(url))
    }

    @Test
    fun `an unminted 403 changes no client selection`() {
        reportMediaRefusal(IOException("load failed", httpError(403, url)))
        assertTrue(InnerTubeXResolver.excludedForTest("vid").isEmpty())
    }

    @Test
    fun `feedback evicts the cached stream whoever minted it`() {
        YTPlayerUtils.resolved["vid|128"] =
            System.currentTimeMillis() to YTPlayerUtils.PlaybackData(streamUrl = url, cacheKey = "vid")
        reportMediaRefusal(httpError(403, url))
        assertFalse(YTPlayerUtils.resolved.keys.any { it.startsWith("vid|") })
    }

    @Test
    fun `policy suppresses retries on guest refusal and reports fetch refusals`() {
        val policy = OrisLoadErrorPolicy()
        assertEquals(C.TIME_UNSET, policy.getRetryDelayMsFor(loadErrorInfo(GuestAccessRefused("bot"))))
        val seen = mutableListOf<Throwable?>()
        mediaRefusalFeedback = { seen += it }
        val error = IOException("load failed", httpError(403, url))
        policy.getRetryDelayMsFor(loadErrorInfo(error))
        assertEquals(listOf(error), seen)
    }
}
