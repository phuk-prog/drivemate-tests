package com.drivemate.app.dmn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Category 2.1 — a closed barrier (bollard) on the one-way Loop Lane.
 * Fixture: tools/fixtures/dmn-barrier.osm, the DMN test fixture with a bollard
 * on Loop Lane between n3 and n5 (pack: app-unit/resources/dmn/dmn-barrier.dmnpack).
 * The router must not pass through a closed barrier, must still route up to it,
 * and must refuse the only other way round (a private drive, which is allowed
 * only at the start or end of a trip).
 */
class DmnBarrierRoutingTest {
    private val pack = javaClass.getResourceAsStream("/dmn/dmn-barrier.dmnpack")!!.use { DmnPack.read(it) }
    private fun edge(a: Long, b: Long) =
        (0 until pack.edgeCount).single { pack.nodeId!![pack.from[it]] == a && pack.nodeId!![pack.to[it]] == b }

    @Test fun barrierSplitsTheOneWayLoopIntoTwoPieces() {
        assertEquals(13, pack.edgeCount)
        assertTrue(pack.nodeId!!.contains(9L))
        assertTrue(pack.banned(edge(1, 2), edge(2, 4)))
    }

    @Test fun routeUpToTheBarrierStillPlans() {
        val plan = DmnRouter(pack).plan(edge(1, 2), edge(3, 9))
        assertEquals("the last piece reached is the one ending at the barrier", edge(3, 9), plan.edges.last())
    }

    @Test fun routeThroughTheClosedBarrierIsRefusedNotPlanned() {
        try {
            DmnRouter(pack).plan(edge(1, 2), edge(5, 4))
            fail("the router planned through a closed barrier")
        } catch (refused: DmnRouter.NoRoute) {
            assertTrue(refused.message.orEmpty().isNotEmpty())
        }
    }
}
