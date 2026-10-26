package com.drivemate.app.controller

import com.drivemate.app.data.*
import com.drivemate.app.nav.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Regression: online guidance must load without a manual offline download, including long trips. */
class OnlineRoadDataTest {
    private val start = LatLon(50.5, -3.0)
    private fun engine(): NavigationEngine {
        val end = Geo.offset(start, 0.0, 500_000.0)
        return NavigationEngine(Route(listOf(start, end), 500_000.0, 20_000.0,
            listOf(RouteStep("Continue", "depart", "straight", "A road", 500_000.0, start),
                RouteStep("Arrive", "arrive", "", "", 0.0, end)), "Synthetic long journey"))
    }

    @Test fun onlineGuidanceDoesNotRequireManualDownload() {
        for (name in listOf("lanes-106_-5.json", "limits-106_-5.json", "roadinfo-106_-5.json"))
            assertTrue("$name must load online", WholeUk.mayFetch(name))
        assertFalse("Unrelated place downloads remain manual", WholeUk.mayFetch("places-106_-5.json"))
        assertTrue(WholeUk.userDownload { WholeUk.mayFetch("places-106_-5.json") })
        assertFalse("Manual scope must not leak", WholeUk.mayFetch("places-106_-5.json"))
    }

    @Test fun longJourneyLoadsOnlyTheNearAheadWindowAndMovesItForward() {
        val engine = engine()
        val clipped = LaneRegionWindow.ahead(engine.route.points)
        assertEquals(30_000.0, Geo.distance(clipped.first(), clipped.last()), 1.0)
        assertTrue(LaneRegionWindow.ahead(engine.route.points, 0.0).isEmpty())
        val first = RoadDataWindow.points(engine, 0.0)
        assertEquals(start, first.first())
        assertEquals(30_000.0, Geo.distance(first.first(), first.last()), 1.0)
        assertTrue(Geo.distance(first.last(), engine.route.points.last()) > 400_000)
        val later = RoadDataWindow.points(engine, 100_000.0)
        assertEquals(99_900.0, Geo.distance(start, later.first()), 1.0)
        assertEquals(130_000.0, Geo.distance(start, later.last()), 1.0)
        val ending = RoadDataWindow.points(engine, engine.total - 100.0)
        assertEquals(engine.route.points.last(), ending.last())
        assertTrue(RoadDataWindow.points(engine, Double.NaN).isEmpty())
    }

    @Test fun retryIsPacedButDoesNotWaitForTwoKilometresAfterFailure() {
        val window = RoadDataWindow()
        val route = Any()
        assertTrue(window.due(route, 0.0, start, 1, 100))
        assertFalse(window.due(route, 0.0, start, 1, 30_099))
        assertTrue("Stationary/slow traffic still retries", window.due(route, 0.0, start, 1, 30_100))
        assertFalse(window.due(route, 100.0, start, 1, 30_101))
        assertTrue("Refill before consuming the look-ahead", window.due(route, 2_000.0, start, 1, 30_102))
    }

    @Test fun rerouteRegionAndMapGenerationRefreshImmediately() {
        val window = RoadDataWindow()
        val route = Any()
        assertTrue(window.due(route, 0.0, start, 1, 100))
        assertTrue(window.due(route, 0.0, start, 2, 101))
        assertTrue(window.due(route, 0.0, LatLon(51.1, -3.0), 2, 102))
        assertTrue(window.due(Any(), 0.0, start, 2, 103))
        window.clear()
        assertTrue(window.due(route, 0.0, start, 2, 104))
        assertFalse(window.due(route, Double.NaN, start, 2, 105))
    }

    @Test fun rollingLimitsRetainEarlierValuesAndAcceptNewValues() {
        assertEquals(listOf(48.3, 112.7, null, 80.5),
            RoadDataWindow.mergeLimits(listOf(48.3, 96.6, null, null), listOf(null, 112.7, null, 80.5)))
        assertEquals(listOf<Double?>(null, 112.7),
            RoadDataWindow.mergeLimits(emptyList(), listOf(null, 112.7)))
    }

    @Test fun providerReceivesOnlyWindowAndDeliversLimitsWithoutOfflineDownload() = runBlocking {
        val engine = engine()
        val window = RoadDataWindow.points(engine, 0.0)
        val requests = mutableListOf<List<LatLon>>()
        val known = LimitWay(window, 112.7)
        val provider = object : RoadWarningProvider {
            override fun covers(point: LatLon) = true
            override fun cameras(): List<Hazard>? = emptyList()
            override fun around(point: LatLon): List<Hazard> = emptyList()
            override fun limitsAlong(points: List<LatLon>): List<LimitWay> {
                requests += points
                return listOf(known)
            }
            override fun roadInfoAlong(points: List<LatLon>): RouteData {
                requests += points
                return RouteData(emptyList(), emptyList())
            }
            override fun alongRoute(points: List<LatLon>): RouteData {
                fail("Available own data must not fall through")
                return RouteData(emptyList(), emptyList())
            }
            override fun loadCache(): List<Hazard> = emptyList()
            override fun saveCache(hazards: List<Hazard>) {}
        }
        val tasks = SessionTasks(this)
        tasks.beginTrip()
        var delivered: List<LimitWay> = emptyList()
        RoadWarningController(provider, this, onUnavailable = { fail("Data should be available") })
            .loadRoute(engine, tasks.ticket(), onData = {}, onLimits = { delivered = it }, points = window)
        assertEquals(listOf(window, window), requests)
        assertEquals(listOf(known), delivered)
        tasks.endTrip()
    }

    @Test fun privacyTextDisclosesRegionalOnlineRequests() {
        val github = com.drivemate.app.DataSharing.services.single { it.name.contains("GitHub") }
        assertTrue(github.sent.contains("approximate areas"))
        assertFalse(github.sent.contains("Nothing is downloaded for the area"))
        assertTrue(com.drivemate.app.PrivacyNotice.text().contains("approximate areas"))
    }
}
