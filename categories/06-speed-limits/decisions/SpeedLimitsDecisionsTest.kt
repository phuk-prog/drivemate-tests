package com.drivemate.tests.cat06

import com.drivemate.app.data.LimitWay
import com.drivemate.app.data.RoadAlerts
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

/** AI-selected category 6 decisions. Every decision remains Needs owner review. */
class SpeedLimitsDecisionsTest {
    @Test fun q6_4_warningUsesUnroundedMeasuredSpeed() {
        val f = Format(true)
        assertTrue(f.isOverBy((32.2 * 0.44704).toFloat(), 30 * 1.609344, 2))
        assertFalse(f.isOverBy((31.8 * 0.44704).toFloat(), 30 * 1.609344, 2))
        assertTrue(Format(false).isOverBy((52.1 / 3.6).toFloat(), 50.0, 2))
        assertFalse(Format(false).isOverBy((51.9 / 3.6).toFloat(), 50.0, 2))
    }
    @Test fun q6_5_invalidSpeedOrLimitNeverWarns() {
        val f = Format(true)
        for (speed in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertFalse(f.isOverBy(speed, 30.0, 0)); assertFalse(f.isSpeeding(speed, 30.0))
        }
        for (limit in listOf(Double.NaN, Double.POSITIVE_INFINITY, -30.0, 0.0)) {
            assertFalse(f.isOverBy(40f, limit, 0)); assertFalse(f.isSpeeding(40f, limit))
        }
    }
    @Test fun q6_3_dialAdjustmentNeverChangesTrueSpeed() {
        assertEquals(20f, SpeedShown.metresPerSecond(20f, null, 0)!!, 0f)
        assertEquals(22f, SpeedShown.metresPerSecond(20f, null, 10)!!, 0.00001f)
        assertNull(SpeedShown.metresPerSecond(Float.NaN, null, 0))
    }
    @Test fun q6_7_unresolvedAndMalformedLimitsStayUnknown() {
        for (raw in listOf("GB:urban", "UK:urban", "30 @ (school_hours)", "30;50", "-20", "none", "signals", "0", "999", "foo30", "gb:other:nsl_single")) {
            assertNull(raw, RoadAlerts.parseSpeed(raw))
        }
        assertEquals(32.5, RoadAlerts.parseSpeed("32.5 km/h")!!, 0.00001)
    }
    @Test fun q6_8_invalidSourcesCannotOverrideKnownLimit() {
        val shown = LimitComparison.shown(48.28032, false, Double.NaN, Double.NEGATIVE_INFINITY)
        assertEquals(48.28032, shown.kmh!!, 0.00001); assertFalse(shown.unsure)
        assertNull(LimitComparison.shown(-1.0, false, Double.POSITIVE_INFINITY, 0.0).kmh)
        assertFalse(LimitComparison.disagree(-10.0, 40.0))
    }
    @Test fun q6_9_temporaryLowerLimitStillOverridesCorrection() {
        val shown = LimitComparison.shown(70 * 1.609344, true, 60 * 1.609344, 40 * 1.609344)
        assertEquals(40 * 1.609344, shown.kmh!!, 0.00001); assertTrue(shown.unsure)
    }
    @Test fun q6_10_conflictingParallelRoadsRemainUnknown() {
        val a = LatLon(53.0, -2.0)
        val b = Geo.offset(a, 0.0, 100.0)
        val leftA = Geo.offset(a, 270.0, 1.0); val leftB = Geo.offset(b, 270.0, 1.0)
        val rightA = Geo.offset(a, 90.0, 1.0); val rightB = Geo.offset(b, 90.0, 1.0)
        assertNull(SpeedLimitMatcher.match(listOf(a,b), listOf(LimitWay(listOf(leftA,leftB),32.0), LimitWay(listOf(rightA,rightB),64.0))).single())
        assertEquals(32.0, SpeedLimitMatcher.match(listOf(a,b), listOf(LimitWay(listOf(leftA,leftB),32.0))).single()!!, 0.0001)
    }
}
