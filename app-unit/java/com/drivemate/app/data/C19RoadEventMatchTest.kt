package com.drivemate.app.data

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Register 19.2 and 19.4: a closure is shown on the route only where its stretch is grounded on it. */
class C19RoadEventMatchTest {
    private val start = LatLon(53.0, -2.0)
    /** A route 1 km long, heading north. Along-route distance is the distance from [start]. */
    private val routeEnd = Geo.offset(start, 0.0, 1_000.0)
    private fun alongOf(p: LatLon, tolerance: Double): Double? {
        val proj = Geo.project(p, start, routeEnd)
        return if (proj.distance <= tolerance) proj.t * Geo.distance(start, routeEnd) else null
    }
    private fun event(line: List<LatLon>, kind: NationalHighways.Kind = NationalHighways.Kind.ROAD_CLOSED, bothWays: Boolean = false) =
        NationalHighways.Event("synthetic", kind, line, bothWays = bothWays)
    private fun at(metres: Double) = Geo.offset(start, 0.0, metres)

    @Test fun closureOnTheRouteIsMatchedForwardsAlongIt() {
        val at = RoadEventMatch.match(event(listOf(at(200.0), at(400.0))), ::alongOf)!!
        assertEquals(200.0, at.first, 1.0)
        assertEquals(400.0, at.second, 1.0)
    }

    @Test fun aSinglePointClosureIsNeverMatched() {
        // One point has no direction: it could be the other carriageway.
        assertNull(RoadEventMatch.match(event(listOf(at(300.0))), ::alongOf))
    }

    @Test fun aClosureOnAnotherRoadIsNotMatched() {
        val sideStart = Geo.offset(at(300.0), 90.0, 300.0)
        val side = event(listOf(sideStart, Geo.offset(sideStart, 0.0, 200.0)))
        assertNull(RoadEventMatch.match(side, ::alongOf))
    }

    @Test fun aStretchBackwardsAlongTheRouteIsNotMatched() {
        // Travelling the other way round: last point is before the first on our route.
        assertNull(RoadEventMatch.match(event(listOf(at(400.0), at(200.0))), ::alongOf))
    }

    @Test fun streetWorksOnTheRouteAffectBothWays() {
        val both = RoadEventMatch.match(event(listOf(at(300.0)), NationalHighways.Kind.ROADWORKS, bothWays = true), ::alongOf)!!
        assertEquals(300.0, both.first, 1.0)
    }
}
