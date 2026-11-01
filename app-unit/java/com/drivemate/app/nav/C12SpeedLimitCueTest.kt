package com.drivemate.app.nav

import com.drivemate.app.data.LimitWay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 12.2 (matching the limit to the segment) and 12.5 (upcoming cues, once each). */
class C12SpeedLimitCueTest {
    private val a = LatLon(53.0, -2.0)
    private val b = Geo.offset(a, 0.0, 100.0)

    @Test fun noMappedLimitStaysUnknown() {
        assertNull(SpeedLimitMatcher.match(listOf(a, b), emptyList()).single())
    }

    @Test fun invalidMappedLimitsAreIgnored() {
        for (kmh in listOf(0.0, -30.0, Double.NaN, 400.0)) {
            assertNull("$kmh", SpeedLimitMatcher.match(listOf(a, b), listOf(LimitWay(listOf(a, b), kmh))).single())
        }
    }

    @Test fun sideStreetAtRightAngleIsNotTheLimit() {
        val sideEnd = Geo.offset(a, 90.0, 100.0)
        val side = LimitWay(listOf(a, sideEnd), 32.0)
        assertNull(SpeedLimitMatcher.match(listOf(a, b), listOf(side)).single())
    }

    @Test fun roadFarAwayIsNotTheLimit() {
        val far = LimitWay(listOf(Geo.offset(a, 270.0, 40.0), Geo.offset(b, 270.0, 40.0)), 32.0)
        assertNull(SpeedLimitMatcher.match(listOf(a, b), listOf(far)).single())
    }

    @Test fun limitAheadIsFoundAfterAChangeAlreadySpoken() {
        // Now 48 km/h; a rise to 64 at 1000 m, then a drop to 32 at 1100 m.
        val limitAt = { d: Double -> when { d < 1_000 -> 48.0; d < 1_100 -> 64.0; else -> 32.0 } }
        val riseSpokenAt = 1_000.0
        // The rise was spoken already; the drop 150 m ahead must still be spoken, not hidden behind it.
        val drop = LimitChangeCue.firstUnspokenChange(950.0, 48.0, 13.0, riseSpokenAt, limitAt)
        assertEquals(LimitChangeCue.Kind.DROP, drop!!.kind)
        assertTrue(LimitChangeCue.shouldSpeak(drop, 950.0, 13.0, false, riseSpokenAt))
        assertFalse(LimitChangeCue.shouldSpeak(drop, 900.0, 13.0, false, riseSpokenAt))
    }
}
