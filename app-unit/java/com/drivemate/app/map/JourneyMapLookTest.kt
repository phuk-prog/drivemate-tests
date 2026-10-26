package com.drivemate.app.map

import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.ImmersiveMap
import com.drivemate.app.nav.LatLon
import com.drivemate.app.ui.mapProblemChipText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Journey review 6, 10 and 12: the owner's choices for how the driving map looks. */
class JourneyMapLookTest {
    private val start = LatLon(53.40, -2.20)
    private val route = (0..100).map { Geo.offset(start, 0.0, it * 100.0) } // 10 km north

    /** 6: "Remove it": the route is drawn in pieces, each knowing where it ends, so driven ones drop. */
    @Test fun q6_routeInPiecesSoTheDrivenPartIsRemoved() {
        val pieces = RouteLayers.pieces(route)
        assertTrue("about 10 km in 50 m pieces", pieces.size in 199..201)
        val length = route.zipWithNext().sumOf { (a, b) -> Geo.distance(a, b) }
        assertEquals("the last piece ends at the route's end", length, pieces.last().second, 0.5)
        pieces.zipWithNext().forEach { (a, b) ->
            assertEquals("pieces join up", 0.0, Geo.distance(a.first.last(), b.first.first()), 0.01)
            assertTrue(b.second > a.second)
        }
        // Driven 7 km: no piece kept behind the car is more than one piece long.
        val kept = pieces.filter { it.second > 7_000.0 }
        assertTrue(kept.first().second - 7_000.0 <= RouteLayers.PIECE_M)
        assertTrue(RouteLayers.piecesJson(route).contains("\"end\":"))
        assertTrue(RouteLayers.pieces(listOf(start)).isEmpty())
    }

    /** 6: the grey driven layers are gone; only the route still ahead is drawn. */
    @Test fun q6_noDrivenLayers() {
        assertFalse(RouteLayers.LAYER_IDS.any { "driven" in it })
        assertEquals(listOf("dm-route-contrast", "dm-route-casing", "dm-route"), RouteLayers.ROUTE_LINE_IDS)
    }

    /** 10: "Smaller, kept off route": boards stand 14 m out on the left verge, clear of the route. */
    @Test fun q10_boardsStandClearOfTheRoute() {
        assertEquals(14.0, ImmersiveMap.BOARD_OFFSET_M, 0.0)
        val boards = ImmersiveMap.countdownBoards(1_000.0, 0.0, ImmersiveMap.Kind.MOTORWAY_EXIT) { Geo.offset(start, 0.0, it) }
        boards.forEach { b ->
            val onRoute = Geo.offset(start, 0.0, Geo.distance(start, LatLon(b.at.lat, start.lon)))
            assertEquals(14.0, Geo.distance(onRoute, b.at), 0.5)
            assertTrue("left of the road", b.at.lon < start.lon)
        }
    }

    /** 12: "Small chip under banner": the chip says the problem in a few words, with Retry beside it. */
    @Test fun q12_chipWording() {
        assertEquals("Map detail unavailable", mapProblemChipText("Map detail unavailable. Retry when you can."))
        assertEquals("Map unavailable", mapProblemChipText("Map unavailable. You can retry."))
        assertEquals("Map not published for this area yet", mapProblemChipText("Map not published for this area yet"))
    }
}
