package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class Immersive3dFailoverTest {
    private val F = Immersive3dFailover.Mode.TWO_D
    private val T = Immersive3dFailover.Mode.THREE_D

    @Test fun threeDWhileHeightDataAndRendererAllow() {
        val f = Immersive3dFailover()
        assertEquals(T, f.update(userWants3d = true, budgetFlat = false, heightFootprints = 4).mode)
        assertEquals(Immersive3dFailover.Reason.NONE, f.reason)
    }

    @Test fun noHeightDataFallsBackToTwoDAtOnce() {
        val f = Immersive3dFailover()
        val d = f.update(true, false, 0)
        assertEquals(F, d.mode)
        assertEquals(Immersive3dFailover.Reason.HEIGHT_DATA_ABSENT, d.reason)
    }

    @Test fun uncertainQueryFallsBackToTwoD() {
        val f = Immersive3dFailover()
        assertEquals(Immersive3dFailover.Reason.QUERY_UNCERTAIN, f.update(true, false, null).reason)
        assertEquals(F, f.mode)
    }

    @Test fun coverageReturnsOnlyAfterTenCleanSamples() {
        val f = Immersive3dFailover()
        f.update(true, false, 0)
        for (i in 1 until Immersive3dFailover.RECOVER_SAMPLES) assertEquals(F, f.update(true, false, 3).mode)
        assertEquals(T, f.update(true, false, 3).mode)
    }

    @Test fun aGapInCoverageRestartsTheCleanRun() {
        val f = Immersive3dFailover()
        f.update(true, false, 0)
        for (i in 1 until Immersive3dFailover.RECOVER_SAMPLES) f.update(true, false, 3)
        assertEquals(F, f.update(true, false, 0).mode)
        for (i in 1 until Immersive3dFailover.RECOVER_SAMPLES) assertEquals(F, f.update(true, false, 3).mode)
        assertEquals(T, f.update(true, false, 3).mode)
    }

    @Test fun renderBudgetFailureIsStickyForTheDrive() {
        val f = Immersive3dFailover()
        assertEquals(F, f.update(true, budgetFlat = true, heightFootprints = 5).mode)
        assertEquals(Immersive3dFailover.Reason.RENDER_BUDGET, f.reason)
        repeat(3 * Immersive3dFailover.RECOVER_SAMPLES) { assertEquals(F, f.update(true, false, 5).mode) }
        f.reset()
        assertEquals(T, f.update(true, false, 5).mode)
    }

    @Test fun userTwoDAppliesImmediatelyAndNeverInventsHeights() {
        val f = Immersive3dFailover()
        val d = f.update(userWants3d = false, budgetFlat = false, heightFootprints = 9)
        assertEquals(F, d.mode)
        assertEquals(Immersive3dFailover.Reason.USER_2D, d.reason)
        // Switching 3D back on by the user applies at once, with the same height data.
        assertEquals(T, f.update(true, false, 9).mode)
    }
}
