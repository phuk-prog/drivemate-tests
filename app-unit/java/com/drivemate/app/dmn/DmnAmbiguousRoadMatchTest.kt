package com.drivemate.app.dmn

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** M03 synthetic two-carriageway tests: illustrative geometry, not legal UK road evidence. */
class DmnAmbiguousRoadMatchTest {
    /** Two parallel east-west OSM ways about nine metres apart; each has both travel directions. */
    private fun router(secondAllowed: Boolean = true) = DmnRouter(
        DmnPack(
            lat = doubleArrayOf(53.40000, 53.40000, 53.40008, 53.40008),
            lon = doubleArrayOf(-2.2000, -2.1990, -2.2000, -2.1990),
            nodeId = longArrayOf(1L, 2L, 3L, 4L),
            wayId = longArrayOf(101L, 202L),
            wayRoundabout = booleanArrayOf(false, false),
            wayName = arrayOf("Main carriageway", "Parallel road"),
            wayRef = arrayOf("", ""),
            wayHighway = arrayOf("residential", "residential"),
            from = intArrayOf(0, 1, 2, 3),
            to = intArrayOf(1, 0, 3, 2),
            metres = FloatArray(4) { 67f },
            kph = FloatArray(4) { 30f },
            access = byteArrayOf(0, 0, if (secondAllowed) 0.toByte() else 2.toByte(), if (secondAllowed) 0.toByte() else 2.toByte()),
            startAccess = ByteArray(4),
            finishAccess = ByteArray(4),
            restricted = BooleanArray(4),
            way = intArrayOf(0, 0, 1, 1),
            banPairs = LongArray(0),
            sequences = emptyList(),
            uturnNode = BooleanArray(4),
            metadata = "synthetic ambiguity",
        )
    )

    @Test fun halfwayBetweenParallelRoadsIsUnknown() {
        val point = LatLon(53.40004, -2.1995)
        val r = router()
        assertNull("No invented road when both fit", r.snapForMatch(point, 90f, 6.0))
        assertNull("Unknown heading cannot resolve two ways", r.snapForMatch(point, null, 6.0))
    }

    @Test fun uniqueDirectionalNearRoadIsAccepted() {
        val point = LatLon(53.400005, -2.1995)
        val r = router()
        val result = r.snapForMatch(point, 90f, 5.0)
        assertNotNull(result)
        assertEquals(0, r.pack.way[result!!])
    }

    @Test fun largerPositionErrorWidensAmbiguityInsteadOfInventingAChoice() {
        val point = LatLon(53.400005, -2.1995)
        assertNull(router().snapForMatch(point, 90f, 25.0))
    }

    @Test fun closedOtherWayCannotBeUsedAsEvidenceForAParallelRoad() {
        val point = LatLon(53.40004, -2.1995)
        val r = router(secondAllowed = false)
        val selected = r.snapForMatch(point, 90f, 6.0)
        assertNotNull(selected)
        assertEquals(0, r.pack.way[selected!!])
    }

    @Test fun observableMatchingDoesNotChangeRoutePlanningSnap() {
        val point = LatLon(53.40004, -2.1995)
        val r = router()
        assertNull(r.snapForMatch(point, 90f, 6.0))
        assertNotNull("Route endpoint planning retains the original snap", r.snap(point, 90f))
    }

    @Test fun invalidAccuracyCannotAssertAnObservedRoad() {
        val r = router()
        assertNull(r.snapForMatch(LatLon(53.4, -2.1995), 90f, Double.NaN))
        assertNull(r.snapForMatch(LatLon(53.4, -2.1995), 90f, 100.0))
    }

    @Test fun repeatedObservationsDoNotChangeConfidenceOrInventRoads() {
        val graph = router()
        val ambiguous = LatLon(53.40004, -2.1995)
        val unique = LatLon(53.400005, -2.1995)
        // Overlapping GPS windows repeatedly reference precisely the same fix.
        repeat(25) {
            assertNull(graph.snapForMatch(ambiguous, 90f, 6.0))
            assertEquals(0, graph.pack.way[graph.snapForMatch(unique, 90f, 5.0)!!])
            assertNull("Large reported accuracy must remain unknown even after a confident fix was cached",
                graph.snapForMatch(unique, 90f, 25.0))
        }
        // A different heading is not permitted to reuse the eastbound match.
        val westbound = graph.snapForMatch(unique, 270f, 5.0)
        if (westbound != null) assertEquals(0, graph.pack.way[westbound])
    }
}
