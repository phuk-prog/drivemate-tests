package com.drivemate.app.car

import com.drivemate.app.data.Lane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Category 21.2 pure check: which lane label the car screen shows over a lane arrow. Only branches
 * that do not depend on lane-indication wording are asserted here. It does not draw a template or
 * prove the head unit shows the label; that needs the DHU run (21.6).
 */
class C21CarLaneMarkerTest {
    @Test fun q21_2_closedLaneIsMarkedClosed() {
        assertEquals("×", carLaneMarker(Lane(listOf("straight"), valid = true, closed = true)))
    }

    @Test fun q21_2_busLaneIsMarkedBus() {
        assertEquals("BUS", carLaneMarker(Lane(listOf("straight"), valid = true, bus = true)))
    }

    @Test fun q21_2_unknownAccessIsMarkedUnknown() {
        assertEquals("?", carLaneMarker(Lane(listOf("straight"), valid = true, accessKnown = false)))
    }

    @Test fun q21_2_restrictionTextIsShownAsIs() {
        assertEquals("Buses only", carLaneMarker(Lane(listOf("straight"), valid = true, restriction = "Buses only")))
    }

    @Test fun q21_2_closedWinsOverBusAndRestriction() {
        assertEquals("×", carLaneMarker(Lane(emptyList(), valid = true, bus = true, closed = true, restriction = "x")))
    }

    @Test fun q21_2_laneWithNoIndicationIsUnknownNotInvented() {
        assertEquals("?", carLaneMarker(Lane(emptyList(), valid = true)))
    }
}
