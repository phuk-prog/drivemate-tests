package com.drivemate.app.nav

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Category 7.1: the sourced UK matching cohort is frozen. Ways are the archived OSM-derived
 * observations in provenance/manchester-lanes; each case is one recorded fix. Outcomes are the
 * matcher's behaviour when the fixture was recorded. Synthetic fixes, no real drive.
 */
class Cat7MatchingCohortTest {
    private val fixtureBytes: ByteArray by lazy {
        javaClass.classLoader!!.getResourceAsStream("cat7-matching-cohort.json")!!.use { it.readBytes() }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun fixture() = JSONObject(String(fixtureBytes, Charsets.UTF_8))

    private fun segmentsOf(d: JSONObject): List<LocalRoadMatcher.Segment> {
        val out = ArrayList<LocalRoadMatcher.Segment>()
        val ways = d.getJSONArray("ways")
        for (i in 0 until ways.length()) {
            val w = ways.getJSONObject(i)
            val id = w.getLong("id").toString()
            val t = w.getJSONObject("t")
            val tags = t.keySet().associateWith { t.getString(it) }
            if (!LocalRoadMatcher.drivable(tags)) continue
            val g = w.getJSONArray("g")
            for (j in 0 until g.length() - 1) {
                val a = g.getJSONArray(j)
                val b = g.getJSONArray(j + 1)
                out += LocalRoadMatcher.Segment(id, LatLon(a.getDouble(1), a.getDouble(0)), LatLon(b.getDouble(1), b.getDouble(0)),
                    tags["name"] ?: "", LocalRoadMatcher.oneWay(tags))
            }
        }
        return out
    }

    @Test fun fixtureBytesAreFrozen() {
        assertEquals(FIXTURE_SHA256, sha256(fixtureBytes))
    }

    @Test fun cohortIsTiedToTheArchivedSourceDigest() {
        val d = fixture()
        assertEquals(SOURCE_SHA256, d.getString("source_sha256"))
        assertEquals(12, d.getJSONArray("ways").length())
        assertEquals(d.getJSONArray("ways").length(), d.getJSONArray("cases").length())
    }

    @Test fun everyCaseReproducesItsRecordedOutcome() {
        val d = fixture()
        val segments = segmentsOf(d)
        val cases = d.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val f = c.getJSONObject("fix")
            val fix = LocalRoadMatcher.Fix(LatLon(f.getDouble("lat"), f.getDouble("lon")), f.getDouble("heading"),
                f.getDouble("speed_mps"), f.getDouble("accuracy_m"), f.getLong("elapsed_ms"))
            val match = LocalRoadMatcher().match(fix, segments)
            val outcome = when {
                match == null -> "unknown"
                match.roadId == c.getLong("intended_way_id").toString() -> "correct"
                else -> "fail"
            }
            assertEquals(c.getString("id"), c.getString("observed"), outcome)
        }
    }

    @Test fun sourcedCohortCoversTheKindsItClaims() {
        val kinds = fixture().getJSONArray("cases").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("kind") }.toSet() }
        for (kind in listOf("slip_road", "bridge_overpass", "near_other_road", "split_carriageway_piece", "urban_trunk")) {
            assertTrue("missing kind $kind", kind in kinds)
        }
    }

    @Test fun absentCategoriesStayUnknownAndUnfabricated() {
        val d = fixture()
        val absent = d.getJSONArray("not_in_sourced_data")
        val kinds = (0 until absent.length()).map { absent.getJSONObject(it).getString("kind") }.toSet()
        assertEquals(setOf("roundabout", "tunnel", "stopped_signal", "opposite_carriageway"), kinds)
        for (i in 0 until absent.length()) assertEquals("unknown", absent.getJSONObject(i).getString("outcome"))
        val cases = d.getJSONArray("cases")
        for (i in 0 until cases.length()) assertFalse(cases.getJSONObject(i).getString("kind") in kinds)
    }

    private companion object {
        const val FIXTURE_SHA256 = "9b1f71e83ca10eb54a79e78429c98eaf25b88fb05e07d62fc071c2b0f237cffd"
        const val SOURCE_SHA256 = "0ab0238e89416df54c6a889d0d6a4fd74c3d1ac47e65f0f50a5d36fc2adcdc13"
    }
}
