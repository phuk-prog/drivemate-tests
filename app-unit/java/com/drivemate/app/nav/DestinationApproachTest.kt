package com.drivemate.app.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 4.4 (drivable approach): a destination entrance needs a car-drivable road within 50 m.
 * Pure check: no network, no routing graph.
 */
class DestinationApproachTest {
    private val residential = mapOf("highway" to "residential")
    private val footway = mapOf("highway" to "footway")

    @Test fun anEntranceOnlyBesideAFootpathHasNoDrivableApproach() {
        assertFalse(DestinationApproach.hasDrivableApproach(listOf(5.0 to footway)))
    }

    @Test fun aDrivableRoadWithinFiftyMetresIsAnApproach() {
        assertTrue(DestinationApproach.hasDrivableApproach(listOf(30.0 to residential)))
    }

    @Test fun aDrivableRoadBeyondFiftyMetresIsNotAnApproach() {
        assertFalse(DestinationApproach.hasDrivableApproach(listOf(80.0 to residential)))
    }

    @Test fun aFootpathDoesNotMakeAnApproachEvenWhenACarRoadIsFar() {
        assertFalse(DestinationApproach.hasDrivableApproach(listOf(5.0 to footway, 120.0 to residential)))
    }

    @Test fun noNearbyWayMeansNoApproach() {
        assertFalse(DestinationApproach.hasDrivableApproach(emptyList()))
    }
}
