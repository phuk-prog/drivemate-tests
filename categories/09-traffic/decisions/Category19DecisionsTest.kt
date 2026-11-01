package com.drivemate.tests.cat09

import com.drivemate.app.data.LiveTrafficInfo
import com.drivemate.app.data.Route
import com.drivemate.app.data.TrafficDetails
import com.drivemate.app.data.TrafficLookupStatus
import com.drivemate.app.data.TrafficSnapshot
import com.drivemate.app.data.TrafficTiming
import com.drivemate.app.nav.RouteSwitchPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 19 decisions: freshness, grounded status wording, direction evidence and detour limits. */
class Category19DecisionsTest {
    private val now = 1_800_000_000_000L

    @Test fun q19_1_freshnessWindowIsExact() {
        assertTrue(TrafficTiming.fresh(now, now))
        assertTrue(TrafficTiming.fresh(now - TrafficTiming.FRESH_FOR, now))
        assertFalse(TrafficTiming.fresh(now - TrafficTiming.FRESH_FOR - 1, now))
        assertFalse(TrafficTiming.fresh(0, now))
        assertFalse(TrafficTiming.fresh(now + 1, now))
    }

    @Test fun q19_1_directionIsOnlyTakenFromExplicitWords() {
        assertEquals("northbound", TrafficDetails.directionInText("Northbound carriageway closed"))
        assertEquals("clockwise", TrafficDetails.directionInText("M60 clockwise J24 to J25"))
        assertNull(TrafficDetails.directionInText("Road closed"))
        assertNull(TrafficDetails.directionInText(null))
    }

    @Test fun q19_2_closureStatusNamesItsAgeAndSource() {
        val current = TrafficSnapshot<String>(emptyList(), TrafficLookupStatus.CURRENT, checkedAt = now - 120_000, source = "National Highways")
        assertTrue(current.lookupSucceeded)
        assertTrue(current.summary(now).contains("National Highways") && current.summary(now).contains("2 min ago"))
        val cached = TrafficSnapshot<String>(emptyList(), TrafficLookupStatus.RECENT_CACHE, checkedAt = now - 180_000, source = "National Highways")
        assertFalse(cached.lookupSucceeded)
        assertTrue(cached.summary(now).startsWith("Closure check unavailable · using information from 3 min ago"))
        val unavailable = TrafficSnapshot<String>(emptyList(), TrafficLookupStatus.UNAVAILABLE, source = "National Highways")
        assertTrue(unavailable.summary(now).contains("could not be checked"))
        assertEquals("Road closures are not connected", TrafficSnapshot<String>(emptyList(), TrafficLookupStatus.DISABLED).summary(now))
    }

    @Test fun q19_4_detourMustBeADistinctMaterialSaving() {
        assertFalse("same road, not a detour", RouteSwitchPolicy.shouldOffer(1800.0, 600.0, 0.85, emptyList(), false))
        assertFalse("saving below the materiality floor", RouteSwitchPolicy.shouldOffer(1800.0, 130.0, 0.2, emptyList(), false))
        assertTrue(RouteSwitchPolicy.shouldOffer(1800.0, 600.0, 0.2, emptyList(), false))
    }

    @Test fun q19_1_liveReportAgeIsExactAtTheMaximum() {
        val live = LiveTrafficInfo("TomTom", now)
        assertFalse(TrafficDetails.stale(live, now))
        assertFalse(TrafficDetails.stale(live, now + TrafficDetails.LIVE_MAX_AGE))
        assertTrue(TrafficDetails.stale(live, now + TrafficDetails.LIVE_MAX_AGE + 1))
        // No report at all is not a stale report; the callers only ask about reports they hold.
        assertFalse(TrafficDetails.stale(null as LiveTrafficInfo?, now))
    }

    @Test fun q19_4_detourOfferSpeechNeverPromisesACountdown() {
        val speech = RouteSwitchPolicy.offerSpeech(" on the M60", 4, alwaysAsk = true)
        assertTrue(speech.endsWith("Switch or keep?"))
        assertFalse(speech.contains("second"))
        assertFalse("an offer is accepted only with current traffic, navigating and a reliable fix",
            RouteSwitchPolicy.mayAccept(trafficCurrent = true, navigating = true, reliablePosition = false))
    }

    @Test fun q19_6_currentTrafficRanksFirstAndUntimedRoutesAreNotCompared() {
        val untimed = Route(emptyList(), 1000.0, 500.0, emptyList(), "synthetic")
        val timed = Route(emptyList(), 1000.0, 600.0, emptyList(), "synthetic", live = true, trafficDelay = 60.0, trafficCheckedAt = now)
        val ranked = TrafficTiming.rank(listOf(untimed, timed), now)
        assertEquals(600.0, ranked.first().duration, 0.0)
        assertTrue("no untimed route is treated as comparable", TrafficTiming.markComparability(listOf(untimed, timed), now).none { it.comparisonReliable })
    }
}
