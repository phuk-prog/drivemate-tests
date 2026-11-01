package com.drivemate.app.data

import com.drivemate.app.data.PlacePolicy.Approach
import com.drivemate.app.data.PlacePolicy.Candidate
import com.drivemate.app.data.PlacePolicy.Entrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** C04.4: the approach offered is the nearest reachable entrance, never a coordinate guess (pure policy, JVM). */
class C04EntranceRuleTest {
    private fun c(name: String, match: Double, distance: Double, entrance: Entrance) =
        Candidate(name, match, distance, entrance, null)

    @Test
    fun bestMatchCanDifferFromClosestReachableEntrance() {
        val list = listOf(
            c("Kerb-side shop", 0.95, 40.0, Entrance.UNREACHABLE),
            c("Car park entrance", 0.80, 120.0, Entrance.REACHABLE),
        )
        assertEquals("Kerb-side shop", PlacePolicy.bestMatch(list)?.name)
        val approach = PlacePolicy.approachFor(list)
        assertTrue(approach is Approach.Reachable)
        assertEquals("Car park entrance", (approach as Approach.Reachable).candidate.name)
    }

    @Test
    fun unknownEntranceIsNeverChosen() {
        val list = listOf(c("Nearest but unsurveyed", 0.9, 5.0, Entrance.UNKNOWN))
        assertEquals(Approach.NotSupported, PlacePolicy.approachFor(list))
    }

    @Test
    fun unreachableEntranceIsNeverChosenEvenWhenNearest() {
        val list = listOf(
            c("Door on the far side", 0.9, 3.0, Entrance.UNREACHABLE),
            c("Reachable kerb", 0.7, 60.0, Entrance.REACHABLE),
        )
        assertEquals("Reachable kerb", (PlacePolicy.approachFor(list) as Approach.Reachable).candidate.name)
    }

    @Test
    fun coordinateOnlyResultsGiveNotSupported() {
        assertEquals(Approach.NotSupported, PlacePolicy.approachFor(emptyList()))
        assertEquals(Approach.NotSupported, PlacePolicy.approachFor(listOf(c("Point only", 1.0, 0.0, Entrance.UNKNOWN))))
    }

    @Test
    fun tiedReachableDistanceIsBrokenByName() {
        val list = listOf(c("Zed", 0.5, 30.0, Entrance.REACHABLE), c("Able", 0.5, 30.0, Entrance.REACHABLE))
        assertEquals("Able", PlacePolicy.closestReachable(list)?.name)
    }
}
