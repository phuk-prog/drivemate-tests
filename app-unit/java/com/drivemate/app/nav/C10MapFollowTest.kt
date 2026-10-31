package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C10.4: the 30 Oct A34/M60 report — voice guidance kept advancing while the map and car froze.
 * Navigation accepts fixes up to 50 m; the map used the lane-level gate (25 m, 2.5 s). The map now
 * follows navigation-grade fixes; lanes, junction view and "Now" keep the strict gate.
 */
class C10MapFollowTest {
    private fun follow(acc: Double?, age: Long?, authoritative: Boolean = true, measured: Boolean = true) =
        PositionUncertainty.followable(authoritative, measured, acc, age)
    private fun lanes(acc: Double?, age: Long?) = PositionUncertainty.reliable(true, true, acc, age)

    @Test fun aMotorwayFixGoodForGuidanceButNotLanesMovesTheMap() {
        for (acc in listOf(26.0, 30.0, 40.0, 50.0)) {
            assertTrue("map follows a $acc m fix that navigation accepts", follow(acc, 1_000))
            assertFalse("but no lane-level claim at $acc m", lanes(acc, 1_000))
        }
    }

    @Test fun aSlightlyLateFixStillMovesTheMapButNotLanes() {
        assertTrue(follow(10.0, 2_900))
        assertFalse(lanes(10.0, 2_900))
    }

    @Test fun everyLaneReliableFixIsAlsoFollowable() {
        for (acc in listOf(0.1, 5.0, 25.0)) for (age in listOf(0L, 1_000L, PositionUncertainty.MAX_LANE_FIX_AGE_MS))
            if (lanes(acc, age)) assertTrue("acc=$acc age=$age", follow(acc, age))
    }

    @Test fun staleRoughUntrustedOrUnmeasuredFixesNeverMoveTheMap() {
        assertFalse("older than the 3 s uncertainty rule", follow(10.0, PositionUncertainty.MAX_FOLLOW_FIX_AGE_MS + 1))
        assertFalse("rougher than navigation accepts", follow(50.1, 1_000))
        assertFalse(follow(10.0, 1_000, authoritative = false))
        assertFalse("network/estimate is not a measured fix", follow(10.0, 1_000, measured = false))
        assertFalse(follow(null, 1_000))
        assertFalse(follow(Double.NaN, 1_000))
        assertFalse(follow(0.0, 1_000))
        assertFalse(follow(10.0, null))
        assertFalse("a fix from the future", follow(10.0, -1))
    }
}
