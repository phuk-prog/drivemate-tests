package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.2: the GPX export holds only the measured positions of a recorded journey. */
class C22JourneyGpxTest {
    private val t = 1_800_000_000_000L
    private fun sample(atMs: Long, lat: Double, accuracy: Double = 4.0) =
        EvidenceSample(EvidenceFix(LatLon(lat, -2.2), atMs, accuracy, null), emptyList())

    @Test fun pointsAreMeasuredOnlyInTimeOrder() {
        val journey = JourneyEvidence("$t", t, t + 9_000, listOf(
            sample(t + 2_000, 53.42),
            sample(t + 1_000, 53.41),
            sample(t + 3_000, Double.NaN),   // not a usable position
            sample(t + 4_000, 53.43, accuracy = -1.0),   // invalid accuracy
        ))
        val gpx = requireNotNull(JourneyGpx.build(journey))
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.indexOf("53.410000") < gpx.indexOf("53.420000"))
        assertFalse(gpx.contains("NaN"))
        assertFalse(gpx.contains("<rte"))
        assertFalse(gpx.contains("<wpt"))
    }

    @Test fun timesAreUtcIso8601() {
        val journey = JourneyEvidence("$t", t, null, listOf(sample(t + 1_000, 53.41)))
        val gpx = requireNotNull(JourneyGpx.build(journey))
        assertTrue(Regex("<time>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z</time>").containsMatchIn(gpx))
    }

    @Test fun stopsBecomeWaypointsInTheExport() {
        val samples = (0..8).map { sample(t + it * 15_000L, 53.40) }
        val gpx = requireNotNull(JourneyGpx.build(JourneyEvidence("$t", t, null, samples)))
        assertEquals(1, Regex("<wpt ").findAll(gpx).count())
        assertTrue(gpx.contains("<name>Stop 1</name>"))
        assertTrue(gpx.indexOf("<wpt ") < gpx.indexOf("<trk>"))
    }

    @Test fun journeyWithNoUsablePositionGivesNoFile() {
        assertNull(JourneyGpx.build(JourneyEvidence("$t", t, null, emptyList())))
    }

    @Test fun limitedRecordingSaysSo() {
        val journey = JourneyEvidence("$t", t, null, listOf(sample(t + 1_000, 53.41)), captureLimited = true)
        assertTrue(requireNotNull(JourneyGpx.build(journey)).contains("storage limit"))
    }

    @Test fun exportIsBoundedToTheRecordingLimit() {
        val samples = (0 until 800).map { sample(t + it * 15_000L, 53.4 + it * 1e-5) }
        val gpx = requireNotNull(JourneyGpx.build(JourneyEvidence("$t", t, null, samples)))
        assertEquals(JourneyGpx.MAX_POINTS, Regex("<trkpt ").findAll(gpx).count())
    }
}
