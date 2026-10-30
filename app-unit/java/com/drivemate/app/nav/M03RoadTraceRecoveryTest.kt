package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M03 regression checks for stale road-match history and sensor confidence.
 * Artificial samples verify code behaviour, not independent UK-road truth.
 */
class M03RoadTraceRecoveryTest {
    @Test fun longGpsOutageRequiresANewContiguousRoadTrace() {
        val max = RoadNetworkMatch.MAX_CONSECUTIVE_GAP_MS
        assertFalse(RoadNetworkMatch.traceBroken(1_000L, 1_000L + max))
        assertTrue(RoadNetworkMatch.traceBroken(1_000L, 1_001L + max))
        assertTrue(RoadNetworkMatch.traceBroken(3_000L, 2_000L))
        assertTrue(RoadNetworkMatch.traceBroken(3_000L, 3_000L))
    }

    @Test fun fourNewestFixesGiveSameVerdictAsFifteen() {
        // Verdict consumes three latest locations and one predecessor for bearing.
        val all = (0 until RoadNetworkMatch.MAX_FIXES).map { i ->
            RoadNetworkMatch.Matched(LatLon(53.4000, -2.2000 + i * 0.00001), 101L, 2.0)
        }
        assertEquals(4, RoadNetworkMatch.VERDICT_TRACE_FIXES)
        val recent = all.takeLast(RoadNetworkMatch.VERDICT_TRACE_FIXES)
        assertEquals(RoadNetworkMatch.verdict(all, 5.0, 40.0) { _, _ -> true },
            RoadNetworkMatch.verdict(recent, 5.0, 40.0) { _, _ -> true })
        assertEquals(RoadNetworkMatch.verdict(all, 5.0, 40.0) { _, _ -> false },
            RoadNetworkMatch.verdict(recent, 5.0, 40.0) { _, _ -> false })
        assertEquals(RoadNetworkMatch.Verdict.OFF_ROUTE,
            RoadNetworkMatch.verdict(recent, 5.0, 40.0) { _, _ -> false })
    }

    @Test fun malformedCarSpeedCannotMakeBadGpsSpeedReliable() {
        val lead = LeadEstimator()
        val moved = lead.lead(1_000_000_000L, 1_500_000_000L,
            12.0, 20.0, Double.NaN, headingAgrees = true)
        assertEquals(0.0, moved, 0.001)
        assertFalse(lead.reliable)
        val negativeCar = LeadEstimator()
        assertEquals(0.0, negativeCar.lead(1_000_000_000L, 1_500_000_000L,
            12.0, 20.0, -5.0, headingAgrees = true), 0.001)
        assertFalse(negativeCar.reliable)
    }

    @Test fun invalidReportedSpeedAccuracyFailsClosedButAbsentAccuracyIsAllowed() {
        assertFalse(LatencyCompensation.speedReliable(12.0, Double.NaN))
        assertFalse(LatencyCompensation.speedReliable(12.0, Double.POSITIVE_INFINITY))
        assertFalse(LatencyCompensation.speedReliable(12.0, -1.0))
        assertTrue(LatencyCompensation.speedReliable(12.0, null))
        val car = LeadEstimator()
        assertTrue(car.lead(1_000_000_000L, 1_500_000_000L,
            12.0, 20.0, 10.0, headingAgrees = true) > 0.0)
        assertTrue(car.reliable)
    }
}
