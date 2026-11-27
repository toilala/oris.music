package com.orismusic.client.morph

import android.content.Intent
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
 * Self-contained cancel-recovery diagnostic: the sheet is opened inside THIS
 * instrumentation lifecycle immediately before injecting, so a harness process
 * restart surfaces as a setup failure, never as a product recovery verdict.
 * UiDevice screenshots and live sheet positions are captured pre-injection,
 * post-MOVE and post-CANCEL into the app cache diag dir (pulled over run-as) before any assertion.
 */
@RunWith(AndroidJUnit4::class)
class MorphCancelTest {

    private val diag = StringBuilder()

    @Test
    fun injectDragCancel() {
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
                File(dir, "cancel-diag.txt").writeText(diag.toString())
            } catch (e: Exception) {
                if (diagWarn == null) diagWarn = "diag write failed: ${e.message}"
            }
        }
        if (diagWarn != null) note("DIAG WARNING: " + diagWarn)

        // 1. Establish the starting state in this lifecycle: the sheet must be open.
        if (device.wait(Until.hasObject(By.desc("Collapse player")), 3_000) != true) {
            openSheet(device)
        }
        val openTop = sheetTop()
        note("open-top=$openTop")
        shot("cancel-1-open.png")
        check(openTop != null && openTop < 200) {
            "SETUP FAILURE: sheet never opened in the test process (openTop=$openTop); " +
                "harness lifecycle, not a recovery verdict"
        }

        // 2. Inject DOWN + MOVEs onto the sheet, then prove the sheet actually moved.
        val cx = device.displayWidth / 2f
        val t0 = SystemClock.uptimeMillis()
        fun ev(action: Int, x: Float, y: Float, t: Long) = MotionEvent.obtain(t0, t, action, x, y, 0)
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_DOWN, cx, 900f, t0), true)) { "DOWN not delivered" }
        var y = 900f
        for (i in 1..8) {
            y += 55f
            SystemClock.sleep(16)
            check(ui.injectInputEvent(ev(MotionEvent.ACTION_MOVE, cx, y, SystemClock.uptimeMillis()), true)) { "MOVE not delivered" }
        }
        SystemClock.sleep(150)
        val movedTop = sheetTop()
        note("moved-top=$movedTop")
        shot("cancel-2-moved.png")
        check(movedTop == null || movedTop > openTop + 100) {
            "SETUP FAILURE: injected drag never moved the sheet (openTop=$openTop movedTop=$movedTop); " +
                "gesture delivery problem, not a recovery verdict"
        }

        // 3. CANCEL: the sheet must settle back to fully open.
        check(ui.injectInputEvent(ev(MotionEvent.ACTION_CANCEL, cx, y, SystemClock.uptimeMillis()), true)) { "CANCEL not delivered" }
        SystemClock.sleep(120)
        note("after-cancel-top=" + sheetTop())
        shot("cancel-3-after-cancel.png")
        val settled = device.wait(Until.hasObject(By.desc("Collapse player")), 6_000)
        SystemClock.sleep(400)
        val settledTop = sheetTop()
        note("settled-top=$settledTop")
        shot("cancel-4-settled.png")
        check(settled == true && settledTop != null && settledTop < 200) {
            "RECOVERY FAILURE: after ACTION_CANCEL the sheet must settle back to fully open " +
                "(settled=$settled settledTop=$settledTop)" + (diagWarn?.let { " [diag: $it]" } ?: "")
        }
    }

    companion object {
        /** Start the debug tone inside the instrumentation process: am instrument restarts
         *  the app process, so any session the launch script began is already dead. */
        fun ensureTone(device: UiDevice, tone: String = "Runtime local tone") {
            if (device.wait(Until.hasObject(By.text(tone)), 3_000) == true) return
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val pkg = ctx.packageName
            ctx.startActivity(
                Intent().setClassName(pkg, "com.orismusic.client.debug.LocalPlaybackCheckActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            check(device.wait(Until.hasObject(By.text("Local audio playing")), 30_000)) {
                "SETUP FAILURE: local tone never reached playing in the test process"
            }
            SystemClock.sleep(2_000)
            ctx.startActivity(
                Intent().setClassName(pkg, "com.orismusic.client.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            check(device.wait(Until.hasObject(By.text(tone)), 25_000)) {
                "SETUP FAILURE: no track title on screen to open the sheet from"
            }
        }

        /** Open the player sheet by tapping the mini-player pill, from any resting state. */
        fun openSheet(device: UiDevice, tone: String = "Runtime local tone") {
            ensureTone(device, tone)
            // The pill is the lowest occurrence of the track title on screen; rows with
            // the same title in lists sit higher.
            val pill = device.findObjects(By.text(tone)).maxByOrNull { it.visibleBounds.top }
            check(pill != null) { "SETUP FAILURE: mini player pill not found" }
            val b = pill.visibleBounds
            device.click(b.centerX(), b.centerY())
            check(device.wait(Until.hasObject(By.desc("Collapse player")), 8_000)) {
                "SETUP FAILURE: pill tap never opened the sheet"
            }
        }
    }
}
