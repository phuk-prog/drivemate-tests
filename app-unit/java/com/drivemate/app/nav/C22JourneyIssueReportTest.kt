package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 22.3/22.4: the drive issue report keeps measured values apart from estimates and names what it leaves out. */
class C22JourneyIssueReportTest {
    private val t = 1_800_000_000_000L
    private fun sample(atMs: Long, lat: Double, accuracy: Double = 5.0) =
        EvidenceSample(EvidenceFix(LatLon(lat, -2.2), atMs, accuracy, null), emptyList())

    @Test fun measuredAndEstimatedValuesAreSeparate() {
        val journey = JourneyEvidence("$t", t, t + 300_000, listOf(sample(t, 53.40), sample(t + 15_000, 53.41)))
        val r = requireNotNull(JourneyIssueReport.build(journey))
        val measured = r.indexOf("Measured (from the saved GPS fixes)")
        val estimated = r.indexOf("Estimated (derived, not measured directly)")
        assertTrue(measured >= 0 && estimated > measured)
        assertFalse(r.substring(0, estimated).contains("Distance:"))
        assertTrue(r.substring(estimated).contains("Distance: 1.1 km, straight lines between fixes"))
    }

    @Test fun countsAndGapsAreMeasured() {
        val journey = JourneyEvidence("$t", t, t + 300_000, listOf(
            sample(t, 53.40, 5.0), sample(t + 15_000, 53.401, 60.0), sample(t + 135_000, 53.402, 8.0)))
        val r = requireNotNull(JourneyIssueReport.build(journey))
        assertTrue(r.contains("Fixes kept: 3"))
        assertTrue(r.contains("fixes worse than 50 m: 1"))
        assertTrue(r.contains("Gaps with no fix over 60 s: 1; longest 2 min"))
    }

    @Test fun stopsAreCountedAsMeasured() {
        val samples = (0..8).map { sample(t + it * 15_000L, 53.40) }
        val r = requireNotNull(JourneyIssueReport.build(JourneyEvidence("$t", t, t + 120_000, samples)))
        assertTrue(r.contains("Stops (within 25 m for at least 1 min): 1; total 2 min"))
    }

    @Test fun partialDriveIsSaid() {
        val journey = JourneyEvidence("$t", t, null, listOf(sample(t, 53.40)))
        assertTrue(requireNotNull(JourneyIssueReport.build(journey)).contains("did not end normally"))
    }

    @Test fun limitedRecordingIsSaid() {
        val journey = JourneyEvidence("$t", t, t + 1, listOf(sample(t, 53.40)), captureLimited = true)
        assertTrue(requireNotNull(JourneyIssueReport.build(journey)).contains("storage limit"))
    }

    @Test fun noUsableFixGivesNoReport() {
        assertNull(JourneyIssueReport.build(JourneyEvidence("$t", t, null, emptyList())))
    }

    @Test fun reportIsShortAndNamesWhatIsLeftOut() {
        val samples = (0 until 800).map { sample(t + it * 15_000L, 53.4 + it * 1e-5) }
        val r = requireNotNull(JourneyIssueReport.build(JourneyEvidence("$t", t, t + 1, samples)))
        assertTrue(r.length < JourneyIssueReport.MAX_CHARS)
        assertTrue(r.contains("Not included: planned route, destination"))
        assertTrue(r.contains("This report makes no comparison."))
    }
}
