package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 12.4: boundaries ahead (increases and decreases) in travel order, with a speed-based marker lead. */
class C12AheadBoundaryTest {
    // 48 km/h to 1,000 m, 64 km/h to 1,500 m, 32 km/h after.
    private val limitAt = { d: Double -> when { d < 1_000 -> 48.0; d < 1_500 -> 64.0; else -> 32.0 } }

    @Test fun boundariesAheadAreFoundInOrderWithDirection() {
        val b = LimitChangeCue.aheadBoundaries(900.0, 48.0, 800.0, limitAt)
        assertEquals(2, b.size)
        assertEquals(LimitChangeCue.Kind.RISE, b[0].kind)
        assertEquals(1_000.0, b[0].atM, LimitChangeCue.SCAN_STEP_M)
        assertEquals(LimitChangeCue.Kind.DROP, b[1].kind)
        assertEquals(1_500.0, b[1].atM, LimitChangeCue.SCAN_STEP_M)
        assertEquals(32.0, b[1].limitKmh, 0.0)
    }

    @Test fun unknownLimitsAreNeverBoundaries() {
        assertTrue(LimitChangeCue.aheadBoundaries(900.0, 48.0, 800.0) { null }.isEmpty())
        assertTrue(LimitChangeCue.aheadBoundaries(900.0, null, 800.0, limitAt).isEmpty())
    }

    @Test fun markerLeadAdaptsToSpeedWithAMinimum() {
        assertEquals(LimitChangeCue.DROP_MIN_M, LimitChangeCue.markerLeadM(5.0), 0.0)
        assertEquals(300.0, LimitChangeCue.markerLeadM(30.0), 0.0)
    }
}
