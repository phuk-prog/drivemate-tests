package com.drivemate.app.nav

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 12.4 and 12.5: the marker and countdown for the limit ahead; verified decreases are announced as verified. */
class C12LimitAheadMarkerTest {
    private val src = LimitLegalSource("GOV.UK speed limits", "https://www.gov.uk/speed-limits", LocalDate.of(2026, 11, 1))
    // 48 km/h to 1,000 m, then 32 km/h.
    private val limitAt = { d: Double -> if (d < 1_000) 48.0 else 32.0 }
    private val unverified = { _: Double -> null as SpeedLimitRecord? }

    @Test fun nextBoundaryHasDistanceAndCountdown() {
        val v = LimitAheadMarker.next(900.0, 48.0, 10.0, limitAt, unverified)!!
        assertEquals(LimitChangeCue.Kind.DROP, v.kind)
        assertEquals(1_000.0, v.atM, LimitChangeCue.SCAN_STEP_M)
        assertEquals(10, v.secondsAway)
    }

    @Test fun stoppedCarHasNoCountdown() {
        assertNull(LimitAheadMarker.secondsAway(100.0, 0.2))
    }

    @Test fun unverifiedLimitIsLabelledWithQuestionMarkAndNeverVerified() {
        val v = LimitAheadMarker.next(900.0, 48.0, 10.0, limitAt, unverified)!!
        assertFalse(v.verified)
        assertEquals("▼ 20 in 10 s ?", LimitAheadMarker.label(v, "20"))
    }

    @Test fun verifiedLimitHasNoQuestionMark() {
        val v = LimitAheadMarker.next(900.0, 48.0, 10.0, limitAt) { SpeedLimitRecord(32.0, src) }!!
        assertTrue(v.verified)
        assertEquals("▼ 20 in 10 s", LimitAheadMarker.label(v, "20"))
    }

    @Test fun unknownAheadGivesNoMarker() {
        assertNull(LimitAheadMarker.next(900.0, 48.0, 10.0, { null }, unverified))
    }

    @Test fun verifiedDecreaseIsAnnouncedAsVerifiedOnlyWhenVerified() {
        assertEquals("Verified speed limit drops to 20", VerifiedSpeedLimit.decreaseWarning("20"))
        assertTrue(VerifiedSpeedLimit.notifyBeforeDecrease(SpeedLimitRecord(48.0, null), SpeedLimitRecord(32.0, src)))
        assertFalse(VerifiedSpeedLimit.notifyBeforeDecrease(SpeedLimitRecord(48.0, null), SpeedLimitRecord(32.0, null)))
    }
}
