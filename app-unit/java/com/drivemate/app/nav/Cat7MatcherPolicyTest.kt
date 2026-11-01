package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Category 7.2 to 7.5 matcher policy checks on synthetic metre-scale geometry. They prove the
 * LocalRoadMatcher rules only; they are not verified UK road truth, not a real drive, and say
 * nothing about on-device latency.
 */
class Cat7MatcherPolicyTest {
    private val base = LatLon(53.48, -2.23)

    /** Point [east] and [north] metres from the base. */
    private fun p(east: Double, north: Double) = LatLon(
        base.lat + north / 110_540.0,
        base.lon + east / (111_320.0 * cos(Math.toRadians(base.lat))),
    )

    private fun seg(id: String, from: LatLon, to: LatLon, oneWay: Int = 0) =
        LocalRoadMatcher.Segment(id, from, to, id, oneWay)

    private fun fix(
        east: Double, north: Double, heading: Double? = 90.0, t: Long,
        accuracy: Double = 8.0, speed: Double = 12.0,
    ) = LocalRoadMatcher.Fix(p(east, north), heading, speed, accuracy, t)

    // ---- 7.2 provider confidence, timestamp ordering, no stale continuity ----

    @Test fun outOfOrderAndDuplicateTimestampsAreRejectedWithoutChangingState() {
        val roads = listOf(seg("A", p(-100.0, 0.0), p(400.0, 0.0)))
        val m = LocalRoadMatcher()
        assertNotNull(m.match(fix(0.0, 0.0, t = 1_000), roads))
        assertNull(m.match(fix(1.0, 0.0, t = 1_000), roads))
        assertNull(m.match(fix(1.0, 0.0, t = 900), roads))
        assertNotNull(m.match(fix(12.0, 0.0, t = 2_000), roads))
    }

    @Test fun implausibleProviderValuesAreRejected() {
        val roads = listOf(seg("A", p(-100.0, 0.0), p(400.0, 0.0)))
        assertNull(LocalRoadMatcher().match(fix(0.0, 0.0, t = 1_000, accuracy = 40.0), roads))
        assertNull(LocalRoadMatcher().match(fix(0.0, 0.0, t = 1_000, accuracy = Double.NaN), roads))
        assertNull(LocalRoadMatcher().match(fix(0.0, 0.0, t = 1_000, speed = 1.0), roads))
        assertNull(LocalRoadMatcher().match(fix(0.0, 0.0, heading = null, t = 1_000), roads))
        assertNull(LocalRoadMatcher().match(fix(0.0, 0.0, heading = 400.0, t = 1_000), roads))
        assertNull(LocalRoadMatcher().match(
            LocalRoadMatcher.Fix(LatLon(Double.NaN, 0.0), 90.0, 12.0, 8.0, 1_000), roads))
    }

    @Test fun staleFixOlderThanTenSecondsGivesNoContinuity() {
        val roads = listOf(seg("A", p(-100.0, 0.0), p(400.0, 0.0)), seg("B", p(-100.0, 30.0), p(400.0, 30.0)))
        val stale = fix(200.0, 0.0, t = 12_000)
        val withHistory = LocalRoadMatcher().apply { match(fix(0.0, 0.0, t = 1_000), roads) }.match(stale, roads)
        val fresh = LocalRoadMatcher().match(stale, roads)
        assertEquals(fresh?.roadId, withHistory?.roadId)
    }

    // ---- 7.3 carriageway resolution with heading and topology; ambiguity held unknown ----

    @Test fun headingPicksTheDirectionOfTravelOnAOneWayPair() {
        val roads = listOf(
            seg("EAST", p(-100.0, 0.0), p(400.0, 0.0), oneWay = 1),
            seg("WEST", p(-100.0, 12.0), p(400.0, 12.0), oneWay = -1),
        )
        assertEquals("EAST", LocalRoadMatcher().match(fix(100.0, 3.0, heading = 90.0, t = 1_000), roads)?.roadId)
        assertEquals("WEST", LocalRoadMatcher().match(fix(100.0, 9.0, heading = 270.0, t = 1_000), roads)?.roadId)
    }

