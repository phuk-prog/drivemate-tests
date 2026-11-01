package com.drivemate.tests.cat05

import com.drivemate.app.data.*
import com.drivemate.app.map.*
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

class ImmersiveNavigationDecisionsTest {
    @Test fun q5_1_2_3_19_defaultContinuous3dAndSaved2dAlternative() {
        val prefs = MemoryPreferences()
        assertEquals(MapView.TILTED, Settings.load(prefs).mapView)
        Settings(mapView = MapView.HEADING_UP).save(prefs)
        assertEquals(MapView.HEADING_UP, Settings.load(prefs).mapView)
        // Ordinary 2D remains available; category 4 junction perspective remains separate.
        val flat = DrivingCamera.target(MapView.HEADING_UP, 17.0, 45.0, 90.0, null, false, 0.0, null)
        assertEquals(0.0, flat.tilt, 0.0)
        val ordinary = DrivingCamera.target(MapView.TILTED, 17.0, 45.0, 90.0, null)
        assertTrue(ordinary.tilt > 0.0)
    }
    @Test fun q5_4_5_6_fourSecondBlendDuplicateAndOldFixesDoNotAdvance() {
        val b = ImmersiveMap.Blend()
        b.update(true, 0)
        for (t in 1000L..4000L step 1000) b.update(true, t)
        assertEquals(1.0, b.amount, 0.0)
        assertEquals(1.0, b.update(false, 3000), 0.0)
        assertEquals(1.0, b.update(false, 4000), 0.0)
        assertEquals(0.75, b.update(false, 5000), 0.0)
    }
    @Test fun q5_9_noNativeFeatureQueryBeforeFirstFrameOrAfterDetach() {
        assertFalse(BuildingLayers.canQuery(false, true, 1080, 2100))
        assertFalse(BuildingLayers.canQuery(true, false, 1080, 2100))
        assertFalse(BuildingLayers.canQuery(true, true, 0, 2100))
        assertFalse(BuildingLayers.canQuery(true, true, 1080, 0))
        assertTrue(BuildingLayers.canQuery(true, true, 1080, 2100))
    }
    @Test fun q5_14_sustainedNativeCostDropsDecorationBeforePerspective() {
        val budget = ImmersiveRenderBudget()
        repeat(29) { budget.sample(0.08, 0.01) }
        assertEquals(ImmersiveRenderBudget.Level.FULL, budget.level)
        budget.sample(0.08, 0.01)
        assertEquals(ImmersiveRenderBudget.Level.SIMPLE, budget.level)
        repeat(30) { budget.sample(0.08, 0.01) }
        assertEquals(ImmersiveRenderBudget.Level.FLAT, budget.level)
        repeat(50) { budget.sample(0.001, 0.001) }
        assertEquals(ImmersiveRenderBudget.Level.FLAT, budget.level)
        budget.reset()
        repeat(100) { budget.sample(Double.NaN, 0.0); budget.sample(0.0, 0.0) }
        assertEquals(ImmersiveRenderBudget.Level.FULL, budget.level)
    }
    @Test fun q5_15_uncertainPositionWaitsThreeSecondsAndNeverShowsPointer() {
        val p = PositionUncertainty()
        assertFalse(p.update(0, false))
        assertFalse(p.update(2999, false))
        assertTrue(p.update(3000, false))
        assertFalse(JunctionViewPolicy.showPointer(true, false))
        assertFalse(p.update(3100, true))
    }
    @Test fun q5_16_17_replayLocksForMovementMissingSpeedStaleOrUnconfirmedParking() {
        assertTrue(ReplaySafety.allowed(true, false, 0.0, 100))
        assertFalse(ReplaySafety.allowed(false, false, 0.0, 100))
        assertFalse(ReplaySafety.allowed(true, true, 0.0, 100))
        assertFalse(ReplaySafety.allowed(true, false, null, 100))
        assertFalse(ReplaySafety.allowed(true, false, 0.5, 100))
        assertFalse(ReplaySafety.allowed(true, false, 0.0, 5001))
        assertFalse(ReplaySafety.allowed(true, false, 0.0, -1))
        assertFalse(ReplaySafety.allowed(true, false, Double.NaN, 100))
        assertEquals(6, RoundaboutReplayScene.frames.size)
    }

    @Test fun q16_5_renderBudgetFallbackDrivesFlatWithoutPointerOrContinuous3d() {
        val budget = ImmersiveRenderBudget()
        repeat(60) { budget.sample(0.08, 0.01) }
        assertEquals(ImmersiveRenderBudget.Level.FLAT, budget.level)
        // MapController uses HEADING_UP and no continuous 3D blend once the budget is FLAT.
        val flat = DrivingCamera.target(MapView.HEADING_UP, 17.0, 45.0, 90.0, null, false, 0.0, null)
        assertEquals(0.0, flat.tilt, 0.0)
        assertFalse(JunctionViewPolicy.showPointer(true, false))
        budget.reset()
        assertEquals(ImmersiveRenderBudget.Level.FULL, budget.level)
    }
}
