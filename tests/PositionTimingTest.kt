package com.drivemate.app

import com.drivemate.app.nav.LocationTracker
import com.drivemate.app.nav.PositionUncertainty
import com.drivemate.app.nav.fixInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Position timing (category A, approved 6 November 2026): the "Position uncertain" warning waits 10 s, the
 * map follows a fix at most 6 s old, and browse mode asks for a fix every 50 ms. Needs app candidate 0c0c4db.
 */
class PositionTimingTest {
    @Test fun uncertainWarningWaitsTenSeconds() {
        val position = PositionUncertainty()
        assertFalse(position.update(0L, reliable = false))
        assertFalse(position.update(9_999L, reliable = false))
        assertTrue(position.update(10_000L, reliable = false))
    }

    @Test fun mapFollowsFixesAtMostSixSecondsOld() {
        assertEquals(6_000L, PositionUncertainty.MAX_FOLLOW_FIX_AGE_MS)
    }

    @Test fun browseModeAsksForAFixEvery50Ms() {
        assertEquals(50L, fixInterval(LocationTracker.Mode.BROWSE))
    }
}
