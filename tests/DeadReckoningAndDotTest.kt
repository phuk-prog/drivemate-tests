package com.drivemate.app

import com.drivemate.app.nav.GpsGapProgressPolicy
import com.drivemate.app.nav.LocationStatusPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category A decisions (approved 6 November 2026): the position estimate runs for at most 60 s of GPS gap,
 * and the amber dot on the car marker shows only above 50 m accuracy. Needs app candidate 15b0344.
 */
class DeadReckoningAndDotTest {
    @Test fun positionEstimateRunsForAtMostSixtySeconds() {
        assertEquals(60_000L, GpsGapProgressPolicy.MAX_ESTIMATE_GAP_MS)
    }

    @Test fun amberDotAppearsOnlyAboveFiftyMetres() {
        assertFalse(LocationStatusPolicy.showsWeakAccuracyDot(10f))
        assertFalse(LocationStatusPolicy.showsWeakAccuracyDot(50f))
        assertTrue(LocationStatusPolicy.showsWeakAccuracyDot(50.5f))
    }
}
