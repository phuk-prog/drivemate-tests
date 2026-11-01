package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 11.4 pure checks: reroute requests are bounded. Each failed request doubles the wait
 * up to one minute, a clock that goes backwards never allows a request, and a reroute waits
 * the full wait before the next one.
 */
class C11ReroutePolicyTest {
    /** 11.4: the wait starts at 6 s, doubles after each failure, and never passes one minute. */
    @Test fun q11_4_retryWaitDoublesAndIsBounded() {
        assertEquals(6_000L, ReroutePolicy.waitMs(0))
        assertEquals(12_000L, ReroutePolicy.waitMs(1))
        assertEquals(24_000L, ReroutePolicy.waitMs(2))
        assertEquals(60_000L, ReroutePolicy.waitMs(4))
        for (n in -5..30) assertTrue(ReroutePolicy.waitMs(n) in 6_000L..60_000L)
    }

    /** 11.4: a new reroute is allowed only after the full wait since the last one. */
    @Test fun q11_4_mayRerouteOnlyAfterTheWait() {
        assertFalse(ReroutePolicy.mayReroute(nowMs = 10_000L, lastRerouteMs = 5_000L, consecutiveFailures = 0))
        assertFalse("exactly at the wait is still too soon", ReroutePolicy.mayReroute(11_000L, 5_000L, 0))
        assertTrue(ReroutePolicy.mayReroute(nowMs = 11_001L, lastRerouteMs = 5_000L, consecutiveFailures = 0))
    }

    /** 11.4: after repeated failures a request waits longer, so patchy signal does not re-ask every 6 s. */
    @Test fun q11_4_failuresLengthenTheWait() {
        assertFalse(ReroutePolicy.mayReroute(nowMs = 20_000L, lastRerouteMs = 5_000L, consecutiveFailures = 2))
        assertTrue(ReroutePolicy.mayReroute(nowMs = 30_001L, lastRerouteMs = 5_000L, consecutiveFailures = 2))
    }

    /** 11.4: invalid or backwards clock values never allow a request. */
    @Test fun q11_4_invalidClockNeverAllowsReroute() {
        assertFalse(ReroutePolicy.mayReroute(nowMs = 1_000L, lastRerouteMs = 5_000L, consecutiveFailures = 0))
        assertFalse(ReroutePolicy.mayReroute(nowMs = -1L, lastRerouteMs = 5_000L, consecutiveFailures = 0))
        assertFalse(ReroutePolicy.mayReroute(nowMs = 100_000L, lastRerouteMs = -1L, consecutiveFailures = 0))
    }
}
