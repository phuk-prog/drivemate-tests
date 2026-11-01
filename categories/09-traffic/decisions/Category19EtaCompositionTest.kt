package com.drivemate.tests.cat09

import com.drivemate.app.data.EtaComposition
import com.drivemate.app.data.EtaComposition.Estimate
import com.drivemate.app.data.Route
import com.drivemate.app.data.TrafficTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 19 (19.3): the driving screen's time left picks live traffic, then your speeds, then the route's time; delay is never added. */
class Category19EtaCompositionTest {
    private val now = 1_800_000_000_000L
    private val plain = Route(emptyList(), 10_000.0, 1_200.0, emptyList(), "synthetic")
    private val live = Route(emptyList(), 10_000.0, 1_200.0, emptyList(), "synthetic",
        live = true, trafficDelay = 60.0, trafficCheckedAt = now)

    @Test fun q19_3_freshLiveEstimateIsScaledToTheDistanceLeftAndNeverAddsItsDelay() {
        val r = EtaComposition.compose(now, 5_000.0, 900.0,
            live = Estimate(600.0, 10_000.0, now), liveDelaySeconds = 60.0,
            learned = Estimate(2_000.0, 10_000.0, 0L), route = plain)
        assertEquals(300.0, r.remainingSeconds, 1e-9)
        assertTrue(r.trafficCurrent)
        assertFalse(r.learnedEta)
        assertEquals(60.0, r.trafficDelaySeconds!!, 0.0)
    }

    @Test fun q19_3_staleLiveEstimateFallsBackToYourSpeedsWithNoTrafficClaim() {
        val r = EtaComposition.compose(now, 5_000.0, 900.0,
            live = Estimate(600.0, 10_000.0, now - TrafficTiming.FRESH_FOR - 1), liveDelaySeconds = 60.0,
            learned = Estimate(400.0, 10_000.0, 0L), route = plain)
        assertEquals(200.0, r.remainingSeconds, 1e-9)
        assertTrue(r.learnedEta)
        assertFalse(r.trafficCurrent)
        assertNull(r.trafficDelaySeconds)
    }

    @Test fun q19_3_noTrafficAtAllShowsTheRoutesOwnTimeAndUnknownDelay() {
        val r = EtaComposition.compose(now, 5_000.0, 900.0,
            live = null, liveDelaySeconds = 0.0, learned = null, route = plain)
        assertEquals(900.0, r.remainingSeconds, 0.0)
        assertFalse(r.trafficCurrent)
        assertFalse(r.learnedEta)
        assertNull("unknown delay is null, never zero", r.trafficDelaySeconds)
    }

    @Test fun q19_3_currentRouteTrafficSuppressesYourSpeedEstimate() {
        val r = EtaComposition.compose(now, 5_000.0, 900.0,
            live = null, liveDelaySeconds = 0.0, learned = Estimate(2_000.0, 10_000.0, 0L), route = live)
        assertEquals(900.0, r.remainingSeconds, 0.0)
        assertFalse(r.learnedEta)
        assertTrue(r.trafficCurrent)
        assertEquals(60.0, r.trafficDelaySeconds!!, 0.0)
    }

    @Test fun q19_3_unusableDistanceAnchorIsIgnoredNotDividedBy() {
        val r = EtaComposition.compose(now, 5_000.0, 900.0,
            live = Estimate(600.0, 0.0, now), liveDelaySeconds = 60.0,
            learned = Estimate(400.0, 0.0, 0L), route = plain)
        assertEquals(900.0, r.remainingSeconds, 0.0)
        assertFalse(r.trafficCurrent)
    }

    @Test fun q19_3_arrivedLeavesZeroTimeLeft() {
        val r = EtaComposition.compose(now, 0.0, 0.0,
            live = Estimate(600.0, 10_000.0, now), liveDelaySeconds = 60.0, learned = null, route = plain)
        assertEquals(0.0, r.remainingSeconds, 0.0)
    }
}
