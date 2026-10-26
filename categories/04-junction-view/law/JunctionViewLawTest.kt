package com.drivemate.tests.cat04

import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

/** Official-source checks only. No register-defined switch-on distance or UI behavior is law. */
class JunctionViewLawTest {
    /** Highway Code 272: countdown markers at 300, 200 and 100 yards before motorway exits. */
    @Test fun motorwayCountdownUsesOfficialYardDistances() {
        assertEquals(listOf(3, 2, 1), ImmersiveMap.COUNTDOWN_M.map { it.first })
        ImmersiveMap.COUNTDOWN_M.zip(listOf(300, 200, 100)).forEach { (marker, yards) ->
            assertEquals(yards * 0.9144, marker.second, 0.00001)
        }
        val origin = LatLon(53.0, -2.0)
        val boards = ImmersiveMap.countdownBoards(1000.0, 0.0, ImmersiveMap.Kind.MOTORWAY_EXIT) { Geo.offset(origin, 0.0, it) }
        assertEquals(3, boards.size)
        boards.forEach { assertTrue(it.motorway); assertTrue(it.at.lon < origin.lon) }
    }
}
