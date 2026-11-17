package com.orismusic.client.lyrics

import android.content.Context
import com.orismusic.client.settings.LyricsPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class LyricsAdaptersTest {
    @Test fun providerOrderAndDisableChoicesSurviveNewReader() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("lyrics_sources", Context.MODE_PRIVATE).edit().clear().commit()
        val preferences = LyricsPreferences(context)
        val options = preferences.read().move("better", -1).enable("lrclib", false)
        preferences.save(options)
        assertEquals(options, LyricsPreferences(context).read())
    }
    @Test fun allDisabledReturnsWithoutCallingASource() = kotlinx.coroutines.runBlocking {
        val allOff = restoreLyricsOptions(emptyList(), LyricsSources.all.map { it.id }.toSet())
        assertNull(LyricsRepository.load("test", "Test", "Test", "", 1, "automatic", allOff))
        assertNull(LyricsRepository.load("test", "Test", "Test", "", 1, "lrclib", allOff))
    }
    @Test fun youlyUsesLineTimesAndDoesNotSplitWords() {
        val lines = ExtraLyricsSources.decodeYouly(JSONObject("""{"type":"Word","lyrics":[{"time":1250,"text":"Hello world","syllabus":[{"text":"Hel"},{"text":"lo"}]}]}"""))
        assertEquals(listOf(LyricLine(1250,"Hello world")), lines)
    }
    @Test fun paxReadsTtmlBeforePlainText() {
        val lines = ExtraLyricsSources.decodePax(JSONObject("""{"ttmlContent":"<tt><p begin='2.5'>Hello world</p></tt>","plain":"Other text"}"""))
        assertEquals(listOf(LyricLine(2500,"Hello world")), lines)
    }
    @Test fun unisonRequiresSubmitterAttribution() {
        val missing = JSONObject("""{"videoId":"12345678901","format":"lrc","lyrics":"[00:01]Hello"}""")
        assertNull(ExtraLyricsSources.decodeUnison(missing))
        missing.put("submitter", JSONObject("""{"displayName":"Test curator","keyId":"abc123"}"""))
        val result = ExtraLyricsSources.decodeUnison(missing)!!
        assertEquals(2, result.credits.size)
        assertEquals("Submitted by Test curator", result.credits.last().label)
        assertTrue(result.timed)
    }
}
