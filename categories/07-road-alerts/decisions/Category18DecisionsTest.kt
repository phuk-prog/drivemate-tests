package com.drivemate.tests.cat07

import com.drivemate.app.data.RouteWeather
import com.drivemate.app.data.WeatherKind
import com.drivemate.app.nav.RoadAlertPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 18 decisions: only driving-relevant weather, exact camera-pass and sample boundaries. */
class Category18DecisionsTest {
    @Test fun q18_3_weatherIsWarnedOnlyForDrivingRisks() {
        assertNull(RouteWeather.classify(0, 0.0, 10_000.0, 15.0, 0.0))
        assertEquals(WeatherKind.SNOW, RouteWeather.classify(73, 0.0, 10_000.0, -2.0, 0.0))
        assertEquals(WeatherKind.ICE, RouteWeather.classify(3, 0.2, 10_000.0, 0.5, 0.0))
        assertEquals(WeatherKind.FOG, RouteWeather.classify(45, 0.0, 10_000.0, 8.0, 0.0))
        assertEquals(WeatherKind.FOG, RouteWeather.classify(0, 0.0, 299.0, 8.0, 0.0))
        assertNull(RouteWeather.classify(0, 0.0, 300.0, 8.0, 0.0))
        assertEquals(WeatherKind.RAIN, RouteWeather.classify(61, 4.0, 10_000.0, 8.0, 0.0))
        assertNull(RouteWeather.classify(61, 3.9, 10_000.0, 8.0, 0.0))
        assertEquals(WeatherKind.WIND, RouteWeather.classify(0, 0.0, 10_000.0, 8.0, 50.0))
        assertNull(RouteWeather.classify(0, 0.0, 10_000.0, 8.0, 49.9))
    }

    @Test fun q18_2_cameraPassBoundariesAreExact() {
        assertTrue(RoadAlertPolicy.mayPassCamera(34.9, true, true, false, false, null, null))
        assertFalse(RoadAlertPolicy.mayPassCamera(35.0, true, true, false, false, null, null))
        assertTrue(RoadAlertPolicy.mayPassCamera(0.0, true, true, true, true, 135.0, 100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(0.0, true, true, true, true, 136.0, 100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(Double.POSITIVE_INFINITY, true, true, true, true, 100.0, 100.0))
    }

    @Test fun q18_1_speedSamplesAreBoundedInTimeAndDistance() {
        assertFalse(RoadAlertPolicy.continuousSample(0, 10.0))
        assertTrue(RoadAlertPolicy.continuousSample(1, 0.0))
        assertTrue(RoadAlertPolicy.continuousSample(10_000, 300.0))
        assertFalse(RoadAlertPolicy.continuousSample(10_001, 1.0))
        assertFalse(RoadAlertPolicy.continuousSample(5_000, 300.1))
    }
}
