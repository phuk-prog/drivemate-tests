package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.nav.*
import org.junit.Assert.*
import org.junit.Test

class LaneConnectivityDecisionsTest {
    private val lanes = List(2) { Lane(listOf("straight"), true) }
    private val following = listOf(Lane(listOf("left"), true), Lane(listOf("straight"), false))
    private val next = LatLon(0.01, 0.0)
    private val limit = 20 * 1.609344
    private fun evidence() = FollowingLaneEvidence(next, LanePolicy.signature(lanes), LanePolicy.signature(following),
        listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 1)), DataSource.MAP_OSM, 0.9, true)
    private fun planned(evidence: FollowingLaneEvidence?, distance: Double = 100.0, speedLimit: Double? = limit) =
        LanePolicy.forFollowing(lanes, following, next, distance, speedLimit, evidence)

    @Test fun q3_3_8_verifiedNextLaneChoiceAddsPreferenceWithoutRemovingUsableLanes() {
        val result = planned(evidence())
        assertEquals(listOf(0, 1), LanePolicy.usable(result))
        assertEquals(listOf(0), LanePolicy.preferred(result))
        assertEquals(lanes.map { it.indications }, result.map { it.indications })
    }
    @Test fun q3_8_23_24_preferredLookAheadUsesTheApprovedLimitBand() {
        assertTrue(LanePolicy.preferred(planned(evidence(), 179.0)).isEmpty())
        assertEquals(listOf(0), LanePolicy.preferred(planned(evidence(), 178.0)))
        assertEquals(listOf(0), LanePolicy.preferred(planned(evidence(), 200.0, 70 * 1.609344)))
        assertTrue(LanePolicy.preferred(planned(evidence(), 100.0, null)).isEmpty())
        assertTrue(LanePolicy.preferred(planned(evidence(), -1.0)).isEmpty())
    }
    @Test fun q3_3_8_incompleteStaleOrMismatchedConnectivityCannotPreferALane() {
        val current = evidence()
        val invalid = listOf(null, current.copy(complete = false), current.copy(source = DataSource.NONE),
            current.copy(source = DataSource.ROUTE_GEOMETRY), current.copy(confidence = Double.NaN), current.copy(confidence = 0.6),
            current.copy(approachSignature = "old-layout"), current.copy(followingSignature = "old-following-layout"),
            current.copy(nextDecision = LatLon(0.02, 0.0)), current.copy(nextDecision = LatLon(Double.NaN, 0.0)),
            current.copy(connections = listOf(FollowingLaneLink(0, 0))),
            current.copy(connections = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 4))))
        for (source in invalid) {
            val result = planned(source)
            assertTrue("$source", LanePolicy.preferred(result).isEmpty())
            assertEquals(listOf(0, 1), LanePolicy.usable(result))
        }
    }
}
