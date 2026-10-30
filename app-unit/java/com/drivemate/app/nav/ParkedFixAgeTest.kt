package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A parked phone asks for a fix every 15 s, so a fix that recent is not "uncertain". */
class ParkedFixAgeTest {
    private val parked = PositionUncertainty.PARKED_FIX_AGE_MS

    @Test fun parkedLimitCoversTheParkedFixInterval() {
        assertEquals(15_000L, LocationTracker.STATIONARY_FIX_MS)
        assertTrue(parked > LocationTracker.STATIONARY_FIX_MS)
        assertTrue(parked <= 30_000L)
    }

    @Test fun twelveSecondOldAccurateFixIsReliableOnlyWhenParked() {
        assertFalse(PositionUncertainty.reliable(true, true, 4.0, 12_000))
        assertTrue(PositionUncertainty.reliable(true, true, 4.0, 12_000, parked))
    }

    @Test fun parkedStillRejectsStaleInaccurateOrEstimatedFixes() {
        assertFalse(PositionUncertainty.reliable(true, true, 4.0, parked + 1, parked))
        assertFalse(PositionUncertainty.reliable(true, true, 35.0, 1_000, parked))
        assertFalse(PositionUncertainty.reliable(true, false, 4.0, 1_000, parked))
        assertFalse(PositionUncertainty.reliable(false, true, 4.0, 1_000, parked))
    }

    @Test fun movingKeepsTheLaneLevelLimit() {
        assertTrue(PositionUncertainty.reliable(true, true, 4.0, 2_500))
        assertFalse(PositionUncertainty.reliable(true, true, 4.0, 2_501))
    }

    private fun fix(at: Long) = LocationSelectionPolicy.Fix(LocationSelectionPolicy.Source.GPS, at, 53.42, -2.26, 4.0, 0.0, 26.0)

    @Test fun selectionKeepsAParkedFixAuthoritativeBetweenFifteenSecondFixes() {
        val moving = LocationSelectionPolicy()
        assertTrue(moving.accept(fix(100_000), 100_000, navigation = false))
        assertFalse("8 s window unchanged while moving", moving.authoritative(110_000))

        val parkedPolicy = LocationSelectionPolicy().apply { maxAgeMs = LocationTracker.STATIONARY_FIX_MS + 5_000 }
        assertTrue(parkedPolicy.accept(fix(100_000), 100_000, navigation = false))
        assertTrue(parkedPolicy.authoritative(114_900))
        assertTrue(parkedPolicy.accept(fix(115_000), 115_200, navigation = false))
        assertTrue(parkedPolicy.authoritative(129_000))
        assertFalse("still expires when fixes stop", parkedPolicy.authoritative(140_001))
    }

    @Test fun parkedSelectionAcceptsAFixDeliveredTenSecondsLate() {
        val p = LocationSelectionPolicy()
        assertFalse(p.accept(fix(100_000), 110_000, navigation = false))
        val parkedPolicy = LocationSelectionPolicy().apply { maxAgeMs = LocationTracker.STATIONARY_FIX_MS + 5_000 }
        assertTrue(parkedPolicy.accept(fix(100_000), 110_000, navigation = false))
    }

    @Test fun navigationAsksForMaximumFreshLocationRateWithoutChangingParkedTracking() {
        assertEquals("active navigation asks as fast as the device can provide", 0L,
            fixInterval(LocationTracker.Mode.NAVIGATION))
        assertEquals("20 Hz callback safety cap", 50L,
            fastestFixInterval(LocationTracker.Mode.NAVIGATION))
        assertEquals("browsing frequency unchanged", 500L,
            fixInterval(LocationTracker.Mode.BROWSE))
        assertEquals(250L, fastestFixInterval(LocationTracker.Mode.BROWSE))
        assertEquals(15_000L, fixInterval(LocationTracker.Mode.STATIONARY))
        assertEquals(7_500L, fastestFixInterval(LocationTracker.Mode.STATIONARY))
    }

    @Test fun navigationFallbackProbesWithinThreeSecondsOfStalledFusedFeed() {
        assertEquals(3_000L, LocationTracker.NAVIGATION_FALLBACK_MS)
    }
}
