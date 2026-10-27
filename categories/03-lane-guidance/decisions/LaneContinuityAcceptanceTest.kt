package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.data.RouteStep
import com.drivemate.app.nav.DataSource
import com.drivemate.app.nav.FollowingLaneEvidence
import com.drivemate.app.nav.FollowingLaneLink
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

/**
 * Category 3 non-physical acceptance regression for the route-model -> lane-policy boundary.
 * Contract fixtures are NOT independent confirmation of lane connections on UK roads.
 * Real UK road/layout acceptance remains open until sourced, dated lane-link truth exists.
 */
class LaneContinuityAcceptanceTest {
    private val firstPoint = LatLon(53.4087, -2.1560)
    private val nextPoint = LatLon(53.4080, -2.1600)
    private val anotherPoint = LatLon(53.4070, -2.1610)
    private val approach = List(3) { Lane(listOf("straight"), true, preferred = false) }
    private val following = listOf(
        Lane(listOf("left"), true, preferred = false),
        Lane(listOf("straight"), false, preferred = false)
    )
    private val limit = 30.0 * 1.609344

    private fun proof(
        next: LatLon = nextPoint,
        from: List<Lane> = approach,
        to: List<Lane> = following,
        connections: List<FollowingLaneLink> = listOf(
            FollowingLaneLink(0, 0), FollowingLaneLink(1, 1), FollowingLaneLink(2, 1)
        ),
    ) = FollowingLaneEvidence(
        nextDecision = next,
        approachSignature = LanePolicy.signature(from),
        followingSignature = LanePolicy.signature(to),
        connections = connections,
        source = DataSource.MAP_OSM,
        confidence = 0.95,
        complete = true
    )

    private fun planned(
        data: FollowingLaneEvidence?,
        to: List<Lane> = following,
        decision: LatLon = nextPoint,
        distance: Double = 100.0,
    ): List<Lane> = LanePolicy.forFollowing(approach, to, decision, distance, limit, data)

    @Test fun q3_3_8_routeStepWithNoSupplierEvidenceNeverInventsALanePreference() {
        val step = RouteStep("Continue", "continue", "straight", "M60", 250.0,
            firstPoint, leftDriving = true, lanes = approach)
        val next = RouteStep("Take the exit", "off ramp", "slight left", "A6", 120.0,
            nextPoint, leftDriving = true, lanes = following)
        assertNull(step.followingLaneEvidence) // Current ordinary provider contract.
        val result = LanePolicy.forFollowing(step.lanes, next.lanes, next.location,
            100.0, limit, step.followingLaneEvidence)
        assertEquals(listOf(0, 1, 2), LanePolicy.usable(result))
        assertTrue("turn angles and lane counts must not produce a preferred lane",
            LanePolicy.preferred(result).isEmpty())
    }

    @Test fun q3_3_8_completeLaneLinksMayPreferOnlyTheVerifiedConnectedSubset() {
        val result = planned(proof())
        assertEquals(listOf(0, 1, 2), LanePolicy.usable(result))
        assertEquals(listOf(0), LanePolicy.preferred(result))
        assertEquals(approach.map { it.indications }, result.map { it.indications })
    }

    @Test fun q3_4_8_missingAmbiguousOrUnverifiedLinksPreserveEqualUsableHighlighting() {
        val possibilities = listOf<FollowingLaneEvidence?>(
            null,
            proof(connections = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 1))),
            proof().copy(complete = false),
            proof().copy(confidence = 0.2),
            proof(connections = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 1), FollowingLaneLink(2, 7))),
            proof(connections = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(0, 1),
                FollowingLaneLink(1, 1), FollowingLaneLink(2, 1)))
        )
        for (candidate in possibilities) {
            val result = planned(candidate)
            assertEquals(listOf(0, 1, 2), LanePolicy.usable(result))
            assertTrue("no unsourced or ambiguous preferred lane: $candidate",
                LanePolicy.preferred(result).isEmpty())
        }
    }

    @Test fun q3_8_newDecisionOrChangedDownloadedLayoutInvalidatesPreviousProof() {
        val existing = proof()
        assertEquals(listOf(0), LanePolicy.preferred(planned(existing)))
        val changedLayout = following.mapIndexed { index, lane ->
            if (index == 1) lane.copy(indications = listOf("right")) else lane
        }
        assertTrue(LanePolicy.preferred(planned(existing, changedLayout)).isEmpty())
        assertTrue(LanePolicy.preferred(planned(existing, decision = anotherPoint)).isEmpty())
        assertTrue(LanePolicy.preferred(planned(existing, distance = 1_000.0)).isEmpty())
        assertTrue(LanePolicy.preferred(planned(existing, distance = -1.0)).isEmpty())
    }

    @Test fun q3_11_closedAndRestrictedFollowingLanesCannotBecomePreferred() {
        val restricted = listOf(following[0].copy(bus = true), following[1].copy(valid = true))
        val result = planned(proof(to = restricted), to = restricted)
        assertEquals(listOf(1, 2), LanePolicy.preferred(result))
        assertFalse(result[0].preferred)
        assertEquals(listOf(0, 1, 2), LanePolicy.usable(result))
    }
}
