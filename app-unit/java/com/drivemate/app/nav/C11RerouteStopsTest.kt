package com.drivemate.app.nav

import com.drivemate.app.data.RouteRequest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Category 11.4 pure check: a reroute keeps the trip's stops in their order and the driver's
 * avoidances, and later changes to the source lists do not alter the reroute.
 */
class C11RerouteStopsTest {
    @Test fun q11_4_rerouteKeepsStopsOrderAndAvoidances() {
        val stops = mutableListOf(LatLon(53.48, -2.24), LatLon(53.40, -2.15))
        val avoid = mutableSetOf("motorways", "tolls")
        val plan = RouteRequest.reroutePlan(stops, avoid)
        assertEquals(listOf(LatLon(53.48, -2.24), LatLon(53.40, -2.15)), plan.stops)
        assertEquals(setOf("motorways", "tolls"), plan.avoid)
        stops.clear(); avoid.clear()
        assertEquals("the reroute keeps its own copy", 2, plan.stops.size)
        assertEquals("the reroute keeps its own avoidances", 2, plan.avoid.size)
    }

    @Test fun q11_4_noStopsStillKeepsAvoidances() {
        val plan = RouteRequest.reroutePlan(emptyList(), setOf("ferries"))
        assertEquals(emptyList<LatLon>(), plan.stops)
        assertEquals(setOf("ferries"), plan.avoid)
    }
}
