package com.drivemate.app.nav

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 7.3 access eligibility (owner decision): ways a car may not use as a public road
 * (access, motorcar, motor_vehicle or vehicle set to private, permit or customers) are never
 * snapped to. Pure rule checks plus a pin of the eligible count on the real Greater Manchester
 * tunnel fixture (cat7-tunnel-ways.json).
 */
class Cat7AccessEligibilityTest {
    private fun tags(vararg kv: Pair<String, String>) = mapOf("highway" to "residential") + kv.toMap()

    @Test fun privatePermitAndCustomersAreNotSnapped() {
        for (v in listOf("private", "permit", "customers")) {
            assertFalse("access=$v", LocalRoadMatcher.drivable(tags("access" to v)))
            assertFalse("motor_vehicle=$v", LocalRoadMatcher.drivable(tags("motor_vehicle" to v)))
        }
        assertFalse(LocalRoadMatcher.drivable(tags("motorcar" to "private")))
    }

    @Test fun publicAndDestinationAccessStayEligible() {
        assertTrue(LocalRoadMatcher.drivable(tags()))
        assertTrue(LocalRoadMatcher.drivable(tags("access" to "yes")))
        assertTrue(LocalRoadMatcher.drivable(tags("access" to "permissive")))
        assertTrue(LocalRoadMatcher.drivable(tags("access" to "destination")))
    }

    @Test fun tunnelFixtureEligibleCountIsPinned() {
        val bytes = javaClass.classLoader!!.getResourceAsStream("cat7-tunnel-ways.json")!!.use { it.readBytes() }
        val ways = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("ways")
        var eligible = 0
        for (i in 0 until ways.length()) {
            val t = ways.getJSONObject(i).getJSONObject("t")
            val map = t.keys().asSequence().map { it.toString() }.associateWith { t.getString(it) }
            if (LocalRoadMatcher.drivable(map)) eligible++
        }
        assertEquals(317, eligible)
    }
}
