package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C15.3: arrival follows the measured position on the route, never speed alone or an estimate. */
class C15ArrivalTimingTest {
    @Test fun notArrivedEarlyFromTheMeasuredPositionAlone() {
        assertFalse("30 m left is not the end", ArrivalPolicy.arrived(30.0, 5.0, 40.0, false, speedMps = 0.0))
        assertTrue("29.9 m left on the route is", ArrivalPolicy.arrived(29.9, 5.0, 40.0, false, speedMps = 0.0))
        assertFalse("off route by 25 m is not the end", ArrivalPolicy.arrived(10.0, 25.0, 40.0, false, speedMps = 0.0))
        assertFalse("150 m from the pin is not the destination", ArrivalPolicy.arrived(150.0, 2.0, 10.0, false, speedMps = 0.0))
    }

    @Test fun notDeclaredLateWhileTheMeasuredPositionIsSafe() {
        assertTrue(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = 0.0))
        assertTrue(ArrivalPolicy.arrived(149.9, 2.0, 10.0, false, speedMps = 0.0))
    }

    @Test fun estimatesAndUnsafeFixesNeverDeclareArrival() {
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, estimating = true, speedMps = 0.0))
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, estimating = false, positionSafe = false, speedMps = 0.0))
        assertFalse(ArrivalPolicy.arrived(Double.POSITIVE_INFINITY, 0.0, 0.0, false, speedMps = 0.0))
    }

    @Test fun drivingFasterThanFiveKmhDoesNotDeclareArrival() {
        // 5 km/h is 1.39 m/s; the car must be below it.
        assertFalse("at the pin at 30 mph", ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = 13.4))
        assertFalse("at 5 km/h exactly", ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = 5.0 / 3.6))
        assertTrue("just under 5 km/h", ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = 5.0 / 3.6 - 0.01))
    }

    @Test fun anUnknownSpeedDoesNotDeclareArrival() {
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = Double.NaN))
        assertFalse(ArrivalPolicy.arrived(0.0, 0.0, 0.0, false, speedMps = Double.POSITIVE_INFINITY))
    }
}
