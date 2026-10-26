package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.data.LaneDb
import com.drivemate.app.nav.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Source-level checks use synthetic local map data, including offline behavior. */
class LaneSourceDecisionsTest {
    private val point = LatLon(0.0005, 0.0)
    private fun db(tags: Map<String, String>): LaneDb {
        val geometry = JSONArray().put(JSONArray(listOf(0.0, 0.0))).put(JSONArray(listOf(0.0, 0.001)))
        val way = JSONObject().put("id", "synthetic-northbound").put("t", JSONObject(tags)).put("g", geometry)
        val root = JSONObject().put("ways", JSONArray().put(way))
        return LaneDb.parse(listOf(root.toString()))
    }
    private fun local(extra: Map<String, String> = emptyMap()) = db(mapOf("oneway" to "yes", "turn:lanes" to "left|through") + extra)

    @Test fun q3_1_10_15_22_localMapPreservesTravelDirectionAndLaneOrder() {
        val source = db(mapOf("lanes" to "3", "lanes:forward" to "2", "lanes:backward" to "1",
            "turn:lanes:forward" to "left|through", "turn:lanes:backward" to "right"))
        assertEquals(2, source.laneCountAt(point, 0.0))
        assertEquals(1, source.laneCountAt(point, 180.0))
        val lanes = source.lanesAt(point, 0.0, "straight")!!
        assertEquals(listOf(listOf("left"), listOf("straight")), lanes.map { it.indications })
        assertEquals(listOf(1), LanePolicy.usable(lanes))
        assertNull(source.lanesAt(point, 180.0, "right"))
        // This is a loaded map object; no network path or current closure claim is needed.
        assertTrue(LanePolicy.reliable(lanes))
    }
    @Test fun q3_4_5_contradictoryCountAndPartialMarkingsSuppressAdvice() {
        assertNull(local(mapOf("lanes" to "3")).lanesAt(point, 0.0, "straight"))
        val partial = db(mapOf("oneway" to "yes", "turn:lanes" to "|through")).lanesAt(point, 0.0, "straight")!!
        assertFalse(LanePolicy.reliable(partial))
        val malformedCount = local(mapOf("lanes" to "two")).lanesAt(point, 0.0, "straight")
        assertTrue(malformedCount == null || !LanePolicy.reliable(malformedCount))
    }
    @Test fun q3_11_conditionalOrUnrecognizedPermissionsCannotBeAssumed() {
        for (extra in listOf(mapOf("motorcar:lanes:conditional" to "yes|no @ (Mo-Fr 07:00-10:00)"),
            mapOf("motorcar:lanes" to "yes|unrecognized"), mapOf("bus:lanes" to "designated"))) {
            val lanes = local(extra).lanesAt(point, 0.0, "straight")
            assertTrue("$extra must not produce reliable advice", lanes == null || !LanePolicy.reliable(lanes))
        }
    }
    @Test fun q3_11_knownBusAndHovLanesStayMarkedAndUnusable() {
        val bus = local(mapOf("bus:lanes" to "designated|no")).lanesAt(point, 0.0, "straight")!!
        assertTrue(bus[0].bus)
        assertEquals(listOf(1), LanePolicy.usable(bus))
        val hov = local(mapOf("hov:lanes" to "designated|no")).lanesAt(point, 0.0, "straight")!!
        assertEquals("HOV", hov[0].restriction)
        assertEquals(listOf(1), LanePolicy.usable(hov))
    }
    @Test fun q3_11_undirectedOrRoadWideRestrictionsCannotBeBypassed() {
        for (extra in listOf(mapOf("access" to "no"), mapOf("motor_vehicle" to "private"))) {
            val lanes = local(extra).lanesAt(point, 0.0, "straight")
            assertTrue(lanes == null || !LanePolicy.reliable(lanes))
        }
        val undirected = db(mapOf("turn:lanes:forward" to "left|through", "access:lanes" to "no|yes"))
            .lanesAt(point, 0.0, "straight")
        assertTrue(undirected == null || !LanePolicy.reliable(undirected))
    }
    @Test fun q3_9_roundaboutIncompleteOrConflictingApproachDoesNotInventLanes() {
        val partialRoute = listOf(Lane(listOf("left"), true), Lane(listOf("straight"), false))
        val fullLocal = listOf(Lane(listOf("left"), false), Lane(listOf("straight"), true), Lane(listOf("right"), false))
        assertNull(LanePlan.turnLanes(partialRoute, true, 3, null) { fullLocal })
        assertNull(LanePlan.turnLanes(partialRoute, true, 3, null) { null })
        val selected = LanePlan.turnLanes(emptyList(), true, 3, null) { fullLocal }!!
        assertEquals(3, selected.size)
        assertEquals(listOf(1), LanePolicy.usable(selected))
    }
    @Test fun q3_4_knownProviderAndLocalArrowConflictHidesAdvice() {
        val provider = listOf(Lane(listOf("left"), true), Lane(listOf("straight"), false))
        val local = listOf(Lane(listOf("right"), false), Lane(listOf("straight"), true))
        assertTrue(LanePolicy.contradicts(provider, local))
        assertNull(LanePlan.turnLanes(provider, false, 2, null) { local })
    }
    @Test fun q3_5_nonconflictingCompleteLocalLayoutReplacesPartialProvider() {
        val partial = listOf(Lane(listOf("unknown"), false), Lane(listOf("straight"), true))
        val local = listOf(Lane(listOf("left"), false), Lane(listOf("straight"), true))
        val chosen = LanePlan.turnLanes(partial, false, 2, null) { local }!!
        assertEquals(listOf(listOf("left"), listOf("straight")), chosen.map { it.indications })
        assertEquals(listOf(1), LanePolicy.usable(chosen))
    }
    @Test fun q3_11_providerRestrictionCannotBeBypassedByLocalFallback() {
        val partial = listOf(Lane(listOf("unknown"), false), Lane(listOf("straight"), true, bus = true))
        val local = listOf(Lane(listOf("left"), false), Lane(listOf("straight"), true))
        assertNull(LanePlan.turnLanes(partial, false, 2, null) { local })
    }

}
