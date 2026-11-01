package com.drivemate.tests.cat04

import com.drivemate.app.data.LaneDb
import com.drivemate.app.nav.Geo
import com.drivemate.app.nav.JunctionPlan
import com.drivemate.app.nav.LatLon
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 17.5 source to junction layout: the frozen, public Manchester OSM-derived way (the same fixture
 * as category 3) gives lanes, and the junction plan shows only those lanes or nothing. The tags are
 * not verified road markings; no 3D or screen behaviour is asserted here.
 */
class Category17SourceLayoutTest {
    private val source = "cat3-published-manchester-lanes-3.json"
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
    private fun point(pair: JSONArray) = LatLon(pair.getDouble(1), pair.getDouble(0))

    @Test fun q17_5_publishedSourceLanesGiveAStraightJunctionLayoutOrNothing() {
        val g = way(930354).getJSONArray("g")
        val a = point(g.getJSONArray(0))
        val b = point(g.getJSONArray(1))
        val lanes = requireNotNull(LaneDb.parse(listOf(json().toString())).lanesAt(Geo.interpolate(a, b, 0.5), Geo.bearing(a, b), "straight"))
        assertEquals(2, lanes.size)
        val plan = requireNotNull(JunctionPlan.build("turn", "straight", true, lanes, lanes.size, null, null, "", "A665", "Manchester", fastRoad = false))
        assertTrue("a straight-on source gives a straight layout", plan.straight)
        assertTrue("highlights stay on the source lanes", plan.highlighted.all { it in lanes.indices })
        assertNull("no source lanes and no count give no layout", JunctionPlan.build("turn", "straight", true, emptyList(), null, null, null, "", "A665", "Manchester", fastRoad = false))
    }
}
