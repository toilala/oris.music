package com.orismusic.client.nav

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.orismusic.client.morph.MorphCancelTest
import com.orismusic.client.settings.NavSettings
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/**
 * Hide-on-scroll verdict in-process: the dial is set through the same Compose state the
 * settings page drives, a track is loaded so the dock shows its full cluster, and the
 * swipe is anchored on vertical feed content. Scroll is proven from the greeting's
 * geometry; the verdict asserts the nav row leaves the tree while the mini player stays.
 */
@RunWith(AndroidJUnit4::class)
class NavHideTest {

    private val diag = StringBuilder()

    @Test
    fun navHidesWhileMiniPlayerStays() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val dir = File(instrumentation.targetContext.cacheDir, "oris-nav-diag")
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

        fun shot(name: String) {
            try {
                if (!device.takeScreenshot(File(dir, name)) && diagWarn == null) diagWarn = "screenshot false: $name"
            } catch (e: Exception) {
                if (diagWarn == null) diagWarn = "screenshot failed $name: ${e.message}"
            }
        }
        fun note(line: String) {
            diag.appendLine(line)
            android.util.Log.i("OrisNavDiag", line)
            try {
                File(dir, "nav-diag.txt").writeText(diag.toString())
            } catch (e: Exception) {
                if (diagWarn == null) diagWarn = "diag write failed: ${e.message}"
            }
        }
        if (diagWarn != null) note("DIAG WARNING: " + diagWarn)

        val greetingPattern = Pattern.compile("^(Good |Late-night|Your late-night).*")
        fun greeting(): UiObject2? = device.findObject(By.text(greetingPattern))
        fun navNode(): UiObject2? = device.findObject(By.desc("Library"))
        fun pill(): UiObject2? =
            device.findObjects(By.text("Runtime local tone")).maxByOrNull { it.visibleBounds.top }

        // 1. Track loaded, on Home, dial on through the app's own settings state.
        MorphCancelTest.ensureTone(device)
        instrumentation.runOnMainSync { NavSettings.hideOnScroll = true }
        check(device.wait(Until.hasObject(By.text(greetingPattern)), 30_000)) {
            "SETUP FAILURE: home greeting never showed"
        }
        SystemClock.sleep(2_000)
        val navAtRest = navNode()
        val pillAtRest = pill()
        note("rested nav=" + (navAtRest != null) + " pill=" + (pillAtRest?.visibleBounds?.toShortString()))
        shot("navhide-1-rested.png")
        check(navAtRest != null) { "SETUP FAILURE: nav row not found at rest" }
        check(pillAtRest != null && pillAtRest.visibleBounds.top > device.displayHeight * 3 / 4) {
            "SETUP FAILURE: mini player pill not found in the dock at rest"
        }

        // 2. Swipe over vertical feed content, anchored on a feed section header.
        val anchor = device.wait(Until.findObject(By.text("Quick picks")), 30_000)
        check(anchor != null) { "SETUP FAILURE: no feed section to anchor the swipe on" }
        val cx = device.displayWidth / 2
        val sy = (anchor.visibleBounds.bottom + 260).coerceAtMost(device.displayHeight - 450)
        val gTopBefore = greeting()?.visibleBounds?.top
        note("swipe from y=$sy greeting-before=$gTopBefore")
        device.swipe(cx, sy, cx, sy - 650, 40)

        // 3. Prove the feed actually scrolled before any verdict.
        var scrolled = false
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (SystemClock.uptimeMillis() < deadline) {
            val g = greeting()
            if (g == null || (gTopBefore != null && g.visibleBounds.top < gTopBefore - 300)) {
                scrolled = true
                break
            }
            SystemClock.sleep(100)
        }
        note("scrolled=$scrolled greeting-after=" + greeting()?.visibleBounds?.top)
        check(scrolled) { "SETUP FAILURE: feed never scrolled; no nav-hide verdict" }

        // 4. Verdict: nav row leaves the tree, mini player stays in the dock.
        val navGone = device.wait(Until.gone(By.desc("Library")), 3_000)
        SystemClock.sleep(600)
        val pillAfter = pill()
        note("nav-gone=$navGone pill-after=" + (pillAfter?.visibleBounds?.toShortString()))
        shot("navhide-2-scrolled.png")
        instrumentation.runOnMainSync { NavSettings.hideOnScroll = false }
        check(pillAfter != null && pillAfter.visibleBounds.top > device.displayHeight / 2) {
            "PRODUCT: mini player vanished when the feed scrolled (hideOnScroll must tuck only the nav)"
        }
        check(navGone == true && navNode() == null) {
            "PRODUCT: nav row still in the tree after a proven scroll with hide_on_scroll on"
        }
        note("VERDICT: nav hidden on scroll, mini player stayed")
    }
}
