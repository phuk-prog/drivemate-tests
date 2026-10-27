package com.drivemate.tests.cat03

import com.drivemate.app.data.LaneDb
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LatLon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Source-backed, frozen, PUBLIC Manchester OSM-derived geometries, not invented
 * streets or manoeuvres. The source digest is from the published UK-join square
 * audited by public run 40570774089. This tests LaneDb interpretation only:
 * OSM tags are NOT independently verified UK road markings or a legal lane
 * recommendation; no route, NavUi or visible strip is asserted here.
 */
class PublishedManchesterLaneDbDecisionsTest {
    private val source = "cat3-published-manchester-lanes-3.json"
    private val expectedSquareSha = "dcaff102f855531e6bce98b77d8a2068684296590f20c032240a4eb8521e5685"
    private fun json(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(source)) {
            "Frozen source-backed Manchester lane fixture missing"
        }
        return JSONObject(stream.bufferedReader().use { it.readText() })
    }
    private fun way(id: Long): JSONObject {
        val ways = json().getJSONArray("ways")
        for (i in 0 until ways.length()) {
            val w = ways.getJSONObject(i)
            if (w.getLong("id") == id) return w
        }
        error("Published OSM way $id absent")
    }
    private fun point(pair: org.json.JSONArray) = LatLon(pair.getDouble(1), pair.getDouble(0))
    private fun positionAndBearing(id: Long): Pair<LatLon, Double> {
        val geometry = way(id).getJSONArray("g")
        val a = point(geometry.getJSONArray(0))
        val b = point(geometry.getJSONArray(1))
        return Geo.interpolate(a, b, 0.5) to Geo.bearing(a, b)
    }
    private fun db() = LaneDb.parse(listOf(json().toString()))

    @Test fun q3_publishedManchesterWaysAreBoundToTheVerifiedUKJoinSquare() {
        val root = json()
        assertEquals(1, root.getInt("schema"))
        assertEquals(expectedSquareSha, root.getString("source_sha256"))
        assertEquals(3, root.getJSONArray("ways").length())
        assertEquals("left;through|through", way(930354).getJSONObject("t").getString("turn:lanes"))
        assertEquals("through|through|through;right|right",
            way(4419709).getJSONObject("t").getString("turn:lanes"))
    }

    @Test fun q3_publishedGreatAncoatsLaneArrowsParseWithActualRoadBearing() {
        val (at, heading) = positionAndBearing(930354)
        val straight = requireNotNull(db().lanesAt(at, heading, "straight"))
        assertEquals(listOf(listOf("left", "straight"), listOf("straight")),
            straight.map { it.indications })
        assertEquals(listOf(0, 1), LanePolicy.usable(straight))
        assertTrue(LanePolicy.reliable(straight))
        val left = requireNotNull(db().lanesAt(at, heading, "left"))
        assertEquals(listOf(0), LanePolicy.usable(left))
    }

    @Test fun q3_publishedTraffordFourLaneSourcePreservesRoadOrderAndRightArrows() {
        val (at, heading) = positionAndBearing(4419709)
        val right = requireNotNull(db().lanesAt(at, heading, "right"))
        assertEquals(4, right.size)
        assertEquals(listOf(2, 3), LanePolicy.usable(right))
        assertTrue(LanePolicy.reliable(right))
        assertEquals(listOf(0, 1, 2), LanePolicy.usable(
            requireNotNull(db().lanesAt(at, heading, "straight"))))
    }

    @Test fun q3_publishedCarringtonCountContradictionIsUnknownNotInventedArrows() {
        // Published source has lanes=3 but only two turn:lanes entries.
        // The parser must refuse the contradictory layout, not manufacture a third arrow.
        val (at, heading) = positionAndBearing(4542409)
        assertEquals("3", way(4542409).getJSONObject("t").getString("lanes"))
        assertEquals("through|right", way(4542409).getJSONObject("t").getString("turn:lanes"))
        assertNull(db().lanesAt(at, heading, "right"))
    }

    @Test fun q3_publishedOneWayCannotSupplyWrongWayLaneAdvice() {
        val (at, heading) = positionAndBearing(930354)
        assertNull(db().lanesAt(at, (heading + 180.0) % 360.0, "left"))
    }
}
