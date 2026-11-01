package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Category 7 uncertainty indication (owner-approved). Pure policy checks on [MatchUncertainty]:
 * no match is LOST, a stale or low-quality fix is UNCERTAIN, and only a fresh accurate match is
 * CONFIDENT. Synthetic values; no device, no real drive.
 */
class Cat7UncertaintyTest {
    private val match = LocalRoadMatcher.Match(
        roadId = "A", name = "A", position = LatLon(53.48, -2.23), heading = 90.0,
        segments = emptyList(), confidence = 1.0,
    )

    @Test fun noMatchIsLost() {
        assertEquals(MatchUncertainty.State.LOST, MatchUncertainty.classify(null, 0, 8.0))
    }

    @Test fun freshAccurateMatchIsConfident() {
        assertEquals(MatchUncertainty.State.CONFIDENT, MatchUncertainty.classify(match, 1_000, 8.0))
        assertEquals(MatchUncertainty.State.CONFIDENT,
            MatchUncertainty.classify(match, MatchUncertainty.MAX_CONFIDENT_FIX_AGE_MS, MatchUncertainty.MAX_CONFIDENT_ACCURACY_M))
    }

    @Test fun staleFixIsUncertainNotConfident() {
        assertEquals(MatchUncertainty.State.UNCERTAIN,
            MatchUncertainty.classify(match, MatchUncertainty.MAX_CONFIDENT_FIX_AGE_MS + 1, 8.0))
        assertEquals(MatchUncertainty.State.UNCERTAIN, MatchUncertainty.classify(match, -1, 8.0))
    }

    @Test fun lowAccuracyOrNonFiniteIsUncertain() {
        assertEquals(MatchUncertainty.State.UNCERTAIN,
            MatchUncertainty.classify(match, 1_000, MatchUncertainty.MAX_CONFIDENT_ACCURACY_M + 0.1))
        assertEquals(MatchUncertainty.State.UNCERTAIN, MatchUncertainty.classify(match, 1_000, Double.NaN))
    }
}
