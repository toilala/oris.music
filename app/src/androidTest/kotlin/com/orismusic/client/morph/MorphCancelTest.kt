package com.orismusic.client.morph

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Injects a real DOWN -> MOVE* -> CANCEL stream at the player sheet so the morph's
 * onDragCancel path runs on device; adb shell input cannot produce ACTION_CANCEL.
 * State assertions live in morph_cancel_check.py around this injection.
 */
@RunWith(AndroidJUnit4::class)
class MorphCancelTest {
    @Test
    fun injectDragCancel() {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val t0 = SystemClock.uptimeMillis()
        fun ev(action: Int, x: Float, y: Float, t: Long) =
            MotionEvent.obtain(t0, t, action, x, y, 0)
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_DOWN, 540f, 900f, t0), true)) { "DOWN not delivered" }
        var y = 900f
        for (i in 1..8) {
            y += 55f
            SystemClock.sleep(16)
            check(ui.injectInputEvent(ev(MotionEvent.ACTION_MOVE, 540f, y, SystemClock.uptimeMillis()), true)) { "MOVE not delivered" }
        }
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_CANCEL, 540f, y, SystemClock.uptimeMillis()), true)) { "CANCEL not delivered" }
    }
}
