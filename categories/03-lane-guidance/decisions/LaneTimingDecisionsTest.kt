package com.drivemate.tests.cat03

import com.drivemate.app.nav.LaneDisplay
import com.drivemate.app.nav.LaneTiming
import org.junit.Assert.*
import org.junit.Test

/** Register 3.25–3.28: lane display timing when limits change, are unknown, or the route starts close. */
class LaneTimingDecisionsTest {
    private val mph = 1.609344
    private val ms = 0.44704 // metres per second per mph

    @Test fun q3_25_eachStretchAtItsOwnLimit() {
        // 70 mph approach dropping to 50 mph for the last 500 m before the decision at 5 km.
        val limitAt = { d: Double -> if (d >= 4_500) 50 * mph else 70 * mph }
        val onset = LaneTiming.onsetBefore(5_000.0, limitAt = limitAt)!!
        val expected = 500 + (45 - 500 / (50 * ms)) * 70 * ms // 45 s band from the 50 mph limit at the decision
        assertEquals(expected, onset, 1.0)
        assertNotEquals("not the junction limit for the whole approach", 45 * 50 * ms, onset, 50.0)
        assertNotEquals("not the limit where the car is now", 90 * 70 * ms, onset, 50.0)
        // One limit throughout gives the plain 3.24 band distance.
        assertEquals(90 * 70 * ms, LaneTiming.onsetBefore(10_000.0) { 70 * mph }!!, 1.0)
        assertEquals(20 * 20 * ms, LaneTiming.onsetBefore(10_000.0) { 20 * mph }!!, 1.0)
    }

    @Test fun q3_26_unknownLimitUsesNationalLimitForRoadType() {
        assertEquals(70 * mph, LaneTiming.nationalLimit(mapOf("highway" to "motorway"))!!, 0.01)
        assertEquals(70 * mph, LaneTiming.nationalLimit(mapOf("highway" to "motorway_link"))!!, 0.01)
        assertEquals(70 * mph, LaneTiming.nationalLimit(mapOf("highway" to "trunk", "oneway" to "yes", "lit" to "no"))!!, 0.01)
        assertEquals(60 * mph, LaneTiming.nationalLimit(mapOf("highway" to "primary", "lit" to "no"))!!, 0.01)
        // Lit restricted road: 30 mph (20 mph in Wales); both use the same 20-second band.
        val lit = LaneTiming.nationalLimit(mapOf("highway" to "primary", "oneway" to "yes", "lit" to "yes"))!!
        assertEquals(30 * mph, lit, 0.01)
        assertEquals(LaneTiming.seconds(20 * mph), LaneTiming.seconds(lit))
        // A roundabout is not a dual carriageway.
        assertEquals(60 * mph, LaneTiming.nationalLimit(mapOf("highway" to "primary", "oneway" to "yes", "junction" to "roundabout", "lit" to "no"))!!, 0.01)
    }

    @Test fun q3_27_routeStartingInsideTheWindowShowsStraightAway() {
        // Decision 300 m after the route start at 70 mph: the whole approach is inside the window.
        assertEquals(300.0, LaneTiming.onsetBefore(300.0) { 70 * mph }!!, 0.001)
        val state = LaneDisplay()
        assertTrue("first check on a new route, already inside", state.due("route-a", "j1", 250.0) { 300.0 })
        assertTrue("stays once shown", state.due("route-a", "j1", 240.0) { null })
        assertTrue("reroute inside the window shows at once", state.due("route-b", "j1", 100.0) { 300.0 })
        assertFalse("passed", state.due("route-b", "j1", -1.0) { 300.0 })
    }

    @Test fun q3_28_unknownLimitAndLightingHidesLanes() {
        assertNull(LaneTiming.nationalLimit(mapOf("highway" to "primary")))
        assertNull(LaneTiming.nationalLimit(mapOf("highway" to "trunk", "oneway" to "yes")))
        assertNull(LaneTiming.nationalLimit(emptyMap()))
        assertNull(LaneTiming.nationalLimit(null))
        // Limit unknown at the decision point: no lane display at all.
        assertNull(LaneTiming.onsetBefore(3_000.0) { null })
        // Unknown stretch 1–1.5 km back from the decision: lanes wait until the car has passed it.
        val withGap = { d: Double -> if (d in 1_000.0..2_000.0) null else 70 * mph }
        assertEquals(1_000.0, LaneTiming.onsetBefore(3_000.0, limitAt = withGap)!!, 0.001)
        val state = LaneDisplay()
        assertFalse(state.due("r", "j", 1_200.0) { LaneTiming.onsetBefore(3_000.0, limitAt = withGap) })
        assertTrue(state.due("r", "j", 990.0) { LaneTiming.onsetBefore(3_000.0, limitAt = withGap) })
    }
}
