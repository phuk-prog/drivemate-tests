package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C13.1/C13.2: "Missed the exit" only with clear evidence and a trustworthy position. */
class C13MissedExitRuleTest {
    // Motorway heading north (0°); car 200 m past the exit, still going north, 35 m off the slip-road route.
    private fun missed(speed: Double = 30.0, past: Double = 200.0, off: Double = 35.0, fromExit: Double = 2.0,
                       car: Double? = 4.0, safe: Boolean = true) =
        MissedExitRule.missed(speed, past, off, fromExit, 0.0, car, safe)

    @Test fun carriedOnAlongTheMotorwayPastTheExitIsAMissedExit() = assertTrue(missed())

    @Test fun anUnsafePositionNeverAnnouncesAMissedExit() = assertFalse(missed(safe = false))

    @Test fun slowOrNotYetFarEnoughOrTooFarIsNotJudged() {
        assertFalse(missed(speed = 10.0))
        assertFalse(missed(past = 40.0))
        assertFalse(missed(past = 800.0))
    }

    @Test fun stillOnTheSlipRouteOrTurnedOffIsNotAMissedExit() {
        assertFalse("close to the planned slip road", missed(off = 10.0))
        assertFalse("heading away from the motorway (took the slip)", missed(car = 40.0))
        assertFalse("not ahead of the exit along the motorway", missed(fromExit = 90.0))
        assertFalse("no heading", missed(car = null))
        assertTrue("bearing wraps around north", MissedExitRule.missed(30.0, 200.0, 35.0, 359.0, 0.0, 357.0, true))
    }

    @Test fun invalidNumbersNeverTrigger() {
        assertFalse(missed(speed = Double.NaN))
        assertFalse(missed(past = Double.NaN))
        assertFalse(missed(off = Double.NaN))
    }
}
