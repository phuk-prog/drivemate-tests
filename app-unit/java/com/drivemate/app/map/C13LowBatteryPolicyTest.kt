package com.drivemate.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 13 (owner Yes): low-battery mode caps head redraws at 2 per second, and the metrics record the mode. */
class C13LowBatteryPolicyTest {
    private val ms = 1_000_000L

    @Test fun normalModeRedrawsEveryProgressChange() {
        assertEquals(0L, LowBatteryRenderPolicy.minRedrawIntervalMs(false))
        assertTrue(LowBatteryRenderPolicy.redrawDue(100 * ms, 100 * ms + 1, false))
    }

    @Test fun batterySaverCapsRedrawsAtTwoPerSecond() {
        assertEquals(500L, LowBatteryRenderPolicy.minRedrawIntervalMs(true))
        assertFalse(LowBatteryRenderPolicy.redrawDue(1_000 * ms, 1_499 * ms, true))
        assertTrue(LowBatteryRenderPolicy.redrawDue(1_000 * ms, 1_500 * ms, true))
    }

    @Test fun theFirstRedrawAndABackwardsClockAlwaysDraw() {
        assertTrue(LowBatteryRenderPolicy.redrawDue(0L, 5 * ms, true))
        assertTrue(LowBatteryRenderPolicy.redrawDue(2_000 * ms, 1_000 * ms, true))
    }

    @Test fun metricsRecordTheModeAndCountSkippedRedraws() {
        val m = RenderMetrics()
        m.batterySaver(true)
        m.redrawSkipped(); m.redrawSkipped()
        val s = m.snapshot()
        assertTrue(s.batterySaverOn)
        assertEquals(2L, s.skippedRedraws)
        assertTrue(s.summary().contains("low-battery mode on, 2 redraws skipped"))
    }

    @Test fun metricsDefaultToNormalMode() {
        val s = RenderMetrics().snapshot()
        assertFalse(s.batterySaverOn)
        assertEquals(0L, s.skippedRedraws)
        assertTrue(s.summary().contains("low-battery mode off, 0 redraws skipped"))
    }
}
