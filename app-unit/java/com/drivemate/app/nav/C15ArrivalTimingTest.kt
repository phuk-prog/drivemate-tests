package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C15.3: arrival follows the measured position on the route, never speed alone or an estimate. */
class C15ArrivalTimingTest {
    @Test fun notArrivedEarlyFromTheMeasuredPositionAlone() {
        assertFalse("30 m left is not the end", ArrivalPolicy.arrived(30.0, 5.0, 40.0, false))
        assertTrue("29.9 m left on the route is", ArrivalPolicy.arrived(29.9, 5.0, 40.0, false))
        assertFalse("off route by 25 m is not the end", ArrivalPolicy.arrived(10.0, 25.0, 40.0, false))
        assertFalse("150 m from the pin is not the destination", ArrivalPolicy.arrived(150.0, 2.0, 10.0, false))
    }

    @Test fun notDeclaredLateWhileTheMeasuredPositionIsSafe() {
        assertTrue(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false))
        assertTrue(ArrivalPolicy.arrived(149.9, 2.0, 10.0, false))
    }

    @Test fun estimatesAndUnsafeFixesNeverDeclareArrival() {
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, estimating = true))
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, estimating = false, positionSafe = false))
        assertFalse(ArrivalPolicy.arrived(Double.POSITIVE_INFINITY, 0.0, 0.0, false))
    }
}
