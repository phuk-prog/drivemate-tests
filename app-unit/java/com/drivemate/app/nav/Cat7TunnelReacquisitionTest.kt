package com.drivemate.app.nav

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Category 7.4 tunnel reacquisition on real OpenStreetMap tunnel ways (Greater Manchester extract,
 * drivemate-map run 41314664440, artifact 13086594248). Each way is driven along its centreline
 * at 8 m/s with a 1 Hz fix stream, then the fixes stop for 13 s (about 104 m, a tunnel), then resume.
 * The fixes are synthetic and noise-free. Candidates are only the tunnel ways in the archive:
 * surface roads are not archived, so parallel-surface confusion is NOT tested here.
 * Outcome after the gap: unknown (no match), correct (the driven way) or fail (another way).
 */
class Cat7TunnelReacquisitionTest {
    private val fixtureBytes: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("cat7-tunnel-ways.json")!!.use { it.readBytes() }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

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

    private fun segmentsOf(all: List<Way>): List<LocalRoadMatcher.Segment> = all
        .filter { LocalRoadMatcher.drivable(it.tags) }
        .flatMap { w -> (0 until w.nodes.size - 1).map { j -> LocalRoadMatcher.Segment(w.id, w.nodes[j], w.nodes[j + 1], w.tags["name"] ?: "", LocalRoadMatcher.oneWay(w.tags)) } }

    /** Position and bearing [s] metres along [path]. */
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

    @Test fun fixtureBytesAreFrozen() {
        assertEquals(FIXTURE_SHA256, sha256(fixtureBytes))
    }

    @Test fun reacquisitionAfterATunnelGapNeverPicksAnotherWay() {
        val all = ways()
        val segments = segmentsOf(all)
        val longEnough = all.filter { w ->
            LocalRoadMatcher.drivable(w.tags) && w.nodes.size >= 2 &&
                (0 until w.nodes.size - 1).sumOf { Geo.distance(w.nodes[it], w.nodes[it + 1]) } >= MIN_LENGTH_M
        }
        assertTrue("no tunnel way long enough to drive the scenario", longEnough.isNotEmpty())
        var correct = 0
        var unknown = 0
        val fails = ArrayList<String>()
        for (way in longEnough) {
            val path = if (way.tags["oneway"] == "-1") way.nodes.reversed() else way.nodes
            val matcher = LocalRoadMatcher()
            var wrongBeforeGap = false
            var resolved = false
            // Pre-tunnel k=1..5 (8 to 40 m), gap k=6..18 (no fixes), post-tunnel k=19..23 (152 to 184 m).
            for (k in 1..23) {
                if (k in 6..18) continue
                val s = SPEED_MPS * k
                val (at, bearing) = pointAt(path, s)
                val match = matcher.match(LocalRoadMatcher.Fix(at, bearing, SPEED_MPS, ACCURACY_M, 1_000L * k), segments)
                if (k <= 5) {
                    if (match != null && match.roadId != way.id) wrongBeforeGap = true
                    continue
                }
                if (resolved) continue
                when {
                    match == null -> { unknown++; resolved = true }
                    match.roadId == way.id -> { correct++; resolved = true }
                    else -> { fails += "way ${way.id} reacquired on ${match.roadId}"; resolved = true }
                }
            }
            if (wrongBeforeGap) fails += "way ${way.id} matched another way before the gap"
        }
        println("CAT7_TUNNEL ways=${longEnough.size} correct=$correct unknown=$unknown fail=${fails.size} ${fails.joinToString("; ")}")
        assertEquals("wrong-road reacquisitions: $fails", 0, fails.size)
        assertTrue("nothing was measured", correct + unknown > 0)
    }

    private companion object {
        const val FIXTURE_SHA256 = "403f4e6def4a5ceb5e1f351683e137689c48765fb769bdbbc8f5bdbcff2acbb1"
        const val MIN_LENGTH_M = 200.0
        const val SPEED_MPS = 8.0
        const val ACCURACY_M = 8.0
    }
}
