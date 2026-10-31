package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C11.4: large-text settings cannot blow the driving header past its caps. */
class C11DrivingTextCapsTest {
    @Test fun largestFontScaleStaysWithinEachCap() {
        for (scale in listOf(1f, 1.3f, 2f, 3f)) {
            val road = Accessibility.cappedSp(18f, scale, DrivingTextCaps.ROAD) * scale
            val then = Accessibility.cappedSp(17f, scale, DrivingTextCaps.THEN) * scale
            assertTrue("road at $scale", road <= DrivingTextCaps.ROAD + 1e-3f)
            assertTrue("then at $scale", then <= DrivingTextCaps.THEN + 1e-3f)
        }
    }

    @Test fun normalScaleIsUnchangedAndBadScaleIsSafe() {
        assertEquals(18f, Accessibility.cappedSp(18f, 1f, DrivingTextCaps.ROAD), 1e-4f)
        assertEquals(18f, Accessibility.cappedSp(18f, Float.NaN, DrivingTextCaps.ROAD), 1e-4f)
        assertEquals(18f, Accessibility.cappedSp(18f, 0f, DrivingTextCaps.ROAD), 1e-4f)
    }
}
