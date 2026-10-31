package com.drivemate.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C10 diagnostic-only aggregates. These synthetic counters cannot establish that a physical
 * car moved, that a GPS fix was measured, or that MapLibre drew a particular position.
 */
class RenderMetricsVisualDeliveryTest {
    @Test fun separatesComposeDeliveryFromUnreliableAndNonadvancingMapRejections() {
        val metrics = RenderMetrics()
        metrics.composeLocationDelivery()
        metrics.visualAttempt()
        metrics.visualHoldUnreliable()
        metrics.composeLocationDelivery()
        metrics.visualAttempt()
        metrics.visualRejectTimestamp()
        val s = metrics.visualSnapshot()
        assertEquals(2L, s.composeDeliveries)
        assertEquals(2L, s.attempts)
        assertEquals(1L, s.heldUnreliable)
        assertEquals(1L, s.rejectedTimestamp)
        assertEquals(0L, s.submittedToMapLibre)
    }

    @Test fun distinguishesMapUnavailableAndDisabledPointerFromActualSubmission() {
        val metrics = RenderMetrics()
        metrics.visualAttempt()
        metrics.visualNoMap()
        metrics.visualAttempt()
        metrics.visualComponentDisabled()
        metrics.visualAttempt()
        metrics.visualAcceptedAgeNanos(4_000_000L)
        metrics.visualSubmitted()
        val s = metrics.visualSnapshot()
        assertEquals(3L, s.attempts)
        assertEquals(1L, s.noMap)
        assertEquals(1L, s.componentDisabled)
        assertEquals(1L, s.submittedToMapLibre)
        assertEquals(4.0, s.meanInputAgeMs, 0.001)
    }

    @Test fun tracksFutureTimestampsWithoutClaimingFramesAcknowledgePositions() {
        val metrics = RenderMetrics()
        metrics.visualAcceptedAgeNanos(-2_000_000L)
        metrics.visualAcceptedAgeNanos(10_000_000L)
        metrics.visualAcceptedAgeNanos(30_000_000L)
        metrics.frame(1_000_000_000L, true, 4, 3)
        val s = metrics.visualSnapshot()
        assertEquals(1L, s.futureTimestampSamples)
        assertEquals(2L, s.ageSamples)
        assertEquals(20.0, s.meanInputAgeMs, 0.001)
        assertEquals(30.0, s.maxInputAgeMs, 0.001)
        assertEquals(0L, s.submittedToMapLibre)
        assertTrue(s.summary().contains("do not confirm that a position was rendered"))
        assertEquals(1L, metrics.snapshot().completeFrames)
    }

    @Test fun distinguishesFreshSubmissionFromStaleCompletedFrameCallback() {
        val metrics = RenderMetrics()
        metrics.frame(1_000_000_000L, true, 4, 3)
        metrics.visualSubmitted(4_000_000_000L)
        val s = metrics.visualSnapshot(4_100_000_000L)
        assertEquals(3_100.0, s.lastCompleteFrameAgeMs!!, 0.001)
        assertEquals(100.0, s.lastSubmissionAgeMs!!, 0.001)
        assertEquals(1L, s.submittedToMapLibre)
        // This difference is a diagnostic discriminator, not proof of a visual freeze.
        assertTrue(s.summary().contains("complete-frame callback age"))
    }

    @Test fun pausedRendererIgnoresLateCallbacksAndDoesNotInheritOldFreshnessOnResume() {
        val metrics = RenderMetrics()
        metrics.frame(1_000_000_000L, true, 2, 1)
        metrics.visualSubmitted(1_050_000_000L)
        metrics.suspended()
        // An in-flight callback after Activity.onPause is not proof of an active display.
        metrics.frame(2_000_000_000L, true, 2, 1)
        metrics.visualSubmitted(2_050_000_000L)
        val paused = metrics.visualSnapshot(3_000_000_000L)
        assertNull(paused.lastCompleteFrameAgeMs)
        assertNull(paused.lastSubmissionAgeMs)
        assertEquals(2L, paused.submittedToMapLibre)

        metrics.resumed()
        val beforeNewFrame = metrics.visualSnapshot(3_500_000_000L)
        assertNull(beforeNewFrame.lastCompleteFrameAgeMs)
        assertNull(beforeNewFrame.lastSubmissionAgeMs)
        metrics.frame(4_000_000_000L, true, 2, 1)
        metrics.visualSubmitted(4_100_000_000L)
        val resumed = metrics.visualSnapshot(4_200_000_000L)
        assertEquals(200.0, resumed.lastCompleteFrameAgeMs!!, 0.001)
        assertEquals(100.0, resumed.lastSubmissionAgeMs!!, 0.001)
    }

    @Test fun incompleteFramesAndRendererSuspensionDoNotPretendToBeFresh() {
        val metrics = RenderMetrics()
        metrics.frame(1_000_000_000L, false, 2, 1)
        assertNull(metrics.visualSnapshot(2_000_000_000L).lastCompleteFrameAgeMs)
        metrics.frame(2_000_000_000L, true, 2, 1)
        metrics.visualSubmitted(2_050_000_000L)
        metrics.suspended()
        val s = metrics.visualSnapshot(3_000_000_000L)
        assertNull(s.lastCompleteFrameAgeMs)
        assertNull(s.lastSubmissionAgeMs)
        assertEquals(1L, s.submittedToMapLibre)
        assertTrue(s.summary().contains("complete-frame callback age unknown"))
    }

    @Test fun rendererSummaryDisclosesInstanceLifetimeInsteadOfDriveScope() {
        val metrics = RenderMetrics()
        metrics.frame(1_000_000_000L, true, 2, 1)
        val before = metrics.snapshot()
        metrics.frame(2_000_000_000L, true, 2, 1)
        val after = metrics.snapshot()
        // Frame totals accumulate for this renderer; starting another drive does not reset them.
        assertEquals(1L, before.frames)
        assertEquals(2L, after.frames)
        MapRenderDiagnostics.register("phone", metrics)
        val report = MapRenderDiagnostics.summary()
        assertTrue(report.contains("cumulative per renderer instance, not per drive"))
        assertTrue(report.contains("not acknowledgements of individual positions"))
        assertTrue(report.contains("Map frames: 2 (2 complete)"))
    }

    @Test fun activeRendererAwaitingFirstFrameDiffersFromPausedRenderer() {
        val metrics = RenderMetrics()
        val active = metrics.visualSnapshot(1_000_000_000L)
        assertTrue(active.rendererActive)
        assertNull(active.lastCompleteFrameAgeMs)
        assertNull(active.lastSubmissionAgeMs)
        assertTrue(active.summary().contains("Renderer lifecycle active"))

        metrics.suspended()
        val paused = metrics.visualSnapshot(2_000_000_000L)
        assertFalse(paused.rendererActive)
        assertNull(paused.lastCompleteFrameAgeMs)
        assertNull(paused.lastSubmissionAgeMs)
        assertTrue(paused.summary().contains("Renderer lifecycle suspended"))

        metrics.resumed()
        val resumed = metrics.visualSnapshot(3_000_000_000L)
        assertTrue(resumed.rendererActive)
        assertNull(resumed.lastCompleteFrameAgeMs)
        assertNull(resumed.lastSubmissionAgeMs)
    }
}
