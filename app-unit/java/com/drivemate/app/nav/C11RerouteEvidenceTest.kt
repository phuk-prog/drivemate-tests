package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Category 11.2 pure checks: a reroute needs reliable, matched off-route evidence. Unknown or
 * unmatched evidence (non-finite accuracy, unknown heading, no route bearing, stopped car, or a
 * heading that still agrees with the route) never counts toward a reroute.
 */
class C11RerouteEvidenceTest {
    /** 11.2: a plain off-route signal needs four fixes; only a clear wrong turn is accepted after one. */
    @Test fun q11_2_plainOffRouteNeedsFourFixes() {
        assertEquals(4, OffRouteRule.fixesNeeded(wrongTurn = false, turnedOff = false, nearEnd = false))
        assertEquals(1, OffRouteRule.fixesNeeded(wrongTurn = true, turnedOff = false, nearEnd = false))
    }

    /** 11.2: an unmatched road with a heading that still agrees with the route is not a turned-off road. */
    @Test fun q11_2_headingAgreeingWithRouteIsNotTurnedOff() {
        assertFalse(OffRouteRule.turnedOffWithoutMap(100.0, 5.0, 10.0, headingAgrees = true))
    }

    /** 11.2: a stopped or barely moving car never reports a turned-off road. */
    @Test fun q11_2_stoppedCarIsNotTurnedOff() {
        assertFalse(OffRouteRule.turnedOffWithoutMap(100.0, 5.0, 1.0, headingAgrees = false))
    }

    /** 11.2: a fix with unknown accuracy never counts as clearly off the route. */
    @Test fun q11_2_unknownAccuracyIsNotClearlyOff() {
        assertFalse(OffRouteRule.clearlyOff(100.0, Double.NaN, headingAgrees = false, nearEnd = false, speed = 10.0))
    }

    /** 11.2: a gyroscope wrong turn needs a known heading and a route bearing to compare against. */
    @Test fun q11_2_wrongTurnNeedsHeadingAndRouteBearing() {
        assertFalse(OffRouteRule.wrongTurn(10.0, false, 50.0, 5.0, null, listOf(0.0)))
        assertFalse(OffRouteRule.wrongTurn(10.0, false, 50.0, 5.0, 90.0, emptyList()))
    }

    /** 11.2: an unknown heading never agrees or disagrees, so it cannot justify rerouting. */
    @Test fun q11_2_unknownHeadingIsNeverAMatch() {
        assertFalse(OffRouteRule.headingAgrees(null, 10.0, 90.0))
    }
}