    @Test fun oppositeHeadingNeverSnapsOntoTheOtherCarriageway() {
        val roads = listOf(
            seg("EAST", p(-100.0, 0.0), p(400.0, 0.0), oneWay = 1),
            seg("WEST", p(-100.0, 12.0), p(400.0, 12.0), oneWay = -1),
        )
        val m = LocalRoadMatcher().match(fix(100.0, 3.0, heading = 270.0, t = 1_000), roads)
        assertTrue(m == null || m.roadId == "WEST")
    }

    @Test fun equallyPlausibleParallelRoadsAreHeldUnknown() {
        val roads = listOf(
            seg("TWO_A", p(-100.0, 0.0), p(400.0, 0.0)),
            seg("TWO_B", p(-100.0, 6.0), p(400.0, 6.0)),
        )
        assertNull(LocalRoadMatcher().match(fix(100.0, 3.0, heading = 90.0, t = 1_000), roads))
    }

    // ---- 7.4 conservative gap prediction; never cross an unconfirmed junction ----

    @Test fun predictionStopsAtAJunctionThatTheGeometryDoesNotContinue() {
        val roads = listOf(
            seg("MAIN", p(0.0, 0.0), p(100.0, 0.0)),
            seg("TURN", p(100.0, 0.0), p(100.0, 100.0)),
        )
        val match = LocalRoadMatcher().match(fix(90.0, 0.0, heading = 90.0, t = 1_000), roads)
        assertNotNull(match)
        val ahead = match!!.advance(50.0)
        assertTrue("prediction crossed the junction", Geo.distance(ahead.position, p(100.0, 0.0)) < 0.5)
    }

    @Test fun predictionFollowsTheSameWayButNotAnotherWayThatContinuesStraight() {
        val sameWay = listOf(
            seg("MAIN", p(0.0, 0.0), p(100.0, 0.0)),
            seg("MAIN", p(100.0, 0.0), p(200.0, 0.0)),
        )
        val follow = LocalRoadMatcher().match(fix(90.0, 0.0, heading = 90.0, t = 1_000), sameWay)!!.advance(40.0)
        assertTrue(Geo.distance(follow.position, p(130.0, 0.0)) < 1.0)
        val otherWay = listOf(
            seg("MAIN", p(0.0, 0.0), p(100.0, 0.0)),
            seg("ONWARD", p(100.0, 0.0), p(200.0, 0.0)),
        )
        val stop = LocalRoadMatcher().match(fix(90.0, 0.0, heading = 90.0, t = 1_000), otherWay)!!.advance(40.0)
        assertTrue(Geo.distance(stop.position, p(100.0, 0.0)) < 0.5)
    }

    @Test fun reacquisitionAfterDropoutStaysOnTheConnectedRoad() {
        val roads = listOf(
            seg("A", p(-100.0, 0.0), p(400.0, 0.0)),
            seg("PARALLEL", p(-100.0, 6.0), p(400.0, 6.0)),
        )
        val m = LocalRoadMatcher().apply { match(fix(0.0, 0.0, t = 1_000), roads) }
        val reacquired = m.match(fix(60.0, 0.0, t = 7_000), roads)
        assertTrue(reacquired == null || reacquired.roadId == "A")
    }

    // ---- 7.5 no hop, teleport or false road between consecutive fixes ----

    @Test fun steadyDrivingProducesNoHopOrTeleport() {
        val roads = listOf(seg("A", p(-100.0, 0.0), p(1_000.0, 0.0)))
        val m = LocalRoadMatcher()
        var previous: LatLon? = null
        for (i in 0 until 30) {
            val match = m.match(fix(i * 12.0, 0.0, t = 1_000L + i * 1_000L), roads)
            assertNotNull("fix $i unmatched", match)
            assertEquals("A", match!!.roadId)
            previous?.let { assertTrue(Geo.distance(it, match.position) < 25.0) }
            previous = match.position
        }
    }

