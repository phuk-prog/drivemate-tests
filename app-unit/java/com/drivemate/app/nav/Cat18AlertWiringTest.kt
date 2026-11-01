package com.drivemate.app.nav

import com.drivemate.app.data.HazardType
import com.drivemate.app.nav.HazardAlertPolicy.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 18.4: which hazards count as urgent (the only ones spoken), checked for every hazard type. */
class Cat18AlertWiringTest {
    @Test fun everyHazardTypeHasAFixedSeverity() {
        val urgent = HazardType.values().filter { HazardAlertPolicy.severityOf(it) == Severity.URGENT }.toSet()
        assertEquals(setOf(HazardType.CLOSURE, HazardType.CRASH, HazardType.LEVEL_CROSSING,
            HazardType.ANIMALS, HazardType.ANIMAL_SIGN, HazardType.OBJECT), urgent)
        assertEquals(setOf(HazardType.SPEED_BUMP, HazardType.SCHOOL, HazardType.TOLL_BOOTH,
            HazardType.SHARP_BEND, HazardType.POTHOLE),
            HazardType.values().filter { HazardAlertPolicy.severityOf(it) == Severity.INFO }.toSet())
    }

    @Test fun onlyUrgentHazardTypesAreSpoken() {
        for (t in HazardType.values()) {
            assertEquals(t.name, HazardAlertPolicy.severityOf(t) == Severity.URGENT, HazardAlertPolicy.speak(HazardAlertPolicy.severityOf(t)))
        }
        assertFalse("a speed camera is on screen, not spoken", HazardAlertPolicy.speak(HazardAlertPolicy.severityOf(HazardType.CAMERA)))
        assertTrue(HazardAlertPolicy.speak(HazardAlertPolicy.severityOf(HazardType.CLOSURE)))
    }
}
