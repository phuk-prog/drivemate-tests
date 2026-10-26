package com.drivemate.tests.cat04

import com.drivemate.app.data.Lane
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

class JunctionViewDecisionsTest {
    private fun plan(type: String, lanes: List<Lane> = emptyList(), count: Int? = null, fast: Boolean = false) =
        JunctionPlan.build(type, "left", true, lanes, count, null, null, "", "A6", "Town", fastRoad = fast)

    @Test fun q4_1_onRampsAndEndOfRoadNeverShow() {
        val lanes = listOf(Lane(listOf("left"), true), Lane(listOf("straight"), false))
        assertNull(plan("on ramp", lanes, 2, true))
        assertNull(plan("end of road", lanes, 2))
    }
    @Test fun q4_1_17_knownTwoLaneRoadDoesNotNeedAChosenLane() {
        assertNotNull(plan("turn", count = 2))
        assertNotNull(plan("roundabout", count = 2))
        assertNotNull(plan("fork", count = 2))
        assertNull(plan("turn", count = 1))
        assertNull(plan("turn"))
        assertNull(plan("off ramp"))
    }
    @Test fun q4_7_8_18_distancesStaySeparateFromLaneTiming() {
        assertEquals(1000.0, JunctionPlan.range(true), 0.0)
        assertEquals(300.0, JunctionPlan.range(false, 32.0), 0.0)
        assertEquals(300.0, JunctionPlan.range(false, 80.0), 0.0)
    }
    @Test fun q4_9_10_startInsideAndRetainThroughTheJunction() {
        val first = ImmersiveJunction.next(ImmersiveJunction.Mode(), 2, 40.0, true, 300.0, false, false)
        assertTrue(first.active)
        assertTrue(ImmersiveJunction.next(first, 2, 0.0, false, 300.0, true, false).active)
        assertFalse(ImmersiveJunction.next(first, 3, 500.0, false, 300.0, false, false).active)
        assertFalse(ImmersiveJunction.next(first, 2, 0.0, true, 300.0, true, true).active)
    }
}
