package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Category 8.1 (roundabout exit numbering and route-step identity): an ordinal is spoken only when
 * the routing step carries an exit number; without one the roundabout step says "Enter the roundabout".
 * The step that leaves the ring has its own wording, so it never repeats the entry instruction.
 */
class Category8ExitNumberingTest {
    @Test fun mappedExitNumberIsSpokenAsOrdinal() {
        assertEquals("At the roundabout, take the third exit onto Mercian Way",
            TurnInstructions.describe("roundabout", "right", "Mercian Way", 3, lastLeg = false))
    }

    @Test fun unmappedExitIsNeverInvented() {
        assertEquals("Enter the roundabout",
            TurnInstructions.describe("roundabout", "right", "", 0, lastLeg = false))
    }

    @Test fun leavingTheRingIsADistinctStep() {
        val enter = TurnInstructions.describe("roundabout", "right", "Mercian Way", 3, lastLeg = false)
        val leave = TurnInstructions.describe("exit roundabout", "right", "Mercian Way", 0, lastLeg = false)
        assertEquals("Exit the roundabout onto Mercian Way", leave)
        assertNotEquals(enter, leave)
    }
}
