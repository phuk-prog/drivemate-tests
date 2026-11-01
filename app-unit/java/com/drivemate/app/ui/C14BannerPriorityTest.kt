package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C14.3: banner and warning priority; the essential turn is never removed by a secondary notice. */
class C14BannerPriorityTest {
    @Test fun gpsEstimateOutranksRerouteAndSigns() {
        assertEquals(DrivingNotice.GPS_ESTIMATE, drivingNotice(rerouting = true, sustainedGpsLoss = true, hasSign = true, imminentTurn = true, secondary = true))
        assertEquals(DrivingNotice.GPS_ESTIMATE, drivingNotice(rerouting = false, sustainedGpsLoss = true, hasSign = false, imminentTurn = false))
    }

    @Test fun rerouteOutranksSignsButNotGps() {
        assertEquals(DrivingNotice.REROUTING, drivingNotice(rerouting = true, sustainedGpsLoss = false, hasSign = true, imminentTurn = false))
        assertEquals(DrivingNotice.REROUTING, drivingNotice(rerouting = true, sustainedGpsLoss = false, hasSign = true, imminentTurn = true, secondary = true))
    }

    @Test fun onlyKnownSecondarySignsHideNearTheTurn() {
        assertEquals(DrivingNotice.NONE, drivingNotice(rerouting = false, sustainedGpsLoss = false, hasSign = true, imminentTurn = true, secondary = true))
        assertEquals(DrivingNotice.ROAD_SIGN, drivingNotice(rerouting = false, sustainedGpsLoss = false, hasSign = true, imminentTurn = true, secondary = false))
        assertEquals(DrivingNotice.ROAD_SIGN, drivingNotice(rerouting = false, sustainedGpsLoss = false, hasSign = true, imminentTurn = false))
    }

    @Test fun essentialTurnArrowIsNeverRemovedByANotice() {
        // Notices only ever choose among notice kinds; the turn banner is decided separately and is
        // shown at the approach distance with a fresh measured speed, whatever notice is active.
        assertTrue(turnBannerNow(arrived = false, stepType = "turn", distance = 6.0, speed = 5f, fixAgeMs = 1_000))
        assertTrue(imminentManeuver(arrived = false, distance = 20.0, onRoundabout = false, speed = 0f))
        assertFalse(turnBannerNow(arrived = true, stepType = "turn", distance = 6.0, speed = 5f, fixAgeMs = 1_000))
        assertFalse(turnBannerNow(arrived = false, stepType = "arrive", distance = 6.0, speed = 5f, fixAgeMs = 1_000))
    }
}
