package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C15.3–C15.5: spoken limit changes ahead — drops early, rises late, never over a turn, once each. */
class C15LimitChangeCueTest {
    // Limit 48 km/h (30 mph) until 1,000 m along the route, then 32 km/h (20 mph); 64 km/h after 2,000 m.
    private val limitAt = { d: Double -> when { d < 1_000 -> 48.0; d < 2_000 -> 32.0; else -> 64.0 } }

    @Test fun aDropAheadIsFoundAtItsChangePoint() {
        val c = LimitChangeCue.firstChange(900.0, 48.0, 13.0, limitAt)!!
        assertEquals(LimitChangeCue.Kind.DROP, c.kind)
        assertEquals(32.0, c.limitKmh, 0.0)
        assertEquals(1_000.0, c.atM, LimitChangeCue.SCAN_STEP_M)
    }

    @Test fun dropsAreSpokenAboutTenSecondsAhead() {
        val speed = 13.0 // 130 m in 10 s, so the 150 m minimum applies
        val far = LimitChangeCue.Change(LimitChangeCue.Kind.DROP, 32.0, 1_000.0)
        assertFalse(LimitChangeCue.shouldSpeak(far, 800.0, speed, false, null))
        assertTrue(LimitChangeCue.shouldSpeak(far, 860.0, speed, false, null))
    }

    @Test fun risesAreSpokenOnlyOnceTheChangePointIsCrossed() {
        // 12.3: no early cue to speed up. The rise at 2000 m is spoken only after the driver is past it.
        val rise = LimitChangeCue.Change(LimitChangeCue.Kind.RISE, 64.0, 2_000.0)
        assertFalse("not before the change point", LimitChangeCue.shouldSpeak(rise, 1_990.0, 9.0, false, null))
        assertTrue(LimitChangeCue.shouldSpeak(rise, 2_005.0, 9.0, false, null))
        val riseLimit = { d: Double -> if (d < 2_000) 48.0 else 64.0 }
        assertNull("nothing ahead to speak early", LimitChangeCue.firstUnspokenChange(1_950.0, 48.0, 9.0, null, riseLimit))
        assertNull("not yet crossed", LimitChangeCue.crossedRise(1_990.0, null, riseLimit))
        assertEquals(64.0, LimitChangeCue.crossedRise(2_005.0, null, riseLimit)!!.limitKmh, 0.0)
        assertEquals("Speed limit rises to 40", LimitChangeCue.phrase(rise, "40"))
    }

    @Test fun neverTalksOverAnImminentTurnAndSaysEachChangeOnce() {
        val drop = LimitChangeCue.Change(LimitChangeCue.Kind.DROP, 32.0, 1_000.0)
        assertFalse(LimitChangeCue.shouldSpeak(drop, 900.0, 13.0, turnImminent = true, lastSpokenAtM = null))
        assertFalse("already spoken for this point", LimitChangeCue.shouldSpeak(drop, 950.0, 13.0, false, 1_010.0))
        assertTrue("a different, later change is spoken", LimitChangeCue.shouldSpeak(drop, 950.0, 13.0, false, 400.0))
    }

    @Test fun unknownLimitsOrTinyDifferencesAreNotChanges() {
        assertNull(LimitChangeCue.firstChange(900.0, null, 13.0, limitAt))
        assertNull(LimitChangeCue.firstChange(900.0, 48.0, 13.0) { 50.0 })
        assertNull("unknown ahead stays unknown", LimitChangeCue.firstChange(900.0, 48.0, 13.0) { null })
        assertNull("nothing within the horizon", LimitChangeCue.firstChange(100.0, 48.0, 13.0, limitAt))
    }
}
