package com.drivemate.app.nav

import com.drivemate.app.nav.RoadNetworkMatch.OffRouteAdvice
import com.drivemate.app.nav.RoadNetworkMatch.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 11.2 pure checks: when the matched road is on the route, distance-only and heading-only
 * evidence is rejected as a false wrong turn; when it is off the route, a turn is accepted; when the
 * match is unknown, nothing is invented. Far from the route a real off-route is never hidden.
 */
class C11RerouteAdvisoryTest {
    /** 11.2: a matched road on the route vetoes a distance-only "clearly off" at a parallel-road distance. */
    @Test fun q11_2_onRouteMatchRejectsParallelRoadDistance() {
        val a = RoadNetworkMatch.advise(Verdict.ON_ROUTE, clearlyOff = true, turnedOff = false,
            mapTurnedOff = false, offRoute = 70.0, accuracy = 5.0, speed = 12.0)
        assertEquals(OffRouteAdvice(clearlyOff = false, turnedOff = false), a)
    }

    /** 11.2: a heading-only or gyroscope "turned off" is rejected when the matched road is on the route. */
    @Test fun q11_2_onRouteMatchRejectsUnmappedTurn() {
        val a = RoadNetworkMatch.advise(Verdict.ON_ROUTE, clearlyOff = false, turnedOff = true,
            mapTurnedOff = false, offRoute = 40.0, accuracy = 5.0, speed = 12.0)
        assertFalse("no reroute from an unmapped turn while the match is on the route", a.turnedOff)
    }

    /** 11.2: a turn the map itself saw is never vetoed, even when the match says on route. */
    @Test fun q11_2_mapSeenTurnIsNeverVetoed() {
        val a = RoadNetworkMatch.advise(Verdict.ON_ROUTE, clearlyOff = false, turnedOff = true,
            mapTurnedOff = true, offRoute = 40.0, accuracy = 5.0, speed = 12.0)
        assertTrue(a.turnedOff)
    }

    /** 11.2: very far from the route, a real off-route is not hidden by a matcher that still says on route. */
    @Test fun q11_2_farOffRouteIsNeverVetoed() {
        val a = RoadNetworkMatch.advise(Verdict.ON_ROUTE, clearlyOff = true, turnedOff = false,
            mapTurnedOff = false, offRoute = 400.0, accuracy = 5.0, speed = 12.0)
        assertTrue(a.clearlyOff)
    }

    /** 11.2: an unknown match leaves the evidence exactly as measured; it never adds a turn. */
    @Test fun q11_2_unknownMatchInventsNoEvidence() {
        assertEquals(OffRouteAdvice(false, false),
            RoadNetworkMatch.advise(Verdict.UNKNOWN, clearlyOff = false, turnedOff = false,
                mapTurnedOff = false, offRoute = 60.0, accuracy = 5.0, speed = 12.0))
        assertEquals(OffRouteAdvice(true, true),
            RoadNetworkMatch.advise(Verdict.UNKNOWN, clearlyOff = true, turnedOff = true,
                mapTurnedOff = false, offRoute = 60.0, accuracy = 5.0, speed = 12.0))
    }

    /** 11.2: a matched road off the route adds a turned-off only while moving and clearly away from it. */
    @Test fun q11_2_offRouteMatchNeedsMovementAndDistance() {
        assertTrue(RoadNetworkMatch.advise(Verdict.OFF_ROUTE, false, false, false, 30.0, 5.0, 10.0).turnedOff)
        assertFalse("a stopped car is not turned off", RoadNetworkMatch.advise(Verdict.OFF_ROUTE, false, false, false, 30.0, 5.0, 1.0).turnedOff)
    }

    /** 11.2: a stale match does not count: only a result for the newest fix is used. */
    @Test fun q11_2_staleMatchIsUnknown() {
        val r = RoadNetworkMatch.Result(Verdict.OFF_ROUTE, 1L, fixElapsedMs = 1_000L)
        assertEquals(Verdict.UNKNOWN, RoadNetworkMatch.current(r, newestFixMs = 1_000L + RoadNetworkMatch.FRESH_MS + 1))
    }
}
