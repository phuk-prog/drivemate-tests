package com.drivemate.app.map

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.RouteGeometryIndex
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C17.4 (29 Oct): erase the driven route continuously behind the car, not in 50 m chunks.
 * The long route keeps 50 m pieces (filter changes once per piece); the car's own piece is
 * clipped at the car every fix.
 */
class C17RouteHeadTest {
    // ~2 km straight north from Stockport.
    private val start = LatLon(53.4000, -2.1600)
    private val route = (0..20).map { LatLon(start.lat + it * 0.0009, start.lon) }
    private val index = RouteGeometryIndex(route)

    private fun headCoords(along: Double): List<LatLon> {
        val f = JSONObject(RouteLayers.headJson(index, along)).getJSONArray("features")
        if (f.length() == 0) return emptyList()
        val c = f.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        return (0 until c.length()).map { LatLon(c.getJSONArray(it).getDouble(1), c.getJSONArray(it).getDouble(0)) }
    }

    private fun length(line: List<LatLon>) = line.zipWithNext { a, b -> Geo.distance(a, b) }.sum()

    @Test fun headStartsExactlyAtTheCarAndEndsAtItsPieceEnd() {
        for (along in listOf(0.5, 12.0, 49.9, 50.0, 137.3, 999.0)) {
            val head = headCoords(along)
            val end = RouteLayers.pieceEnd(along, index.total)
            assertTrue("head drawn at $along", head.size >= 2)
            val car = index.slice(along, along + 0.01).first()
            assertEquals("starts at the car ($along m)", 0.0, Geo.distance(car, head.first()), 0.05)
            assertEquals("head is the rest of the car's piece", end - along, length(head), 0.5)
            assertTrue("never longer than one piece", length(head) <= RouteLayers.PIECE_M + 0.5)
        }
    }

    @Test fun headPlusWholePiecesAheadCoverTheRestOfTheRouteWithNoGapOrOverlap() {
        val pieces = RouteLayers.pieces(route)
        for (along in listOf(3.0, 75.0, 180.5, index.total - 10.0)) {
            val end = RouteLayers.pieceEnd(along, index.total)
            val ahead = pieces.filter { it.second > end + 0.001 }.sumOf { length(it.first) }
            assertEquals("at $along m", index.total - along, length(headCoords(along)) + ahead, 1.0)
        }
    }

    @Test fun beforeTheTripStartsTheWholeRouteIsDrawnAndNoHead() {
        assertTrue(headCoords(-1.0).isEmpty())
        assertTrue(RouteLayers.afterCurrentPiece(-1.0, index.total).toString().contains("-1"))
    }

    @Test fun pieceEndsAreFiftyMetreBoundariesCappedAtTheRouteEnd() {
        assertEquals(50.0, RouteLayers.pieceEnd(0.0, 1_000.0), 0.0)
        assertEquals(100.0, RouteLayers.pieceEnd(50.0, 1_000.0), 0.0)
        assertEquals(1_000.0, RouteLayers.pieceEnd(990.0, 1_000.0), 0.0)
    }
}
