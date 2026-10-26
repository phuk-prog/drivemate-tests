package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import com.drivemate.app.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneTimingTest {
    @Test fun approvedBandsControlLaneOnsetArrowStillAlwaysShown() {
        assertEquals(178.816, LaneTiming.showFromLimit(20 * 1.609344)!!, 0.001)
        assertEquals(2_816.352, LaneTiming.showFromLimit(70 * 1.609344)!!, 0.001)
        val display = LaneDisplay()
        assertFalse(display.due("route", "junction", 180.0, 20 * 1.609344))
        assertTrue(display.due("route", "junction", 178.0, 20 * 1.609344))
        assertTrue("arrow always shown (1.5)", LaneTiming.arrowShown(false, "turn", 4_000.0, false, true))
        assertFalse(LaneTiming.arrowShown(true, "turn", 4_000.0, false, true))
        assertTrue(LaneTiming.arrowShown(false, "off ramp", 900.0, false, true))
        assertTrue(LaneTiming.arrowShown(false, "roundabout", 900.0, true, false))
    }

    @Test fun laneWordsSayWhichLanesToUseNeverAvoid() {
        val threeOfFour = listOf(Lane(listOf("left"), valid = false)) + List(3) { Lane(listOf("straight"), valid = true) }
        assertEquals("Use the right three lanes", Wording.laneWords(threeOfFour))
        val leftOne = listOf(Lane(listOf("left"), valid = true)) + List(2) { Lane(listOf("straight"), valid = false) }
        assertEquals("Use the left lane", Wording.laneWords(leftOne))
        val step = RouteStep("Continue straight", "continue", "straight", "", 500.0, LatLon(0.0, 0.0))
        assertEquals("Use the right three lanes to continue straight", Wording.withLane(step, "Continue straight", Wording.laneWords(threeOfFour)))
    }

    @Test fun weatherWarnedOnEnteringTheStretch() {
        assertFalse("not minutes before", WeatherWarning.entering(5_000.0, 9_000.0, 2_000.0))
        assertTrue(WeatherWarning.entering(5_000.0, 9_000.0, 4_900.0))
        assertTrue("starting a trip inside it", WeatherWarning.entering(5_000.0, 9_000.0, 6_000.0))
        assertFalse("after it", WeatherWarning.entering(5_000.0, 9_000.0, 9_100.0))
    }
}
