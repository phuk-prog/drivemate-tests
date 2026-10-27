package com.drivemate.app.dmn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DMN road pack writer (drivemate-tests tools/dmn_pack.py; owner, 27 Oct 2026: "There is no
 * drive mate navigation use the drivemate test repo") makes packs the app reads and routes on.
 * Fixture: tools/fixtures/dmn-test.osm, written to resources/dmn/dmn-test.dmnpack by the tool.
 * Main Street n1-n2-n3, Side Road n2-n4 (no left turn into it from Main Street), one-way Loop Lane
 * n3-n5-n4 and a private drive n1-n6-n4.
 */
class DmnPackWriterTest {
    private val pack = javaClass.getResourceAsStream("/dmn/dmn-test.dmnpack")!!.use { DmnPack.read(it) }
    private fun edge(a: Long, b: Long) = (0 until pack.edgeCount).single { pack.nodeId!![pack.from[it]] == a && pack.nodeId!![pack.to[it]] == b }
    private fun has(a: Long, b: Long) = (0 until pack.edgeCount).any { pack.nodeId!![pack.from[it]] == a && pack.nodeId!![pack.to[it]] == b }

    @Test fun roadsSpeedsAndAccessAsTheAppExpects() {
        assertEquals(12, pack.edgeCount)
        assertEquals("the footpath is not a car road", 4, pack.wayId.size)
        val main = edge(1, 2)
        assertEquals("Main Street", pack.wayName[pack.way[main]])
        assertEquals("residential at 30 km/h; its 20 mph limit is higher", 30f, pack.kph[main], 0.01f)
        assertEquals(100f, pack.metres[main], 1f)
        assertEquals(DmnRouter.ALLOWED, pack.access[main].toInt())
        val drive = edge(1, 6)
        assertEquals("private: only at the start or end of a trip", DmnRouter.UNKNOWN, pack.access[drive].toInt())
        assertTrue(pack.restricted[drive])
    }

    @Test fun oneWayAndTurnBan() {
        assertTrue(has(3, 5)); assertTrue(has(5, 4))
        assertFalse("one-way: never backwards", has(5, 3) || has(4, 5))
        assertTrue("no left turn from Main Street into Side Road", pack.banned(edge(1, 2), edge(2, 4)))
        assertFalse(pack.banned(edge(1, 2), edge(2, 3)))
    }

    @Test fun routeGoesRoundTheBanOnTheOneWayLoop() {
        val plan = DmnRouter(pack).plan(edge(1, 2), edge(5, 4))
        val nodes = plan.edges.map { pack.nodeId!![pack.to[it]] }
        assertEquals("along Main Street, round Loop Lane; not the banned turn or the private drive", listOf(3L, 5L, 4L), nodes)
    }

    @Test fun metadataNamesTheWriterAndTheCredit() {
        assertTrue(pack.metadata.contains("\"format\": \"DMNPACK2\""))
        assertTrue(pack.metadata.contains("OpenStreetMap contributors"))
        assertTrue(pack.metadata.contains("tools/dmn_pack.py"))
    }
}
