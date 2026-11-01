package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** Register 12.2: what the speed-limit sign shows when our map, your correction and other sources meet. */
class C12LimitComparisonShownTest {

    @Test fun unknownLimitStaysUnknown() {
        assertEquals(ShownLimit(null, false), LimitComparison.shown(null, false, null, null))
        assertEquals(ShownLimit(null, false), LimitComparison.shown(null, true, null, null))
    }

    @Test fun invalidValuesFromEverySourceCountAsUnknown() {
        for (bad in listOf(0.0, -30.0, Double.NaN, 400.0)) {
            assertEquals("map $bad", ShownLimit(null, false), LimitComparison.shown(bad, false, null, null))
            assertEquals("tomtom $bad", ShownLimit(null, false), LimitComparison.shown(null, false, bad, null))
            assertEquals("national $bad", ShownLimit(null, false), LimitComparison.shown(null, false, null, bad))
        }
    }

    @Test fun correctionBeatsTheMapValueAndTomTomWithNoQuestionMark() {
        // TomTom disagrees with the map by 20 km/h, but your correction is the sign.
        assertEquals(ShownLimit(50.0, false), LimitComparison.shown(50.0, true, 30.0, null))
    }

    @Test fun withoutACorrectionTomTomDisagreementIsShownWithAQuestionMark() {
        assertEquals(ShownLimit(30.0, true), LimitComparison.shown(50.0, false, 30.0, null))
    }

    @Test fun correctionKeepsTheMapValueWhenTomTomAgrees() {
        assertEquals(ShownLimit(50.0, false), LimitComparison.shown(50.0, true, 51.0, null))
    }

    @Test fun temporaryLimitStillAppliesOverACorrection() {
        // Roadworks at 20 km/h are a live legal limit, so they beat the corrected 50 km/h, with a "?".
        assertEquals(ShownLimit(20.0, true), LimitComparison.shown(50.0, true, null, 20.0))
    }
}
