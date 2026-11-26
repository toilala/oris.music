package com.orismusic.client.playback

import com.orismusic.client.utils.GuestAccessRefused
import com.orismusic.client.utils.diagnosticShort
import com.orismusic.client.utils.diagnosticSummary
import com.orismusic.client.utils.hasGuestRefusal
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class FailureFeedbackTest {
    @Test fun networkFailuresGetShortLabels() {
        assertEquals("No connection", diagnosticShort("No playable stream came back. NETWORK"))
        assertEquals("No connection", diagnosticShort("Cannot resolve abc: unable to resolve host"))
    }

    @Test fun refusalAndTimeoutHaveDistinctLabels() {
        assertEquals("Playback refused", diagnosticShort("YouTube refused the guest request (LOGIN_REQUIRED)"))
        assertEquals("Taking too long", diagnosticShort("No playable stream came back. TIMED OUT"))
        assertEquals(
            "Taking too long. Check your connection and try again.",
            diagnosticSummary("No playable stream came back. TIMED OUT"),
        )
    }

    @Test fun everyShortLabelFitsThePill() {
        listOf("network", "refused", "timed out", "no playable stream", "something else").forEach {
            assertTrue(diagnosticShort(it).length <= 20)
        }
    }

    @Test fun refusalIsFoundAnywhereInTheCauseChain() {
        assertTrue(hasGuestRefusal(GuestAccessRefused("x")))
        assertTrue(hasGuestRefusal(IOException("outer", IOException("mid", GuestAccessRefused("bot")))))
        assertFalse(hasGuestRefusal(IOException("just network")))
        assertFalse(hasGuestRefusal(null))
    }
}
