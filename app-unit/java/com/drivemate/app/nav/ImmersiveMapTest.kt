package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImmersiveMapTest {
    private val o = LatLon(53.4, -2.15)
    private fun at(eastM: Double, northM: Double) = Geo.offset(Geo.offset(o, 90.0, eastM), 0.0, northM)

    @Test fun blendTakesFourSecondsAndTurnsRoundWithoutAJump() {
        val b = ImmersiveMap.Blend()
        for (t in 0L..2_000L step 1_000L) b.update(true, t)
        assertEquals(0.5, b.amount, 1e-9)
        b.update(true, 3_000); b.update(true, 4_000)
        assertEquals(1.0, b.amount, 1e-9)
        assertEquals(0.75, b.update(false, 5_000), 1e-9)
        // A 10 s gap moves it at most 1.5 s worth.
        assertEquals(0.375, b.update(false, 15_000), 1e-9)
    }

    @Test fun switchOnAt1kmAnd300m() {
        assertEquals(1_000.0, JunctionPlan.range(true, 112.0), 0.0)
        assertEquals(300.0, JunctionPlan.range(false, 32.0), 0.0)
        assertEquals(300.0, JunctionPlan.range(false, 64.0), 0.0)
    }

    @Test fun countdownBoardsOnTheLeftVergeOnlyAhead() {
        val pointAt = { d: Double -> at(0.0, d) } // driving north
        val boards = ImmersiveMap.countdownBoards(1_000.0, 800.0, ImmersiveMap.Kind.MOTORWAY_EXIT, pointAt)
        assertEquals(listOf(2, 1), boards.map { it.bars })
        boards.forEach { assertTrue("left of the road", it.at.lon < o.lon); assertTrue(it.motorway) }
        assertTrue(ImmersiveMap.countdownBoards(1_000.0, 0.0, ImmersiveMap.Kind.TURN, pointAt).isEmpty())
        assertTrue(ImmersiveMap.countdownBoards(1_000.0, 0.0, ImmersiveMap.Kind.PRIMARY_EXIT, pointAt).none { it.motorway })
    }

    @Test fun junctionRoadsKeepConnectedRoadsAndDropABridge() {
        val route = listOf(at(0.0, -200.0), at(0.0, 0.0), at(80.0, 60.0))
        val sideRoad = listOf(at(0.0, 0.0), at(-120.0, 0.0)) // joins the route
        val bridge = listOf(at(-100.0, -100.0), at(100.0, -100.0)) // crosses over, never joins
        val farRoad = listOf(at(500.0, 500.0), at(600.0, 500.0))
        val kept = ImmersiveMap.junctionRoads(route, listOf(route to "route", sideRoad to "side", bridge to "bridge", farRoad to "far"))
        assertEquals(setOf("route", "side"), kept.map { it.second }.toSet())
    }

    @Test fun exitsNumberedOnlyWhenTheArmsMatchTheRoute() {
        // A ring driven clockwise from the south (UK): west is exit 1, north exit 2, east (yours) exit 3.
        val ring = (0..270 step 10).map { a -> val r = Math.toRadians(a.toDouble()); at(-40 * Math.sin(r), -40 * Math.cos(r)) }
        val west = listOf(at(-40.0, 0.0), at(-140.0, 0.0))
        val north = listOf(at(0.0, 40.0), at(0.0, 140.0))
        val marks = ImmersiveMap.exitMarks(ring, listOf(north, west), 3)
        assertEquals(listOf(1, 2, 3), marks.map { it.number })
        assertTrue(marks[0].at.lon < o.lon) // exit 1 is the west arm
        assertEquals(listOf(false, false, true), marks.map { it.yours })
        val unmatched = ImmersiveMap.exitMarks(ring, listOf(west), 3)
        assertEquals(1, unmatched.size)
        assertEquals(null, unmatched.single().number)
    }
}
