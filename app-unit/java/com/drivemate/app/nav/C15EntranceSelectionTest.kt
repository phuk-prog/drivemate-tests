package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** C15.2: the chosen entrance is a verified reachable one on a known side; no coordinate-only guess. */
class C15EntranceSelectionTest {
    private val pin = LatLon(53.0, -2.1)

    private fun entrance(id: String, lat: Double, lon: Double, side: String = "left", road: Double = 5.0, roadId: String = "way/1") =
        EntranceCandidate(id, LatLon(lat, lon), roadId, road, side)

    @Test fun choosesTheNearestReachableEntrance() {
        val far = entrance("node/1", 53.0010, -2.1000)
        val near = entrance("node/2", 53.0002, -2.1000)
        assertEquals("node/2", EntranceSelection.choose(listOf(far, near), pin)?.id)
    }

    @Test fun aNearerEntranceWithUnknownSideIsSkipped() {
        val unknown = entrance("node/3", 53.0001, -2.1000, side = "unknown")
        val reachable = entrance("node/4", 53.0010, -2.1000)
        assertEquals("node/4", EntranceSelection.choose(listOf(unknown, reachable), pin)?.id)
    }

    @Test fun anEntranceFartherThanTheRoadMarginIsNotOnThatRoad() {
        val detached = entrance("node/5", 53.0001, -2.1000, road = 55.0)
        assertNull(EntranceSelection.choose(listOf(detached), pin))
    }

    @Test fun anEntranceWithoutARoadIdIsNotReachable() {
        val noRoad = entrance("node/6", 53.0001, -2.1000, roadId = " ")
        assertNull(EntranceSelection.choose(listOf(noRoad), pin))
    }

    @Test fun noCandidatesMeansNoEntrance() {
        assertNull(EntranceSelection.choose(emptyList(), pin))
    }

    @Test fun aNonFiniteRoadDistanceIsRejected() {
        val bad = entrance("node/7", 53.0001, -2.1000, road = Double.NaN)
        assertNull(EntranceSelection.choose(listOf(bad), pin))
    }
}
