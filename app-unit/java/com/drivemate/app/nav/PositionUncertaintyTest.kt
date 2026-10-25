package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Position uncertain" after three seconds, counted from the last fix when fixes stop. */
class PositionUncertaintyTest {
    @Test fun gpsLostShowsThreeSecondsAfterTheLastFix() {
        val u = PositionUncertainty()
        val lastFix = 10_000L
        assertFalse(u.update(lastFix + 2_000, reliable = true))
        // The fix goes stale at 2.5 s; uncertainty counts from the fix itself.
        assertFalse(u.update(lastFix + 2_600, reliable = false, sinceMs = lastFix))
        assertFalse(u.update(lastFix + 2_900, reliable = false, sinceMs = lastFix))
        assertTrue(u.update(lastFix + 3_000, reliable = false, sinceMs = lastFix))
    }

    @Test fun poorAccuracyCountsFromWhenItStarted() {
        val u = PositionUncertainty()
        assertFalse(u.update(20_000, reliable = false))
        assertFalse(u.update(22_900, reliable = false))
        assertTrue(u.update(23_000, reliable = false))
    }

    @Test fun recoveryClearsAndRestartsTheCount() {
        val u = PositionUncertainty()
        assertTrue(u.update(3_000, reliable = false, sinceMs = 0))
        assertFalse(u.update(3_100, reliable = true))
        assertFalse(u.update(5_000, reliable = false, sinceMs = 4_000))
        assertTrue(u.update(7_000, reliable = false, sinceMs = 4_000))
    }

    @Test fun startTimeInTheFutureOrBeforeZeroIsClamped() {
        val u = PositionUncertainty()
        assertFalse(u.update(1_000, reliable = false, sinceMs = 9_000))   // treated as now
        assertTrue(u.update(4_000, reliable = false))
        val v = PositionUncertainty()
        assertTrue(v.update(3_000, reliable = false, sinceMs = -50_000))  // treated as 0
    }
}
