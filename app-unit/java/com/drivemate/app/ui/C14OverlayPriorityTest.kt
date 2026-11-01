package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C14 banner priority stack: manoeuvre first, then urgent hazard, then secondary offers.
 * The overlap matrix below stands in for visual overlap checks: no secondary offer is ever
 * drawn over the manoeuvre or a hazard card. Pixel snapshots need an emulator and are not run here.
 */
class C14OverlayPriorityTest {
    @Test fun secondaryOffersShowOnlyWhenNeitherManoeuvreNorHazardIsShown() {
        assertTrue(secondaryOffersShow(manoeuvreImminent = false, hazardShowing = false))
        assertFalse(secondaryOffersShow(manoeuvreImminent = true, hazardShowing = false))
        assertFalse(secondaryOffersShow(manoeuvreImminent = false, hazardShowing = true))
        assertFalse(secondaryOffersShow(manoeuvreImminent = true, hazardShowing = true))
    }

    @Test fun overlapMatrixNeverShowsAnOfferOverAManoeuvreOrHazard() {
        val states = listOf(false, true)
        for (manoeuvre in states) for (hazard in states) {
            val offers = secondaryOffersShow(manoeuvre, hazard)
            assertEquals("offers with manoeuvre=$manoeuvre hazard=$hazard", !manoeuvre && !hazard, offers)
        }
    }

    @Test fun anImminentRoundaboutHidesOffersEvenWhenSlow() {
        // 20 m before a roundabout at standstill is still imminent, so offers stay out of the way.
        val imminent = imminentManeuver(arrived = false, distance = 20.0, onRoundabout = true, speed = 0f)
        assertTrue(imminent)
        assertFalse(secondaryOffersShow(imminent, hazardShowing = false))
    }

    @Test fun clearOfTheManoeuvreAndNoHazardOffersReturn() {
        val imminent = imminentManeuver(arrived = false, distance = 900.0, onRoundabout = false, speed = 13f)
        assertFalse(imminent)
        assertTrue(secondaryOffersShow(imminent, hazardShowing = false))
    }
}
