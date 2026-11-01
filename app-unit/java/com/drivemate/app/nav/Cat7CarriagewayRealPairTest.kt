package com.drivemate.app.nav

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 7.3 on real OpenStreetMap geometry: the two one-way carriageways of Wilmslow Road
 * tunnel (ways w4083129 and w4083130, bearings about 322 and 159 degrees, from the Greater
 * Manchester extract in cat7-tunnel-ways.json). The matcher must follow heading and one-way
 * direction: a car on one carriageway never snaps to the other carriageway while it travels
 * the other way. Synthetic noise-free fixes on the centreline; no verified turn restrictions
 * are used because none are archived for these ways.
 */
class Cat7CarriagewayRealPairTest {
    private val fixtureBytes: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("cat7-tunnel-ways.json")!!.use { it.readBytes() }
    }

    private class Way(val id: String, val tags: Map<String, String>, val nodes: List<LatLon>)

    private fun ways(): List<Way> {
        val arr = JSONObject(String(fixtureBytes, Charsets.UTF_8)).getJSONArray("ways")
        return (0 until arr.length()).map { i ->
            val w = arr.getJSONObject(i)
            val t = w.getJSONObject("t")
            val tags = t.keys().asSequence().map { it.toString() }.associateWith { t.getString(it) }
            val g = w.getJSONArray("g")
            Way(w.getString("id"), tags, (0 until g.length()).map { j -> LatLon(g.getJSONArray(j).getDouble(1), g.getJSONArray(j).getDouble(0)) })
        }
    }

    private val all by lazy { ways() }

    private fun segments(): List<LocalRoadMatcher.Segment> = all
        .filter { LocalRoadMatcher.drivable(it.tags) }
        .flatMap { w -> (0 until w.nodes.size - 1).map { j -> LocalRoadMatcher.Segment(w.id, w.nodes[j], w.nodes[j + 1], w.tags["name"] ?: "", LocalRoadMatcher.oneWay(w.tags)) } }

    private fun way(id: String) = all.first { it.id == id }

    private fun pointAt(path: List<LatLon>, s: Double): Pair<LatLon, Double> {
        var left = s
        for (j in 0 until path.size - 1) {
            val d = Geo.distance(path[j], path[j + 1])
            if (left <= d || j == path.size - 2) {
                val t = if (d == 0.0) 0.0 else (left / d).coerceIn(0.0, 1.0)
                return Geo.interpolate(path[j], path[j + 1], t) to Geo.bearing(path[j], path[j + 1])
            }
            left -= d
        }
        return path.last() to 0.0
    }

    private fun length(path: List<LatLon>) = (0 until path.size - 1).sumOf { Geo.distance(path[it], path[it + 1]) }

    /** Matched road ids along a drive at [headingOffset] degrees from the way bearing, 20 m apart. */
    private fun drive(w: Way, headingOffset: Double): List<String?> {
        val matcher = LocalRoadMatcher()
        val segs = segments()
        val out = ArrayList<String?>()
        val total = length(w.nodes)
        var s = 20.0
        var t = 1_000L
        while (s <= total - 20.0) {
            val (at, bearing) = pointAt(w.nodes, s)
            val heading = (bearing + headingOffset + 360.0) % 360.0
            out += matcher.match(LocalRoadMatcher.Fix(at, heading, 12.0, 8.0, t), segs)?.roadId
            s += 20.0
            t += 1_000L
        }
        return out
    }

    @Test fun realCarriagewayPairIsTwoOneWayWays() {
        val a = way("w4083129")
        val b = way("w4083130")
        assertEquals(1, LocalRoadMatcher.oneWay(a.tags))
        assertEquals(1, LocalRoadMatcher.oneWay(b.tags))
        assertTrue(LocalRoadMatcher.drivable(a.tags) && LocalRoadMatcher.drivable(b.tags))
    }

    @Test fun drivingAlongACarriagewayStaysOnThatCarriageway() {
        for (id in listOf("w4083129", "w4083130")) {
            val ids = drive(way(id), 0.0)
            assertTrue("no fix matched on $id", ids.any { it != null })
            for (r in ids) assertTrue("$id drove onto $r", r == null || r == id)
        }
    }

    @Test fun drivingTheOtherWayNeverSnapsOntoTheCarriagewayYouAreNotOn() {
        for (id in listOf("w4083129", "w4083130")) {
            val ids = drive(way(id), 180.0)
            for (r in ids) assertTrue("$id opposite drive matched $id itself", r != id)
        }
    }
}
