package com.drivemate.app

import com.drivemate.app.data.RouteRequest
import com.drivemate.app.map.LocationComponentPermissionPolicy
import com.drivemate.app.nav.ArrivalPolicy
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

class NavigationSafetyTest {
    @Test fun revokedPermissionNeverEnablesLocation() {
        for (reliable in listOf(false, true))
            assertFalse(LocationComponentPermissionPolicy.enabled(false, reliable))
    }
    @Test fun unreliablePositionNeverEnablesLocation() {
        assertFalse(LocationComponentPermissionPolicy.enabled(true, false))
        assertTrue(LocationComponentPermissionPolicy.enabled(true, true))
    }
    @Test fun estimatedOrUnsafePositionNeverCompletesTrip() {
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, true))
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, false))
    }
    @Test fun passingDestinationBeforeTurnaroundDoesNotCompleteTrip() {
        assertFalse(ArrivalPolicy.arrived(500.0, 50.0, 5.0, false))
        assertTrue(ArrivalPolicy.arrived(10.0, 5.0, 5.0, false))
    }
    @Test fun nonFiniteArrivalEvidenceNeverCompletesTrip() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(ArrivalPolicy.arrived(bad, 0.0, 0.0, false))
            assertFalse(ArrivalPolicy.arrived(0.0, bad, 0.0, false))
            assertFalse(ArrivalPolicy.arrived(0.0, 0.0, bad, false))
        }
    }
    @Test fun malformedCoordinatesAreRejected() {
        for (p in listOf(LatLon(Double.NaN, 0.0), LatLon(0.0, Double.POSITIVE_INFINITY),
            LatLon(90.01, 0.0), LatLon(0.0, -180.01))) assertFalse(RouteRequest.valid(p))
        assertTrue(RouteRequest.valid(LatLon(53.48, -2.24)))
    }
    @Test fun invalidHeadingsAreDroppedAndFiniteHeadingsNormalized() {
        assertNull(RouteRequest.heading(Float.NaN))
        assertNull(RouteRequest.heading(Float.POSITIVE_INFINITY))
        assertNull(RouteRequest.heading(null))
        assertEquals(270f, RouteRequest.heading(-90f)!!, 0f)
        assertEquals(90f, RouteRequest.heading(450f)!!, 0f)
    }
    @Test fun invalidTripPointCountsAndSamePlaceAreRejected() {
        val a = LatLon(53.48, -2.24)
        val b = LatLon(53.50, -2.20)
        for (points in listOf(emptyList(), listOf(a), listOf(a, a), List(28) { if (it % 2 == 0) a else b })) {
            try { RouteRequest.check(points); fail("Invalid trip was accepted") }
            catch (_: IllegalArgumentException) {}
        }
        RouteRequest.check(listOf(a, b))
    }
    @Test fun routeBudgetsUseElapsedTimeAndDoNotBecomeNegative() {
        var now = 100L
        val budget = RouteRequest.Budget(firstMs = 1000, totalMs = 2000, clock = { now })
        now = 600L
        assertEquals(500L, budget.leftForFirst())
        assertEquals(1500L, budget.leftForExtras(0))
        now = 3100L
        assertEquals(0L, budget.leftForFirst())
        assertEquals(0L, budget.leftForExtras(0))
    }
}
