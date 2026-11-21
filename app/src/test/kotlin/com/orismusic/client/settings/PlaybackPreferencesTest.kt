package com.orismusic.client.settings

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PlaybackPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() { context.getSharedPreferences("playback_behavior", Context.MODE_PRIVATE).edit().clear().commit() }
    @Test fun removalDefaultsToKeepPlaying() {
        assertTrue(PlaybackPreferences(context).keepPlayingAfterTaskRemoval)
    }
    @Test fun explicitOptInSurvivesANewReader() {
        PlaybackPreferences(context).keepPlayingAfterTaskRemoval = true
        assertTrue(PlaybackPreferences(context).keepPlayingAfterTaskRemoval)
    }
    @Test fun optingOutIsImmediatelyVisibleToTheServiceReader() {
        val serviceReader = PlaybackPreferences(context)
        PlaybackPreferences(context).keepPlayingAfterTaskRemoval = true
        PlaybackPreferences(context).keepPlayingAfterTaskRemoval = false
        assertFalse(serviceReader.keepPlayingAfterTaskRemoval)
    }
}
