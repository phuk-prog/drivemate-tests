package com.drivemate.tests.cat08

import com.drivemate.app.data.Settings
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

class ReroutingDecisionsTest {
    @Test fun newInstallAsksBeforeTakingAnOptionalAlternative() {
        assertTrue(Settings().alwaysAskFaster)
        assertTrue(RouteSwitchPolicy.offerPrompt(true).contains("Switch or keep?"))
        assertFalse(RouteSwitchPolicy.offerSpeech("", 3, true).contains("countdown"))
    }

    @Test fun savingsMustBeMaterialAndAnAlternativeMustBeDifferent() {
        fun offer(seconds: Double, savings: Double, overlap: Double, declined: List<Double> = emptyList(), visible: Boolean = false) =
            RouteSwitchPolicy.shouldOffer(seconds, savings, overlap, declined, visible)
        assertFalse(offer(1_000.0, 120.0, 0.4))
        assertTrue(offer(1_000.0, 121.0, 0.4))
        assertFalse(offer(5_000.0, 400.0, 0.4))
        assertTrue(offer(5_000.0, 401.0, 0.4))
        assertFalse(offer(1_000.0, 180.0, 0.8))
        assertFalse(offer(1_000.0, 180.0, 0.4, listOf(0.9)))
        assertFalse(offer(1_000.0, 180.0, 0.4, visible = true))
    }

    @Test fun uncertainProviderTimingCannotOfferAnAlternative() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0, 0.0)) {
            assertFalse(RouteSwitchPolicy.shouldOffer(bad, 180.0, 0.4, emptyList(), false))
            assertFalse(RouteSwitchPolicy.shouldOffer(1_000.0, bad, 0.4, emptyList(), false))
        }
        assertFalse(RouteSwitchPolicy.shouldOffer(1_000.0, 1_000.0, 0.4, emptyList(), false))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1)) {
            assertFalse(RouteSwitchPolicy.shouldOffer(1_000.0, 180.0, bad, emptyList(), false))
            assertFalse(RouteSwitchPolicy.shouldOffer(1_000.0, 180.0, 0.4, listOf(bad), false))
        }
    }

    @Test fun staleOffersAndGpsGapsNeverReplaceTheCurrentRoute() {
        assertTrue(RouteSwitchPolicy.mayAccept(true, true, true))
        assertFalse(RouteSwitchPolicy.mayAccept(false, true, true))
        assertFalse(RouteSwitchPolicy.mayAccept(true, false, true))
        assertFalse(RouteSwitchPolicy.mayAccept(true, true, false))
    }

    @Test fun retryBackoffRemainsBoundedAndRejectsClockRegression() {
        assertEquals(6_000L, ReroutePolicy.waitMs(0))
        assertEquals(12_000L, ReroutePolicy.waitMs(1))
        assertEquals(60_000L, ReroutePolicy.waitMs(20))
        assertFalse(ReroutePolicy.mayReroute(6_000, 0, 0))
        assertTrue(ReroutePolicy.mayReroute(6_001, 0, 0))
        assertFalse(ReroutePolicy.mayReroute(1, 2, 0))
        assertFalse(ReroutePolicy.mayReroute(Long.MAX_VALUE, Long.MIN_VALUE, 0))
    }

    @Test fun denseFixesAndInvalidDistanceDoNotCreateWrongTurns() {
        assertFalse(OffRouteRule.lastedLongEnough(4, 1.5))
        assertTrue(OffRouteRule.lastedLongEnough(4, 2.7))
        assertFalse(OffRouteRule.lastedLongEnough(1, Double.NaN))
        assertFalse(OffRouteRule.lastedLongEnough(0, 3.0))
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            assertFalse(OffRouteRule.clearlyOff(bad, 5.0, false, false, 10.0))
            assertFalse(OffRouteRule.turnedOffWithoutMap(100.0, bad, 10.0, false))
            assertFalse(OffRouteRule.wrongTurn(10.0, false, 50.0, bad, 90.0, listOf(0.0)))
        }
        assertFalse(OffRouteRule.wrongTurn(10.0, true, 50.0, 5.0, 90.0, listOf(0.0)))
        assertFalse(OffRouteRule.wrongTurn(10.0, false, 50.0, 5.0, 90.0, listOf(0.0, 90.0)))
        assertTrue(OffRouteRule.wrongTurn(10.0, false, 50.0, 5.0, 90.0, listOf(0.0, 0.0)))
    }
}
