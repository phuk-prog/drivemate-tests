package com.drivemate.app.nav

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Register 12 (owner Yes): verified limits show source and date; driver is told before a verified decrease. */
class C12VerifiedSpeedLimitTest {
    private val src = LimitLegalSource("GOV.UK speed limits", "https://www.gov.uk/speed-limits", LocalDate.of(2026, 11, 1))
    private val v30 = SpeedLimitRecord(48.0, src)
    private val v20 = SpeedLimitRecord(32.0, src)
    private val unverified = SpeedLimitRecord(32.0, null)

    @Test fun mapOnlyLimitIsNeverVerified() {
        assertFalse(VerifiedSpeedLimit.isVerified(unverified))
        assertFalse(VerifiedSpeedLimit.isVerified(null))
        assertEquals("Unverified map limit", VerifiedSpeedLimit.sourceLine(unverified))
    }

    @Test fun verifiedLimitShowsSourceAndDate() {
        assertTrue(VerifiedSpeedLimit.isVerified(v20))
        assertEquals("Verified: GOV.UK speed limits, updated 2026-11-01", VerifiedSpeedLimit.sourceLine(v20))
    }

    @Test fun verifiedDecreaseIsNotifiedButUnverifiedOrRiseOrTinyIsNot() {
        assertTrue(VerifiedSpeedLimit.notifyBeforeDecrease(v30, v20))
        assertFalse(VerifiedSpeedLimit.notifyBeforeDecrease(v30, unverified))
        assertFalse(VerifiedSpeedLimit.notifyBeforeDecrease(v20, v30))
        assertFalse(VerifiedSpeedLimit.notifyBeforeDecrease(v30, SpeedLimitRecord(46.0, src)))
        assertFalse(VerifiedSpeedLimit.notifyBeforeDecrease(null, v20))
    }

    @Test fun warningWording() {
        assertEquals("Verified speed limit drops to 20", VerifiedSpeedLimit.decreaseWarning("20"))
    }
}
