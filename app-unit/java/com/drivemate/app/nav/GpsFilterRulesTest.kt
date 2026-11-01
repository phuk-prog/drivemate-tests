package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks of the GpsFilter rules and StrayRun reacquisition. These call the companion
 * functions and StrayRun directly, so no android.location.Location is needed. The Android
 * GpsFilterScreenTest covers the full Location path on a device.
 */
class GpsFilterRulesTest {
    private val here = LatLon(53.42, -2.26)
    // About 665 m east of [here]: far beyond any believable movement in one second.
    private val farAway = LatLon(53.42, -2.25)

    @Test fun poorWifiFixRightAfterGoodGpsIsRejected() {
        assertTrue(GpsFilter.rejects(dt = 5.0, d = 10.0, accuracy = 80.0, previousAccuracy = 5.0))
    }

    @Test fun ordinaryGoodFixIsKept() {
        assertFalse(GpsFilter.rejects(dt = 1.0, d = 3.0, accuracy = 5.0, previousAccuracy = 5.0))
    }

    @Test fun impossibleJumpIsRejected() {
        assertTrue(GpsFilter.rejects(dt = 2.0, d = 500.0, accuracy = 5.0, previousAccuracy = 5.0))
    }

    @Test fun fastMovementWithinErrorIsNotAJump() {
        // 50 m/s is under the 70 m/s limit, so the jump rule does not apply.
        assertFalse(GpsFilter.rejects(dt = 10.0, d = 500.0, accuracy = 5.0, previousAccuracy = 5.0))
        // 71 m/s but the move is inside the two fixes' combined error, so it is kept.
        assertFalse(GpsFilter.rejects(dt = 1.0, d = 71.0, accuracy = 50.0, previousAccuracy = 40.0))
    }

    @Test fun jumpRuleOnlyAppliesWithinThirtySeconds() {
        assertFalse(GpsFilter.rejects(dt = 30.0, d = 3000.0, accuracy = 5.0, previousAccuracy = 5.0))
    }

    @Test fun slowFixWithinTwelveMetresOrTwiceAccuracyStaysStill() {
        assertTrue(GpsFilter.staysStill(speed = 0.5f, d = 10.0, accuracy = 5.0))
        assertFalse(GpsFilter.staysStill(speed = 0.5f, d = 12.0, accuracy = 5.0))
        assertTrue(GpsFilter.staysStill(speed = 0f, d = 25.0, accuracy = 20.0))
        assertFalse(GpsFilter.staysStill(speed = 0f, d = 45.0, accuracy = 20.0))
    }

    @Test fun pullingAwayIsNeverHeldStill() {
        assertFalse(GpsFilter.staysStill(speed = 0.9f, d = 1.0, accuracy = 5.0))
    }

    @Test fun missingSpeedIsWorkedOutFromMovement() {
        val speed = GpsFilter.derivedSpeed(reported = null, hasSpeedAccuracy = false, dt = 2.0, d = 20.0, accuracy = 5.0)
        assertEquals(10f, speed!!, 0.001f)
    }

    @Test fun zeroSpeedWithoutSpeedAccuracyWhileMovingIsReplaced() {
        val speed = GpsFilter.derivedSpeed(reported = 0f, hasSpeedAccuracy = false, dt = 2.0, d = 20.0, accuracy = 5.0)
        assertEquals(10f, speed!!, 0.001f)
    }

    @Test fun reportedSpeedIsKeptWhenNotSuspicious() {
        assertNull(GpsFilter.derivedSpeed(reported = 0f, hasSpeedAccuracy = true, dt = 2.0, d = 20.0, accuracy = 5.0))
        assertNull(GpsFilter.derivedSpeed(reported = 0f, hasSpeedAccuracy = false, dt = 2.0, d = 8.0, accuracy = 5.0))
        assertNull(GpsFilter.derivedSpeed(reported = 12f, hasSpeedAccuracy = false, dt = 1.0, d = 12.0, accuracy = 5.0))
        assertNull(GpsFilter.derivedSpeed(reported = 0f, hasSpeedAccuracy = false, dt = 0.0, d = 20.0, accuracy = 5.0))
    }

    @Test fun strayFixAloneNeverReacquiresPosition() {
        val run = StrayRun()
        assertFalse(run.rejected(0.0, here, 5.0, lowQuality = false))
        assertFalse(run.rejected(1.0, farAway, 5.0, lowQuality = false))
    }

    @Test fun threeAgreeingFixesWithinSixSecondsReacquirePosition() {
        val run = StrayRun()
        val results = listOf(
            run.rejected(0.0, here, 5.0, lowQuality = false),
            run.rejected(1.0, farAway, 5.0, lowQuality = false),
            run.rejected(2.0, farAway, 5.0, lowQuality = false),
            run.rejected(3.0, farAway, 5.0, lowQuality = false),
        )
        assertEquals(listOf(false, false, false, true), results)
    }

    @Test fun poorAccuracyFixesNeverCountTowardReacquisition() {
        val run = StrayRun()
        assertFalse(run.rejected(0.0, farAway, 5.0, lowQuality = false))
        assertFalse(run.rejected(1.0, farAway, 80.0, lowQuality = true))
        assertFalse(run.rejected(2.0, farAway, 5.0, lowQuality = false))
        assertFalse(run.rejected(3.0, farAway, 5.0, lowQuality = false))
    }

    @Test fun agreementSpreadOverMoreThanSixSecondsDoesNotReacquire() {
        val run = StrayRun()
        val results = listOf(
            run.rejected(0.0, here, 5.0, lowQuality = false),
            run.rejected(1.0, farAway, 5.0, lowQuality = false),
            run.rejected(5.0, farAway, 5.0, lowQuality = false),
            run.rejected(9.0, farAway, 5.0, lowQuality = false),
            run.rejected(10.0, farAway, 5.0, lowQuality = false),
        )
        // The window holds the last three fixes: 1 s to 9 s is too long, 5 s to 10 s is accepted.
        assertEquals(listOf(false, false, false, false, true), results)
    }
}