    @Test fun aFixFarAlongTheSameRoadWithinOneSecondIsRejected() {
        val roads = listOf(seg("A", p(-100.0, 0.0), p(1_000.0, 0.0)))
        val m = LocalRoadMatcher()
        assertNotNull(m.match(fix(0.0, 0.0, t = 1_000), roads))
        assertNull(m.match(fix(300.0, 0.0, t = 2_000), roads))
    }

    @Test fun matchedRoadNeverChangesWithoutASharedNode() {
        val roads = listOf(
            seg("A", p(-100.0, 0.0), p(100.0, 0.0)),
            seg("CROSS", p(50.0, -100.0), p(50.0, 100.0)),
        )
        val m = LocalRoadMatcher()
        assertNotNull(m.match(fix(0.0, 0.0, t = 1_000), roads))
        val next = m.match(fix(50.0, 0.0, heading = 90.0, t = 2_000), roads)
        assertTrue(next == null || next.roadId == "A")
    }

    @Test fun networkOnlyFixNeverDrivesOrIsAuthoritativeNavigation() {
        // 7.2: a network-provider fix is never authoritative navigation, even when it is accepted
        // while parked; during navigation it is refused outright.
        val network = LocationSelectionPolicy.Fix(LocationSelectionPolicy.Source.NETWORK, 1_000L, 53.42, -2.26, 10.0)
        val navigating = LocationSelectionPolicy()
        assertFalse(navigating.accept(network, 1_000L, navigation = true))
        assertFalse(navigating.authoritative(1_000L))
        val parked = LocationSelectionPolicy()
        parked.accept(network, 1_000L, navigation = false)
        assertFalse(parked.authoritative(1_000L))
    }

    // ---- gap cases for 7.3 to 7.5 ----

    @Test fun parallelTieWithUnknownHeadingStaysUnknown() {
        // 7.3: two equal parallel roads and no heading: nothing may be chosen blindly.
        val roads = listOf(
            seg("TWO_A", p(-100.0, 0.0), p(400.0, 0.0)),
            seg("TWO_B", p(-100.0, 6.0), p(400.0, 6.0)),
        )
        assertNull(LocalRoadMatcher().match(fix(100.0, 3.0, heading = null, t = 1_000), roads))
    }

    @Test fun gapAcrossAnUnconfirmedJunctionIsNeverBridged() {
        // 7.4: the next road starts 5 m past the end of the last one, with no shared node, so
        // the junction is unconfirmed and the car may not be placed on the far road.
        val roads = listOf(
            seg("MAIN", p(0.0, 0.0), p(100.0, 0.0)),
            seg("FAR", p(105.0, 0.0), p(200.0, 0.0)),
        )
        val m = LocalRoadMatcher()
        assertNotNull(m.match(fix(90.0, 0.0, t = 1_000), roads))
        assertNull(m.match(fix(130.0, 0.0, t = 3_000), roads))
    }

    @Test fun fixFarAlongTheSameRoadWithinOneSecondIsRejectedButANearOneIsAccepted() {
        // 7.5: 1 s at 12 m/s allows about 37 m of travel; 30 m is accepted, 300 m is a teleport.
        val roads = listOf(seg("A", p(-100.0, 0.0), p(1_000.0, 0.0)))
        val near = LocalRoadMatcher().apply { match(fix(0.0, 0.0, t = 1_000), roads) }
        assertEquals("A", near.match(fix(30.0, 0.0, t = 2_000), roads)?.roadId)
        val far = LocalRoadMatcher().apply { match(fix(0.0, 0.0, t = 1_000), roads) }
        assertNull(far.match(fix(300.0, 0.0, t = 2_000), roads))
    }
}
