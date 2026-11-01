package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C13.3: adaptive ~5 s reroute countdown, deferred while the position is unsafe. */
class C13RerouteCountdownTest {
    @Test fun strongEvidenceWaitsThreeSecondsOrdinaryFive() {
        assertEquals(3.0, RerouteCountdown.seconds(wrongTurn = true, turnedOff = false, nearEnd = false, speedMps = 12.0), 0.0)
        assertEquals(3.0, RerouteCountdown.seconds(false, false, nearEnd = true, speedMps = 8.0), 0.0)
        assertEquals(4.0, RerouteCountdown.seconds(false, turnedOff = true, nearEnd = false, speedMps = 8.0), 0.0)
        assertEquals(5.0, RerouteCountdown.seconds(false, false, false, 13.0), 0.0)
    }

    @Test fun motorwaySpeedAddsOneSecondAndStaysWithinThreeToSeven() {
        assertEquals(6.0, RerouteCountdown.seconds(false, false, false, 31.0), 0.0)
        assertEquals(4.0, RerouteCountdown.seconds(true, false, false, 31.0), 0.0)
        for (w in listOf(true, false)) for (t in listOf(true, false)) for (n in listOf(true, false))
            for (v in listOf(null, Double.NaN, 0.0, 40.0)) {
                val s = RerouteCountdown.seconds(w, t, n, v)
                assertTrue(s in RerouteCountdown.MIN_S..RerouteCountdown.MAX_S)
            }
    }

    @Test fun firesOnlyOnceTheCountdownHasRunWithASafePosition() {
        assertFalse(RerouteCountdown.ready(4.9, 5.0, positionSafe = true))
        assertTrue(RerouteCountdown.ready(5.0, 5.0, positionSafe = true))
        assertFalse("deferred while the position is unsafe", RerouteCountdown.ready(30.0, 5.0, positionSafe = false))
        assertFalse(RerouteCountdown.ready(Double.NaN, 5.0, true))
    }

    @Test fun countdownDoesNotEndInsideAJunction() {
        assertFalse("roundabout: reroute waits until it is left", RerouteCountdown.ready(30.0, 5.0, positionSafe = true, junctionSafe = RerouteCountdown.junctionClear(200.0, onRoundabout = true)))
        assertFalse("within 40 m of the next turn", RerouteCountdown.ready(30.0, 5.0, true, RerouteCountdown.junctionClear(39.0, false)))
        assertFalse("unknown distance to the next turn", RerouteCountdown.ready(30.0, 5.0, true, RerouteCountdown.junctionClear(Double.NaN, false)))
        assertTrue("clear of junctions and off the route long enough", RerouteCountdown.ready(5.0, 5.0, true, RerouteCountdown.junctionClear(40.0, false)))
    }
}
