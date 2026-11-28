package com.orismusic.client.morph

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Self-contained grab-mid-close diagnostic: an injected drag puts the sheet partway
 * down, a gentle release (velocity buffer diluted below the fling threshold) starts a
 * real close, the close's movement is proven from live bounds, and only then does a
 * second touch re-grab the sheet. Recovery asserts the sheet settles back to open.
 */
@RunWith(AndroidJUnit4::class)
class MorphGrabTest {

    private val diag = StringBuilder()

    @Test
    fun grabMidClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = instrumentation.uiAutomation
        val device = UiDevice.getInstance(instrumentation)
        val dir = File(instrumentation.targetContext.cacheDir, "oris-morph-diag")
        var diagWarn: String? = null
        try {
            dir.mkdirs()
            val probe = File(dir, "probe.txt")
            probe.writeText("ok")
            if (probe.readText() != "ok") diagWarn = "diag probe read-back mismatch"
            probe.delete()
        } catch (e: Exception) {
            diagWarn = "diag probe failed: ${e.message}"
        }

        fun sheetTop(): Int? {
            repeat(3) {
                try {
                    return device.findObject(By.desc("Collapse player"))?.visibleBounds?.top
                } catch (stale: androidx.test.uiautomator.StaleObjectException) {
                    // The node was recycled mid-animation; ask again.
                }
            }
            return null
        }
        fun settleTop(): Int? {
            var last: Int? = null
            var stable = 0
            val deadline = SystemClock.uptimeMillis() + 6_000
            while (SystemClock.uptimeMillis() < deadline) {
                val cur = sheetTop()
                if (cur != null && last != null && Math.abs(cur - last) <= 8) {
                    stable++
                    if (stable >= 3) return cur
                } else {
                    stable = 0
                }
                last = cur
                SystemClock.sleep(120)
            }
            return last
        }
        fun shot(name: String) {
            try {
                if (!device.takeScreenshot(File(dir, name)) && diagWarn == null) diagWarn = "screenshot false: $name"
            } catch (e: Exception) {
                if (diagWarn == null) diagWarn = "screenshot failed $name: ${e.message}"
            }
        }
        fun note(line: String) {
            diag.appendLine(line)
            android.util.Log.i("OrisMorphDiag", line)
            try {
                File(dir, "grab-diag.txt").writeText(diag.toString())
            } catch (e: Exception) {
                if (diagWarn == null) diagWarn = "diag write failed: ${e.message}"
            }
        }
        fun move(cx: Float, y: Float, t0: Long) {
            check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_MOVE, cx, y, SystemClock.uptimeMillis(), t0), true)) { "MOVE not delivered" }
        }
        if (diagWarn != null) note("DIAG WARNING: " + diagWarn)

        if (device.wait(Until.hasObject(By.desc("Collapse player")), 3_000) != true) {
            MorphCancelTest.openSheet(device)
        }
        val openTop = settleTop()
        note("open-top=$openTop")
        shot("grab-1-open.png")
        check(openTop != null && openTop < device.displayHeight / 4) {
            "SETUP FAILURE: sheet never opened in the test process (openTop=$openTop); " +
                "harness lifecycle, not a recovery verdict"
        }

        // Drag the sheet partway down, then dilute the velocity buffer so the release
        // is gentle: a fling release closes faster than any grab can land.
        val cx = device.displayWidth / 2f
        val t0 = SystemClock.uptimeMillis()
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_DOWN, cx, 900f, t0, t0), true)) { "DOWN not delivered" }
        var y = 900f
        for (i in 1..12) {
            y += 55f
            SystemClock.sleep(16)
            move(cx, y, t0)
        }
        SystemClock.sleep(150)
        val draggedTop = sheetTop()
        note("dragged-top=$draggedTop")
        shot("grab-2-dragged.png")
        check(draggedTop != null && draggedTop > openTop + 300 && draggedTop < openTop + 1000) {
            "SETUP FAILURE: injected drag left the sheet at $draggedTop (openTop=$openTop); " +
                "gesture delivery problem, not a grab verdict"
        }
        for (i in 1..5) {
            y += 2f
            SystemClock.sleep(12)
            move(cx, y, t0)
        }
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_UP, cx, y, SystemClock.uptimeMillis(), t0), true)) { "UP not delivered" }

        // The close runs on a short spring and a UiAutomator query costs more than the
        // window it would measure, so re-grab straight after the release instead of polling.
        val closeTop = draggedTop
        SystemClock.sleep(60)

        // Re-grab inside the live sheet bounds and hold: changed mind, sheet must reopen.
        val grabY = (closeTop + 400f).coerceAtMost(device.displayHeight - 160f)
        val t1 = SystemClock.uptimeMillis()
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_DOWN, cx, grabY, t1, t1), true)) { "grab DOWN not delivered" }
        SystemClock.sleep(25)
        val grabTop = sheetTop()
        note("grab-top=$grabTop grab-y=$grabY")
        check(grabTop != null && grabTop > openTop + 150) {
            "SETUP FAILURE: the close finished before the re-grab could land (grabTop=$grabTop)"
        }
        check(grabTop < grabY) {
            "SETUP FAILURE: grab finger missed the sheet (sheet top $grabTop below finger $grabY)"
        }
        move(cx, grabY + 40f, t1)
        SystemClock.sleep(250)
        for (i in 1..4) {
            move(cx, grabY + 40f + 2f * i, t1)
            SystemClock.sleep(10)
        }
        val heldTop = sheetTop()
        note("held-top=$heldTop")
        shot("grab-3-held.png")
        check(heldTop != null) { "SETUP FAILURE: sheet vanished under the grab (hit region missed)" }
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_UP, cx, grabY + 48f, SystemClock.uptimeMillis(), t1), true)) { "grab UP not delivered" }

        // The grab interrupted a close, so the sheet must settle back to open.
        val settled = device.wait(Until.hasObject(By.desc("Collapse player")), 6_000)
        SystemClock.sleep(500)
        val settledTop = sheetTop()
        note("settled-top=$settledTop")
        shot("grab-4-settled.png")
        check(settled == true && settledTop != null && settledTop <= openTop + 24) {
            "RECOVERY FAILURE: grabbing the sheet mid-close must settle back to open " +
                "(settled=$settled settledTop=$settledTop)" + (diagWarn?.let { " [diag: $it]" } ?: "")
        }
    }
}
