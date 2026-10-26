package com.drivemate.tests.cat04

import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

class JunctionPolicyDecisionsTest {
    @Test fun q4_1_17_motorwaysNeedExitSplitOrVerifiedComplexity() {
        assertTrue(JunctionViewPolicy.eligible("off ramp", true, null))
        assertTrue(JunctionViewPolicy.eligible("fork", true, null))
        assertTrue(JunctionViewPolicy.eligible("turn", true, null, true))
        assertFalse(JunctionViewPolicy.eligible("turn", true, 3))
        assertFalse(JunctionViewPolicy.eligible("on ramp", true, 3, true))
        assertFalse(JunctionViewPolicy.eligible("end of road", false, 3))
        for (type in listOf("turn", "roundabout", "rotary", "fork", "off ramp")) {
            assertTrue(JunctionViewPolicy.eligible(type, false, 2))
            assertFalse(JunctionViewPolicy.eligible(type, false, null))
            assertFalse(JunctionViewPolicy.eligible(type, false, 1))
        }
    }
    @Test fun q4_2_7_8_9_18_settingAndExactEntryBoundaries() {
        assertTrue(com.drivemate.app.data.Settings().junctionView)
        assertFalse(JunctionViewPolicy.due(false, false, 1.0, false))
        assertFalse(JunctionViewPolicy.due(true, true, 1.0, false))
        assertTrue(JunctionViewPolicy.due(true, false, 300.0, false))
        assertFalse(JunctionViewPolicy.due(true, false, 300.01, false))
        assertTrue(JunctionViewPolicy.due(true, false, 1000.0, true))
        assertFalse(JunctionViewPolicy.due(true, false, 1000.01, true))
        assertTrue(JunctionViewPolicy.due(true, false, 0.0, true))
        for (d in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) assertFalse(JunctionViewPolicy.due(true, false, d, true))
    }
    @Test fun q4_10_chainedExitAndRoundaboutEndOnArrivalDisableOrDismissal() {
        val chain = setOf(2, 3, 4)
        assertTrue(JunctionViewPolicy.retainChain(true, false, false, 3, 500.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(false, false, false, 3, 500.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(true, true, false, 3, 500.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(true, false, true, 3, 500.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(true, false, false, 5, 500.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(true, false, false, 3, 900.0, chain, 900.0))
        assertFalse(JunctionViewPolicy.retainChain(true, false, false, 3, Double.NaN, chain, 900.0))
    }
    @Test fun q4_11_dismissalStaysForThisJunctionAndResetsOnReroute() {
        val state = JunctionDismissal()
        val route = Any(); val reroute = Any()
        assertFalse(state.hidden(route, 2))
        state.dismiss(route, 2)
        assertTrue(state.hidden(route, 2))
        assertTrue(state.hidden(route, 2)) // no timeout
        assertFalse(state.hidden(route, 3))
        assertFalse(state.hidden(reroute, 2))
    }
    @Test fun q4_3_4_measuredPointerMovesAgainstOneRaisedCamera() {
        val junction = LatLon(53.0, -2.0)
        val far = Geo.offset(junction, 180.0, 250.0)
        val near = Geo.offset(junction, 180.0, 20.0)
        val a = JunctionViewPolicy.cameraCentre(far, junction, 1.0)
        val b = JunctionViewPolicy.cameraCentre(near, junction, 1.0)
        assertTrue(Geo.distance(far, a) > Geo.distance(near, b))
        assertTrue(Geo.distance(far, a) <= 80.1)
        assertEquals(far, JunctionViewPolicy.cameraCentre(far, junction, 0.0))
    }
    @Test fun q4_12_unreliablePositionNeverShowsPointer() {
        assertFalse(JunctionViewPolicy.showPointer(true, false))
        assertTrue(JunctionViewPolicy.showPointer(true, true))
        assertTrue(JunctionViewPolicy.showPointer(false, false))
        val state = PositionUncertainty()
        assertFalse(state.update(0, false))
        assertFalse(state.update(2999, false))
        assertTrue(state.update(3000, false))
        assertFalse(state.update(3001, true))
    }
    @Test fun q4_13_14_labelsNeedCheckedDirectionsAndMatchedArms() {
        val marks = listOf(ImmersiveMap.ExitMark(LatLon(53.0, -2.0), 1, false), ImmersiveMap.ExitMark(LatLon(53.001, -2.0), 2, true))
        assertTrue(JunctionViewPolicy.exitLabels(marks, 151.0, false, true).isEmpty())
        assertEquals(marks, JunctionViewPolicy.exitLabels(marks, 150.0, false, true))
        assertEquals(marks, JunctionViewPolicy.exitLabels(marks, 0.0, true, true))
        assertTrue(JunctionViewPolicy.exitLabels(marks, 10.0, true, false).isEmpty())
        assertTrue(JunctionViewPolicy.exitLabels(marks.map { it.copy(number = null) }, 10.0, true, true).isEmpty())
        assertTrue(JunctionViewPolicy.exitLabels(marks, Double.NaN, false, true).isEmpty())
    }
}
