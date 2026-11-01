package com.drivemate.app.nav

import com.drivemate.app.data.Route
import com.drivemate.app.data.TrafficTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C19.5: rapidly changing traffic responses never make the route oscillate. */
class C19RouteStabilityTest {
    @Test fun marginalSavingsAreNotOfferedHoweverTrafficFlips() {
        for (i in 0 until 50) {
            val saves = if (i % 2 == 0) 100.0 else 110.0
            assertFalse(RouteSwitchPolicy.shouldOffer(1000.0, saves, 0.2, emptyList(), false))
        }
        assertTrue(RouteSwitchPolicy.shouldOffer(1800.0, 600.0, 0.2, emptyList(), false))
    }

    @Test fun declinedAlternativesAreNotReOfferedAndOffersDoNotStack() {
        assertFalse(RouteSwitchPolicy.shouldOffer(1800.0, 600.0, 0.2, listOf(0.85), false))
        assertFalse(RouteSwitchPolicy.shouldOffer(1800.0, 600.0, 0.2, emptyList(), true))
    }

    @Test fun staleOrSkewedTrafficNeverSwitchesOrReranks() {
        assertFalse(RouteSwitchPolicy.mayAccept(trafficCurrent = false, navigating = true, reliablePosition = true))
        val now = 1_800_000_000_000L
        fun r(at: Long) = Route(emptyList(), 1000.0, 600.0, emptyList(), "synthetic", live = true, trafficDelay = 0.0, trafficCheckedAt = at)
        assertTrue(TrafficTiming.markComparability(listOf(r(now), r(now - 60_000)), now).all { it.comparisonReliable })
        assertFalse(TrafficTiming.markComparability(listOf(r(now), r(now - 3 * 60_000)), now).any { it.comparisonReliable })
    }
}
