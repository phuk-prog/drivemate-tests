package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C11.2: 29 Oct — "Parking near here" covered the view ~60 yd before a roundabout. */
class C11ParkingOfferPolicyTest {
    private fun defer(m: Double, speed: Double? = 13.0, type: String? = "turn", rb: Boolean = false, on: Boolean = false) =
        ParkingOfferPolicy.defer(m, speed, type, rb, on)

    @Test fun reportedCaseSixtyYardsBeforeARoundaboutIsHeldBack() {
        assertTrue(defer(55.0, type = "roundabout", rb = true))
    }

    @Test fun goingRoundTheRoundaboutHoldsItBackWhateverTheDistance() {
        assertTrue(defer(5_000.0, type = "roundabout", rb = true, on = true))
    }

    @Test fun clearOfTheNextManoeuvreTheOfferShows() {
        assertFalse(defer(800.0))
        assertFalse(defer(700.0, type = "roundabout", rb = true))
    }

    @Test fun anImminentOrdinaryTurnHoldsItBack() {
        assertTrue(defer(150.0))
        assertTrue(defer(399.0, speed = 5.0))
    }

    @Test fun fasterDrivingNeedsMoreRoom() {
        assertFalse(defer(450.0, speed = 13.0)) // ~30 mph: 25 s is 325 m, 400 m minimum
        assertTrue(defer(450.0, speed = 31.0))  // ~70 mph: 25 s is 775 m
    }

    @Test fun theFinalArriveStepNeverHoldsItBackSoDestinationParkingIsNotLost() {
        assertFalse(defer(50.0, type = "arrive"))
        assertFalse(defer(50.0, type = null))
    }

    @Test fun unknownDistanceStaysOutOfTheWay() {
        assertTrue(defer(Double.NaN))
        assertTrue(defer(-1.0))
    }
}
