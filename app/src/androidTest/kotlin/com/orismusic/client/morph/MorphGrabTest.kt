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
 * down, release starts the close, and a second touch re-grabs it while its top edge
 * is still on the move. The grab point is chosen from where the drag left the sheet,
 * so no polling has to race the close animation. Movement and hit region are proven
 * from live bounds and UiDevice screenshots before the settle assertion runs.
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

        fun sheetTop(): Int? = device.findObject(By.desc("Collapse player"))?.visibleBounds?.top
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

        // Drag the sheet partway down; the release then starts a real close.
        val cx = device.displayWidth / 2f
        val t0 = SystemClock.uptimeMillis()
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_DOWN, cx, 900f, t0, t0), true)) { "DOWN not delivered" }
        var y = 900f
        for (i in 1..12) {
            y += 55f
            SystemClock.sleep(16)
            check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_MOVE, cx, y, SystemClock.uptimeMillis(), t0), true)) { "MOVE not delivered" }
        }
        SystemClock.sleep(150)
        val draggedTop = sheetTop()
        note("dragged-top=$draggedTop")
        shot("grab-2-dragged.png")
        check(draggedTop != null && draggedTop > openTop + 300 && draggedTop < openTop + 1000) {
            "SETUP FAILURE: injected drag left the sheet at $draggedTop (openTop=$openTop); " +
                "gesture delivery problem, not a grab verdict"
        }
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_UP, cx, y, SystemClock.uptimeMillis(), t0), true)) { "UP not delivered" }

        // Re-grab while the close is in flight. The drag left the sheet near draggedTop,
        // and a finger at grabY stays on the sheet for the first stretch of the close.
        SystemClock.sleep(60)
        val grabY = (draggedTop + 700f).coerceAtMost(device.displayHeight - 240f)
        val t1 = SystemClock.uptimeMillis()
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_DOWN, cx, grabY, t1, t1), true)) { "grab DOWN not delivered" }
        SystemClock.sleep(30)
        val grabTop = sheetTop()
        note("grab-top=$grabTop grab-y=$grabY")
        check(grabTop != null && grabTop > openTop + 200) {
            "SETUP FAILURE: the close finished before the re-grab could land (grabTop=$grabTop)"
        }
        check(grabTop < grabY) {
            "SETUP FAILURE: grab finger missed the sheet (sheet top $grabTop below finger $grabY)"
        }
        // Engage the drag detector, then hold: the grab itself is the changed mind.
        check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_MOVE, cx, grabY + 40f, SystemClock.uptimeMillis(), t1), true)) { "grab MOVE not delivered" }
        SystemClock.sleep(250)
        for (i in 1..4) {
            check(ui.injectInputEvent(MorphCancelTest.ev(MotionEvent.ACTION_MOVE, cx, grabY + 40f + 2f * i, SystemClock.uptimeMillis(), t1), true)) { "hold MOVE not delivered" }
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
