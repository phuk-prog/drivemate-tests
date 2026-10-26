package com.drivemate.tests.cat07

import com.drivemate.app.nav.*
import com.drivemate.app.data.RouteWeather
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** AI-recommended decisions, all Needs owner review; thresholds are not law. */
class RoadAlertsDecisionsTest {
    @Test fun q7_4_averageNeedsEnoughDistanceAndTime() {
        val z = AverageSpeedZone()
        z.passedCamera(48.28032, 1000)
        z.moved(100.0)
        assertNull(z.averageKmh(10999))
        assertEquals(36.0, z.averageKmh(11000)!!, 0.001)
        z.moved(2401.0)
        assertFalse(z.active)
        assertNull(z.averageKmh(30000))
    }
    @Test fun q7_5_unmeasuredOrInvalidMovementCannotProduceAnAverage() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val z = AverageSpeedZone(); z.passedCamera(50.0, 1000); z.moved(100.0); z.moved(bad)
            assertFalse(z.active); assertNull(z.averageKmh(11000))
            z.passedCamera(50.0, 20000); z.moved(100.0)
            assertEquals(36.0, z.averageKmh(30000)!!, 0.001)
        }
        val z = AverageSpeedZone(); z.passedCamera(50.0, 1000); z.moved(100.0)
        assertNull(z.averageKmh(999)); assertFalse(z.active)
        assertFalse(RoadAlertPolicy.continuousSample(10001, 10.0))
        assertFalse(RoadAlertPolicy.continuousSample(0, 10.0))
        assertFalse(RoadAlertPolicy.continuousSample(1000, 301.0))
        assertTrue(RoadAlertPolicy.continuousSample(10000, 300.0))
    }
    @Test fun q7_3_cameraNeedsRoadAndDirectionEvidence() {
        assertTrue(RoadAlertPolicy.mayPassCamera(10.0,true,true,false,false,null,null))
        assertFalse(RoadAlertPolicy.mayPassCamera(10.0,true,false,false,false,null,null))
        assertFalse(RoadAlertPolicy.mayPassCamera(10.0,false,true,true,true,100.0,100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(10.0,true,true,false,true,100.0,100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(10.0,true,true,true,true,null,100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(10.0,true,true,true,true,200.0,100.0))
        assertTrue(RoadAlertPolicy.mayPassCamera(10.0,true,true,true,true,100.0,100.0))
        assertFalse(RoadAlertPolicy.mayPassCamera(Double.NaN,true,true,true,true,100.0,100.0))
    }
    @Test fun q7_4_unknownLimitRemainsUnknown() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0)) {
            val z = AverageSpeedZone(); z.passedCamera(bad, 1000); assertNull(z.limitKmh)
        }
    }
    @Test fun q7_10_weatherEntryRetainsExistingTimingWithoutInvalidStretches() {
        assertTrue(WeatherWarning.entering(1000.0,2000.0,850.0))
        assertFalse(WeatherWarning.entering(1000.0,2000.0,849.0))
        assertFalse(WeatherWarning.entering(1000.0,2000.0,2000.0))
        assertFalse(WeatherWarning.entering(2000.0,1000.0,1500.0))
        assertFalse(WeatherWarning.entering(0.0,Double.POSITIVE_INFINITY,10.0))
        assertFalse(WeatherWarning.entering(0.0,200.0,-1.0))
    }
    @Test fun q7_11_invalidRouteCannotTriggerForecastLookup() {
        assertTrue(RouteWeather.along(listOf(LatLon(53.0,-2.0),LatLon(53.1,-2.1)),Double.NaN).isEmpty())
        assertTrue(RouteWeather.along(listOf(LatLon(Double.NaN,-2.0),LatLon(53.1,-2.1)),100.0).isEmpty())
        assertNull(RouteWeather.hourAt(JSONObject(),0))
    }
    @Test fun q7_7_retainedBumpWarningsAreShortAndRouteOnly() {
        val bump = com.drivemate.app.data.HazardType.SPEED_BUMP
        assertEquals(120,bump.alertRange)
        assertTrue(bump.navOnly)
        assertEquals(6f,bump.minSpeed,0f)
    }
    @Test fun q7_8_reportExpiryDoesNotDeleteMappedFeatures() {
        val t = com.drivemate.app.data.HazardType.HAZARD
        val h = com.drivemate.app.data.Hazard(1,t,LatLon(53.0,-2.0),1000)
        assertFalse(h.expired(1000 + t.ttlMinutes * 60000L))
        assertTrue(h.expired(1001 + t.ttlMinutes * 60000L))
        assertFalse(h.copy(fixed=true).expired(Long.MAX_VALUE))
    }
    @Test fun q7_11_forecastDoesNotPretendToCoverMissingHours() {
        val h = JSONObject("""{"time":["2026-10-26T10:00"],"weather_code":[45]}""")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val t = fmt.parse("2026-10-26T10:00")!!.time
        assertNull(RouteWeather.hourAt(h,t-1))
        assertNotNull(RouteWeather.hourAt(h,t))
        assertNotNull(RouteWeather.hourAt(h,t+3599999))
        assertNull(RouteWeather.hourAt(h,t+3600000))
    }
}
