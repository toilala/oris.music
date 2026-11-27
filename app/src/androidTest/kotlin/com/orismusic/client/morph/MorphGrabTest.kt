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
 * Self-contained grab-mid-close diagnostic (leg 7, moved in-process): BACK starts the
 * close, the test catches the sheet while its top edge is moving, grabs INSIDE the
 * live sheet bounds and holds. Movement and hit region are proven from live bounds
 * and UiDevice screenshots before the settle assertion runs, so input latency and
 * harness restarts read as setup failures, never as product recovery verdicts.
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

        // 1. Establish open in this lifecycle.
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

        // 2. BACK starts the close; catch the sheet while it is actually moving.
        device.pressBack()
        var grabTop: Int? = null
        var probes = 0
        while (probes < 600) {
            val t = sheetTop()
            if (t == null) break // closed before a grab could land
            if (t > 480) { grabTop = t; break }
            SystemClock.sleep(5)
            probes++
        }
        note("grab-top=$grabTop probes=$probes")
        check(grabTop != null) {
            "SETUP FAILURE: the close finished before any grab could land; never saw the sheet mid-close"
        }

        // 3. Grab inside the live sheet bounds and hold: changed mind, sheet must reopen.
        val cx = device.displayWidth / 2f
        val gy = (grabTop + 600f).coerceAtMost(device.displayHeight - 200f)
        note("grab-point=($cx,$gy) sheet-top-at-grab=$grabTop")
        val t0 = SystemClock.uptimeMillis()
        fun ev(action: Int, x: Float, y: Float, t: Long) = MotionEvent.obtain(t0, t, action, x, y, 0)
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_DOWN, cx, gy, t0), true)) { "DOWN not delivered" }
        SystemClock.sleep(120)
        val heldTop = sheetTop()
        note("held-top=$heldTop")
        shot("grab-2-held.png")
        check(heldTop != null) { "SETUP FAILURE: sheet vanished under the grab (hit region missed)" }
        // Small wiggle so the drag detector owns the gesture, then release.
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_MOVE, cx, gy - 40f, SystemClock.uptimeMillis()), true)) { "MOVE not delivered" }
        SystemClock.sleep(250)
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_UP, cx, gy - 40f, SystemClock.uptimeMillis()), true)) { "UP not delivered" }

        // 4. Settle: the grab interrupted a close, so the sheet must settle open.
        val settled = device.wait(Until.hasObject(By.desc("Collapse player")), 6_000)
        SystemClock.sleep(500)
        val settledTop = sheetTop()
        note("settled-top=$settledTop")
        shot("grab-3-settled.png")
        check(settled == true && settledTop != null && settledTop <= openTop + 24) {
            "RECOVERY FAILURE: grabbing the sheet mid-close must settle back to open " +
                "(settled=$settled settledTop=$settledTop)" + (diagWarn?.let { " [diag: $it]" } ?: "")
        }
    }
}
