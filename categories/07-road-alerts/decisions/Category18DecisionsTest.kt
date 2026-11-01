package com.drivemate.tests.cat07

import com.drivemate.app.data.Hazard
import com.drivemate.app.data.HazardType
import com.drivemate.app.data.RouteWeather
import com.drivemate.app.data.WeatherKind
import com.drivemate.app.nav.LatLon
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

    @Test fun q18_2_hazardFreshnessFollowsItsTypeAndFixedReportsNeverAge() {
        val p = LatLon(53.0, -2.0)
        assertFalse(Hazard(1, HazardType.OBJECT, p, 0).expired(90 * 60_000L))
        assertTrue(Hazard(1, HazardType.OBJECT, p, 0).expired(90 * 60_000L + 1))
        assertFalse("a pothole report lasts a week", Hazard(2, HazardType.POTHOLE, p, 0).expired(7L * 24 * 60 * 60_000L))
        assertFalse(Hazard(3, HazardType.OBJECT, p, 0, fixed = true).expired(Long.MAX_VALUE))
    }

    @Test fun q18_3_alertGatingPropertiesKeepSecondaryNoticesOutOfDriving() {
        assertTrue(HazardType.SPEED_BUMP.navOnly)
        assertTrue(HazardType.SCHOOL.navOnly)
        assertFalse(HazardType.OBJECT.navOnly)
        assertEquals(6f, HazardType.SPEED_BUMP.minSpeed, 0f)
        assertEquals(0f, HazardType.OBJECT.minSpeed, 0f)
        assertFalse(HazardType.SPEED_BUMP.reportable)
    }

    @Test fun q18_5_directedHazardsApplyOnlyToTheirOwnWayOfTravel() {
        val p = LatLon(53.0, -2.0)
        val eastbound = Hazard(4, HazardType.OBJECT, p, 0, bearing = 90.0)
        assertTrue(eastbound.appliesTo(null))
        assertTrue(eastbound.appliesTo(90.0))
        assertTrue(eastbound.appliesTo(40.0))
        assertFalse(eastbound.appliesTo(270.0))
        val northish = Hazard(5, HazardType.OBJECT, p, 0, bearing = 350.0)
        assertTrue("wraps across north", northish.appliesTo(10.0))
        assertFalse(northish.appliesTo(200.0))
        assertTrue("undirected reports apply both ways", Hazard(6, HazardType.OBJECT, p, 0).appliesTo(270.0))
    }
}
