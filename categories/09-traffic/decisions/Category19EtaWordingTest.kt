package com.drivemate.tests.cat09

import com.drivemate.app.data.ClosureArrivalPolicy
import com.drivemate.app.data.Route
import com.drivemate.app.data.TrafficDetails
import com.drivemate.app.data.TrafficTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 19 (19.2, 19.3): ETA and delay wording never invents a delay, and delay never adds to the journey total. */
class Category19EtaWordingTest {
    private val now = 1_800_000_000_000L

    @Test fun q19_2_unknownTrafficIsSaidUnknownNotZero() {
        val expected = "Journey time 10 min · traffic unavailable, so any delay is unknown"
        assertEquals(expected, TrafficDetails.journeyLine(600.0, 120.0, false))
        assertEquals(expected, TrafficDetails.journeyLine(600.0, null, true))
        assertEquals(expected, TrafficDetails.journeyLine(600.0, -5.0, true))
        assertEquals(expected, TrafficDetails.journeyLine(600.0, Double.NaN, true))
    }

    @Test fun q19_3_delayIsPartOfTheTotalNotAddedToIt() {
        // The journey total is the source's traffic-inclusive time; the delay only explains part of it.
        assertEquals("Journey time 10 min, including about 2 min traffic delay (TomTom live estimate)",
            TrafficDetails.journeyLine(600.0, 90.0, true))
        assertTrue(TrafficDetails.journeyLine(600.0, 120.0, true).startsWith("Journey time 10 min"))
        assertEquals("Journey time 10 min · no traffic delay reported (TomTom live)", TrafficDetails.journeyLine(600.0, 0.0, true))
    }

    @Test fun q19_3_trafficIsCurrentOnlyWhenFreshAndInsideTheJourneyTotal() {
        fun route(duration: Double = 600.0, delay: Double = 60.0, at: Long = now) =
            Route(emptyList(), 1000.0, duration, emptyList(), "synthetic", live = true, trafficDelay = delay, trafficCheckedAt = at)
        assertTrue(route().trafficCurrent(now))
        assertFalse("delay larger than the total is impossible", route(delay = 700.0).trafficCurrent(now))
        assertFalse("negative delay is not a saving", route(delay = -1.0).trafficCurrent(now))
        assertFalse("untimed route", route().copy(live = false).trafficCurrent(now))
        assertFalse("stale route", route(at = now - TrafficTiming.FRESH_FOR - 1).trafficCurrent(now))
    }

    @Test fun q19_3_arrivalIsAnIntervalAlongTheRouteNotAClockClaim() {
        val route = Route(emptyList(), 1000.0, 600.0, emptyList(), "synthetic")
        assertEquals(now, ClosureArrivalPolicy.arrivalAt(route, 0.0, now))
        assertEquals(now + 300_000L, ClosureArrivalPolicy.arrivalAt(route, 500.0, now))
        assertEquals("beyond the route end is clamped", now + 600_000L, ClosureArrivalPolicy.arrivalAt(route, 5000.0, now))
    }
}
