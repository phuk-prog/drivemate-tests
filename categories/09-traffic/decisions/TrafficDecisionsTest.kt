package com.drivemate.tests.cat09

import com.drivemate.app.data.*
import com.drivemate.app.nav.LatLon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TrafficDecisionsTest {
    private val now = 1_800_000_000_000L
    private fun info(at: Long = now) = LiveTrafficInfo("TomTom", at)
    private fun report(id: Long, at: Long = now) = Hazard(id, HazardType.TRAFFIC, LatLon(53.0, -2.0), at, live = info(at))
    private fun route(delay: Double = 120.0, duration: Double = 600.0, at: Long = now) =
        Route(emptyList(), 1000.0, duration, emptyList(), "synthetic", live = true, trafficDelay = delay, trafficCheckedAt = at)

    @Test fun q9_2_5_12_missingFutureAndOldChecksAreNeverLive() {
        assertTrue(TrafficDetails.stale(info(0), now))
        assertTrue(TrafficDetails.stale(info(now + 1), now))
        assertFalse(TrafficDetails.stale(info(now - TrafficDetails.LIVE_MAX_AGE), now))
        assertTrue(TrafficDetails.stale(info(now - TrafficDetails.LIVE_MAX_AGE - 1), now))
        assertTrue(route().trafficCurrent(now))
        assertFalse(route(at = now - TrafficTiming.FRESH_FOR - 1).trafficCurrent(now))
    }
    @Test fun q9_2_malformedResponseIsFailureButCheckedEmptyIsSuccess() {
        assertTrue(TomTom.parseIncidents(JSONObject("{\"incidents\":[]}"), now).isEmpty())
        try {
            TomTom.parseIncidents(JSONObject("{\"error\":\"unavailable\"}"), now)
            fail("An invalid response must not clear valid reports as a successful empty lookup")
        } catch (_: java.io.IOException) { }
    }
    @Test fun q9_5_12_replacementDropsMalformedFreshItemsAndRetainsUnrefreshedSources() {
        val tfl = report(1).copy(live = info().copy(source = "TfL"))
        val good = report(2)
        val unknown = report(3).copy(live = null)
        val blank = report(4).copy(live = info().copy(source = ""))
        val merged = TrafficDetails.mergeLive(listOf(tfl, report(8)), listOf(good, unknown, blank, report(5, now + 1)), setOf("TomTom"), now)
        assertEquals(listOf(1L, 2L), merged.map { it.id })
        assertEquals(listOf(tfl), TrafficDetails.mergeLive(listOf(tfl, good), emptyList(), setOf("TomTom"), now))
    }
    @Test fun q9_6_7_impossibleDelayAndJourneyTimesDoNotClaimCurrentTraffic() {
        for (r in listOf(route(-1.0), route(Double.NaN), route(601.0), route(duration = Double.POSITIVE_INFINITY), route(duration = -1.0)))
            assertFalse(r.trafficCurrent(now))
        assertTrue(TrafficDetails.journeyLine(600.0, -1.0, true).contains("delay is unknown"))
        assertTrue(TrafficDetails.journeyLine(600.0, null, false).contains("traffic unavailable"))
        assertTrue(TrafficDetails.journeyLine(600.0, 120.0, true).contains("including about 2 min"))
    }
    @Test fun q9_1_8_trafficNeverEnablesRouteOrSearchUploads() {
        assertTrue(TomTom.route(listOf(LatLon(53.0, -2.0)), null, false, emptySet()).isEmpty())
        assertNull(TomTom.retime(route()))
        assertTrue(TomTom.search("synthetic", null).isEmpty())
    }
    @Test fun q9_2_invalidExternalCoordinatesAreNotShownAsIncidents() {
        val malformed = org.json.JSONArray("""[{"status":"Active","severity":"Severe","id":"synthetic","point":"[0,91]","hasClosures":true}]""")
        assertTrue(TfL.parseDisruptions(malformed, now).isEmpty())
    }
    @Test fun q9_10_11_refreshReserveAndClockRegressionStayBounded() {
        val c = com.drivemate.app.controller.TrafficController()
        val stream = com.drivemate.app.controller.TrafficController.Stream.INCIDENTS
        assertTrue(c.begin(stream, 100_000, 0))
        assertFalse(c.begin(stream, 200_000, 0))
        c.finish(stream)
        assertFalse(c.begin(stream, 100_001, 0))
        assertTrue(c.begin(stream, 99_999, 0))
        c.finish(stream)
        assertFalse(c.begin(stream, 300_000, 2_375))
    }
    @Test fun q9_3_4_13_streetWorksNeverPromoteClosureCoverage() {
        val unavailable = TrafficSnapshot<NationalHighways.Event>(emptyList(), TrafficLookupStatus.UNAVAILABLE, source = "National Highways")
        val work = NationalHighways.Event("synthetic", NationalHighways.Kind.ROADWORKS, listOf(LatLon(53.0, -2.0)))
        val closed = work.copy(kind = NationalHighways.Kind.ROAD_CLOSED)
        val combined = UkRoadFeeds.combine(unavailable, TrafficSnapshot(listOf(work, closed), TrafficLookupStatus.CURRENT, now))
        assertEquals(TrafficLookupStatus.UNAVAILABLE, combined.status)
        assertEquals(listOf(work), combined.items)
        assertEquals("National Highways", combined.source)
    }
    @Test fun q9_4_5_13_cachedFeedRetainsActualRequestTimeAndProvenance() {
        val oldEnabled = UkRoadFeeds.ukpnEnabled
        val oldFetcher = UkRoadFeeds.fetcher
        try {
            UkRoadFeeds.ukpnEnabled = true
            UkRoadFeeds.clearCache()
            UkRoadFeeds.focusOn(listOf(LatLon(51.5, 0.0)))
            UkRoadFeeds.fetcher = { "{\"features\":[]}" }
            val first = UkRoadFeeds.snapshot(now)
            val cached = UkRoadFeeds.snapshot(now + 1000)
            assertEquals(now, first.checkedAt)
            assertEquals(now, cached.checkedAt)
            assertEquals("UK Power Networks", cached.source)
            assertTrue(cached.lookupSucceeded)
        } finally {
            UkRoadFeeds.fetcher = oldFetcher
            UkRoadFeeds.ukpnEnabled = oldEnabled
            UkRoadFeeds.clearCache()
        }
    }

    @Test fun q19_3_etaUsesRouteDurationOnceAndDelayOnlyExplainsIt() {
        val r = route(delay = 120.0, duration = 600.0)
        assertTrue(r.trafficCurrent(now))
        assertEquals(600_000L, ClosureArrivalPolicy.arrivalAt(r, r.distance, 0L))
        assertEquals(300_000L, ClosureArrivalPolicy.arrivalAt(r, r.distance / 2, 0L))
        val line = TrafficDetails.journeyLine(600.0, 120.0, true)
        assertTrue(line.startsWith("Journey time 10 min"))
        assertFalse(line.contains("12 min"))
    }
}
