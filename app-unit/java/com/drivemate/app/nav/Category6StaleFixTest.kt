package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 6.2 and 6.3: a frozen map must be the safe stale-fix hold, never a stale position
 * shown as current. Covers the position going stale once fixes stop, resume clearing the last
 * fix, and voice progress moving while the map stays put.
 */
class Category6StaleFixTest {
    private fun fix(atMs: Long, accuracyM: Double = 5.0) = LocationSelectionPolicy.Fix(
        LocationSelectionPolicy.Source.GPS, atMs, 53.42, -2.26, accuracyM,
    )

    @Test fun positionIsAuthoritativeWhileFreshAndStopsOnceFixesStop() {
        val policy = LocationSelectionPolicy()
        assertTrue("the first good fix is accepted", policy.accept(fix(1_000L), 1_000L, navigation = true))
        assertTrue(policy.authoritative(1_000L))
        assertFalse(
            "no new fixes for longer than the freshness window: the position is not current",
            policy.authoritative(1_000L + LocationSelectionPolicy.MAX_AGE_MS + 1),
        )
    }

    @Test fun uncertainOrStalePositionNeverMovesTheMap() {
        // Authoritative but stale (age beyond the follow window): the map must hold.
        assertFalse(PositionUncertainty.followable(
            authoritative = true, measured = true, accuracyM = 5.0,
            ageMs = PositionUncertainty.MAX_FOLLOW_FIX_AGE_MS + 1_000L,
        ))
        // Not authoritative (for example after the fixes stopped): the map must hold.
        assertFalse(PositionUncertainty.followable(
            authoritative = false, measured = true, accuracyM = 5.0, ageMs = 100L,
        ))
    }

    @Test fun resumeClearsTheLastFixSoAnOldPositionIsNotReused() {
        val policy = LocationSelectionPolicy()
        assertTrue(policy.accept(fix(1_000L), 1_000L, navigation = true))
        policy.reset()
        assertNull("resume must not reuse the last fix", policy.lastDelivered)
        assertFalse(policy.authoritative(1_000L))
    }

    @Test fun voiceProgressMovesWhileTheMapStaysPut() {
        // Fixes have stopped: the map flag is off, but the guidance progress estimate may still
        // advance within its own limit (this is the split the register asks for in 6.3).
        val mapMayMove = PositionUncertainty.followable(
            authoritative = false, measured = true, accuracyM = 5.0, ageMs = 4_000L,
        )
        assertFalse(mapMayMove)
        val held = 100.0
        val advanced = GpsGapProgressPolicy.advance(
            estimatedAlong = held, limit = 500.0, gapMs = 2_000L, elapsedSeconds = 2.0,
            freshCarSpeedMps = null, lastGpsSpeedMps = 10.0,
        )
        assertTrue("progress advances with no fix", advanced > held)
        assertTrue("progress stays within its limit", advanced <= 500.0)
        assertEquals(held, GpsGapProgressPolicy.advance(
            estimatedAlong = held, limit = 500.0, gapMs = 999_999L, elapsedSeconds = 2.0,
            freshCarSpeedMps = null, lastGpsSpeedMps = 10.0,
        ), 0.0001)
    }
}
