package com.drivemate.tests.cat08

import com.drivemate.app.nav.RouteSwitchPolicy
import org.junit.Assert.*
import org.junit.Test

/** Category 8.12 AI-recommended distraction policy; Needs owner review. */
class ReroutingDistractionDecisionsTest {
    @Test fun optionalCongestionChoiceDoesNotInterruptAnImminentManeuverOrUncertainFix() {
        assertFalse(RouteSwitchPolicy.mayPresent(50.0, 10.0, true))
        assertFalse(RouteSwitchPolicy.mayPresent(2_000.0, 10.0, false))
        assertFalse(RouteSwitchPolicy.mayPresent(Double.NaN, 10.0, true))
        assertFalse(RouteSwitchPolicy.mayPresent(2_000.0, Double.POSITIVE_INFINITY, true))
        assertTrue(RouteSwitchPolicy.mayPresent(800.0, 10.0, true))
        assertFalse(RouteSwitchPolicy.mayPresent(1_199.0, 30.0, true))
        assertTrue(RouteSwitchPolicy.mayPresent(1_200.0, 30.0, true))
    }
}
