package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C11.3/C11.4: essential driving controls stay, banners stay compact and readable. */
class C11DrivingBarPolicyTest {
    @Test fun soundAndTheTripMenuOrDoneAreAlwaysAvailable() {
        for (warnings in listOf(0, 3)) for (following in listOf(true, false)) {
            val driving = drivingControls(arrived = false, warningsAhead = warnings, following = following)
            assertTrue(DrivingControl.SOUND in driving)
            assertTrue(DrivingControl.MENU in driving)
            assertFalse(DrivingControl.DONE in driving)
            assertEquals("recentre only when not following", !following, DrivingControl.RECENTER in driving)
            assertEquals("warnings chip only with something ahead", warnings > 0, DrivingControl.WARNINGS in driving)
            val arrived = drivingControls(arrived = true, warningsAhead = warnings, following = following)
            assertTrue(DrivingControl.SOUND in arrived && DrivingControl.DONE in arrived)
            assertFalse(DrivingControl.MENU in arrived || DrivingControl.WARNINGS in arrived)
        }
    }

    @Test fun hazardChipNamesTheNearestAndStaysOneLine() {
        assertNull(aheadLabel(emptyList()))
        assertEquals("Speed camera in 400 yd", aheadLabel(listOf("Speed camera" to "400 yd")))
        assertEquals("Roadworks in 1.2 mi · +2 more",
            aheadLabel(listOf("Roadworks" to "1.2 mi", "Camera" to "2 mi", "Closure" to "3 mi")))
    }

    @Test fun everyArrivalColourIsReadableOnTheDayAndNightBar() {
        val day = 0xFFFFFFFF
        val night = 0xFF1E2127
        for (level in DelayLevel.values()) {
            delayColour(level, night = false)?.let { assertTrue("$level day", contrastRatio(it, day) >= 4.5) }
            delayColour(level, night = true)?.let { assertTrue("$level night", contrastRatio(it, night) >= 4.5) }
        }
        assertNull("no live traffic is never shown as reassuring green", delayColour(DelayLevel.UNKNOWN, false))
    }

    @Test fun colourIsNeverTheOnlyClue() {
        for (level in listOf(DelayLevel.NONE, DelayLevel.SOME, DelayLevel.SERIOUS))
            assertNotNull(level.name, delayNote(level, 15 * 60.0, closureAhead = false))
        assertEquals("Road closure on route", delayNote(delayLevel(null, true), null, closureAhead = true))
        assertEquals(DelayLevel.UNKNOWN, delayLevel(Double.NaN, false))
    }

    @Test fun parkingIsOfferedOnlyNearTheTripEnd() {
        assertTrue(nearTripEnd(1_000.0, 600.0))
        assertTrue("3 km but under 3 minutes", nearTripEnd(2_800.0, 170.0))
        assertFalse(nearTripEnd(2_800.0, 400.0))
        assertFalse("too far", nearTripEnd(3_100.0, 60.0))
        assertFalse("arriving", nearTripEnd(100.0, 20.0))
        assertFalse(nearTripEnd(Double.NaN, 60.0))
    }
}
