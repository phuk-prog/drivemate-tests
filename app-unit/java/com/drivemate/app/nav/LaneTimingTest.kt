package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import com.drivemate.app.data.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneTimingTest {
    @Test fun lanesComeOnWithTheJunctionViewArrowAlways() {
        assertTrue(LaneTiming.lanesDue(990.0, fastRoad = true, spoken = false, onRoundabout = false))
        assertFalse(LaneTiming.lanesDue(1_010.0, fastRoad = true, spoken = false, onRoundabout = false))
        assertTrue(LaneTiming.lanesDue(290.0, fastRoad = false, spoken = false, onRoundabout = false))
        assertFalse(LaneTiming.lanesDue(310.0, fastRoad = false, spoken = false, onRoundabout = false))
        assertTrue("named by the voice: shown", LaneTiming.lanesDue(1_400.0, true, spoken = true, onRoundabout = false))
        assertTrue("arrow always shown (1.5)", LaneTiming.arrowShown(false, "turn", 4_000.0, false, true))
        assertFalse(LaneTiming.arrowShown(true, "turn", 4_000.0, false, true))
        assertTrue(LaneTiming.arrowShown(false, "off ramp", 900.0, false, true))
        assertTrue(LaneTiming.arrowShown(false, "roundabout", 900.0, true, false))
    }

    @Test fun laneDiagramFullFirstThenNarrow() {
        assertEquals(1f, LaneTiming.laneDetail(900.0, true))
        assertEquals(0f, LaneTiming.laneDetail(200.0, true))
        assertEquals(1f, LaneTiming.laneDetail(250.0, false))
        assertEquals(0f, LaneTiming.laneDetail(80.0, false))
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
