package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 8.2 and 10.4 pure timing checks: the spoken "Now" call (VoiceTiming.now) and when a
 * manoeuvre may be announced as "Then" (ThenStepPolicy). No Android or TextToSpeech is involved.
 */
class C08ThenNowTimingTest {
    /** 8.2: the "Now" call comes about 5 s before the turn, never under 8 m, and Early speaks it 20% later. */
    @Test fun q8_2_nowCallTimingFollowsSpeedAndSetting() {
        assertEquals(8.0, VoiceTiming.now(0.0, 1), 0.0)
        assertEquals(50.0, VoiceTiming.now(10.0, 1), 0.0)
        assertEquals(60.0, VoiceTiming.now(10.0, 2), 0.0)
        assertEquals("Late and Normal use the same Now call", VoiceTiming.now(10.0, 0), VoiceTiming.now(10.0, 1), 0.0)
    }

    /** 8.2: the "Then" window is about 30 s at speed, clamped to 150 m to 900 m. */
    @Test fun q8_2_thenWindowIsSpeedDependentAndClamped() {
        assertEquals(150.0, ThenStepPolicy.window(0.0), 0.0)
        assertEquals(750.0, ThenStepPolicy.window(25.0), 0.0)
        assertEquals(900.0, ThenStepPolicy.window(100.0), 0.0)
    }

    /** 10.4: "Then" is never the same physical manoeuvre again (3 m or less), never the arrival, and never beyond the window. */
    @Test fun q10_4_thenNeverRepeatsSameManoeuvreOrArrival() {
        assertFalse("same manoeuvre at 3 m", ThenStepPolicy.accepts(3.0, 20.0, nextIsArrival = false))
        assertTrue("a distinct close turn", ThenStepPolicy.accepts(4.0, 20.0, nextIsArrival = false))
        assertFalse("the arrival is never a Then", ThenStepPolicy.accepts(200.0, 20.0, nextIsArrival = true))
        assertFalse("beyond the window at 10 m/s", ThenStepPolicy.accepts(400.0, 10.0, nextIsArrival = false))
        assertTrue("inside the window at 25 m/s", ThenStepPolicy.accepts(700.0, 25.0, nextIsArrival = false))
    }
}
