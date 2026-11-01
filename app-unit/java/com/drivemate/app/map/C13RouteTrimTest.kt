package com.drivemate.app.map

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RouteGeometryIndex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C13.4: across a reroute, the travelled line is trimmed and the upcoming route kept; alternative
 * routes are drawn as their own full lines and never trimmed as the main route.
 */
class C13RouteTrimTest {
    // Main route: ~2 km straight north from Stockport.
    private val start = LatLon(53.4000, -2.1600)
    private val mainRoute = (0..20).map { LatLon(start.lat + it * 0.0009, start.lon) }

    // Reroute from the car 600 m along the main route: turns east for ~1 km.
    private val carAt600 = RouteGeometryIndex(mainRoute).slice(600.0, 600.01).first()
    private val reroute = listOf(carAt600) + (1..12).map { LatLon(carAt600.lat, carAt600.lon + it * 0.0013) }

    // Alternative from the same start, offset west, drawn separately.
    private val alternative = (0..20).map { LatLon(start.lat + it * 0.0009, start.lon - 0.004) }

    private fun coords(json: String): List<LatLon> {
        val out = ArrayList<LatLon>()
        val features = JSONObject(json).getJSONArray("features")
        for (f in 0 until features.length()) {
            val c = features.getJSONObject(f).getJSONObject("geometry").getJSONArray("coordinates")
            for (i in 0 until c.length()) out += LatLon(c.getJSONArray(i).getDouble(1), c.getJSONArray(i).getDouble(0))
        }
        return out
    }

    private fun length(line: List<LatLon>) = line.zipWithNext { a, b -> Geo.distance(a, b) }.sum()

    /** What the main route layers draw at [along]: the head plus the pieces still ahead. */
    private fun drawnMain(route: List<LatLon>, along: Double): List<LatLon> {
        val index = RouteGeometryIndex(route)
        val head = coords(RouteLayers.headJson(index, along))
        val ahead = RouteLayers.pieces(route).filter { it.second > RouteLayers.pieceEnd(along, index.total) + 0.001 }
        return head + ahead.flatMap { it.first }
    }

    @Test fun afterAReroutePiecesAndHeadComeFromTheNewRouteOnly() {
        val drawn = drawnMain(reroute, 0.5)
        val reroutedTotal = RouteGeometryIndex(reroute).total
        assertEquals("upcoming reroute drawn with no gap or overlap", reroutedTotal - 0.5, length(drawn), 2.0)
        // No point of the old main route north of the car (already travelled) is still drawn.
        val oldTravelled = mainRoute.filter { it.lat < carAt600.lat - 1e-6 }
        assertTrue(oldTravelled.isNotEmpty())
        assertFalse(drawn.any { p -> oldTravelled.any { Geo.distance(it, p) < 0.5 } })
    }

    @Test fun thePlaceTheCarIsOnStaysTheHeadStartAtEveryFixAcrossTheReroute() {
        val index = RouteGeometryIndex(reroute)
        for (along in listOf(0.0, 4.0, 49.0, 51.0, 180.0)) {
            val head = coords(RouteLayers.headJson(index, along))
            val car = index.slice(along, along + 0.01).first()
            assertEquals("head starts at car at $along m", 0.0, Geo.distance(car, head.first()), 0.05)
        }
    }

    @Test fun theTravelledLineOfTheOldRouteIsTrimmedContinuouslyBeforeTheReroute() {
        // Before the reroute, the old route behind the car is gone: nothing south of the car is drawn.
        val index = RouteGeometryIndex(mainRoute)
        for (along in listOf(600.0, 600.8, 601.6)) {
            val drawn = drawnMain(mainRoute, along)
            val behind = mainRoute.filter { Geo.distance(it, index.slice(along, along + 0.01).first()) > 0.5 && it.lat < index.slice(along, along + 0.01).first().lat - 1e-6 }
            assertFalse("travelled points still drawn at $along m", drawn.any { p -> behind.any { Geo.distance(it, p) < 0.5 } })
        }
    }

    @Test fun alternativesAreDrawnWholeAndNeverTrimmedAsTheMainRoute() {
        // Alternatives go to their own source as complete lines, whatever the progress is.
        val alt = coords(RouteLayers.lines(listOf(alternative)))
        assertEquals("alternative is whole", RouteGeometryIndex(alternative).total, length(alt), 1.0)
        // The main route's pieces never contain alternative geometry.
        val mainPieces = RouteLayers.pieces(mainRoute).flatMap { it.first }
        assertFalse(mainPieces.any { Geo.distance(it, alternative.first()) < 1.0 })
        // The trim filters and head layers only touch the main route layers, never the alternative.
        assertFalse(RouteLayers.ROUTE_LINE_IDS.any { it.contains("alt") })
        assertFalse(RouteLayers.HEAD_LINE_IDS.any { it.contains("alt") })
    }
}
