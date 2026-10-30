package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M03 monotonic timestamp and provider recovery regressions. */
class LocationSelectionRecoveryTest {
    private fun fix(source: LocationSelectionPolicy.Source, elapsedMs: Long) =
        LocationSelectionPolicy.Fix(source, elapsedMs, 53.42, -2.26, 5.0, 0.0, 20.0)

    @Test fun futureFixCannotPoisonProviderOrderingOrBlockRecovery() {
        val policy = LocationSelectionPolicy()

        assertFalse(policy.accept(fix(LocationSelectionPolicy.Source.GOOGLE, 100_001), 100_000, true))
        assertTrue(policy.accept(fix(LocationSelectionPolicy.Source.GPS, 100_000), 100_000, true))
    }

    @Test fun staleAndOutOfOrderFixesCannotReplaceCurrentProviderEvidence() {
        val policy = LocationSelectionPolicy()
        assertTrue(policy.accept(fix(LocationSelectionPolicy.Source.GPS, 100_000), 100_000, true))
        assertFalse(policy.accept(fix(LocationSelectionPolicy.Source.GOOGLE, 91_999), 100_000, true))
        assertFalse(policy.accept(fix(LocationSelectionPolicy.Source.GPS, 99_999), 100_000, true))
        assertTrue(policy.accept(fix(LocationSelectionPolicy.Source.GPS, 101_000), 101_000, true))
    }
}
