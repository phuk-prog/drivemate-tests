package com.drivemate.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 13 (owner Yes): FPS and p95 callback interval, measured from counts only, never invented. */
class C13RenderCadenceTest {
    private val frameNs = 16_000_000L

    @Test fun p95IsNearestRankAndIgnoresNonFiniteValues() {
        assertEquals(19.0, RenderMetrics.p95((1..20).map { it.toDouble() })!!, 1e-9)
        assertEquals(7.0, RenderMetrics.p95(listOf(7.0, Double.NaN, -1.0))!!, 1e-9)
        assertNull(RenderMetrics.p95(emptyList()))
    }

    @Test fun fpsIsOneThousandOverTheMeanIntervalAndZeroWithoutOne() {
        assertEquals(50.0, RenderMetrics.fpsFromMeanInterval(20.0), 1e-9)
        assertEquals(0.0, RenderMetrics.fpsFromMeanInterval(0.0), 1e-9)
        assertEquals(0.0, RenderMetrics.fpsFromMeanInterval(Double.NaN), 1e-9)
    }

    @Test fun steadySixtyHertzFramesGiveAboutSixtyFpsAndP95OfTheInterval() {
        val m = RenderMetrics()
        for (i in 0 until 61) m.frame(1_000_000_000L + i * frameNs, complete = true, drawCalls = 1, activeTextures = 1)
        val s = m.snapshot()
        assertEquals(60L, s.cadenceSamples)
        assertEquals(62.5, s.meanFps, 1e-6)
        assertEquals(16.0, s.p95CallbackIntervalMs!!, 1e-6)
        assertTrue(s.summary().contains("p95 16.0 ms"))
    }

    @Test fun aLongStallIsAGapNotAFrameInterval() {
        val m = RenderMetrics()
        m.frame(1_000_000_000L, true, 1, 1)
        m.frame(1_000_000_000L + frameNs, true, 1, 1)
        m.frame(1_000_000_000L + frameNs + 400_000_000L, true, 1, 1)
        val s = m.snapshot()
        assertEquals(1L, s.longGaps)
        assertEquals(1L, s.cadenceSamples)
        assertEquals(16.0, s.p95CallbackIntervalMs!!, 1e-6)
        assertEquals(62.5, s.meanFps, 1e-6)
    }
}
