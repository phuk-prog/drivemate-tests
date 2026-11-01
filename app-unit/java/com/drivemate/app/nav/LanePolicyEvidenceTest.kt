package com.drivemate.app.nav

import com.drivemate.app.data.Lane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanePolicyEvidenceTest {
    private val limit = 20 * 1.609344
    private val decision = LatLon(53.0, -2.0)
    private val approach = listOf(Lane(listOf("left"), valid = true), Lane(listOf("straight"), valid = true))
    private val following = listOf(Lane(listOf("left"), valid = true), Lane(listOf("straight"), valid = false))

    private fun proof(
        complete: Boolean = true, confidence: Double = 0.9, source: DataSource = DataSource.MAP_OSM,
        at: LatLon = decision, connections: List<FollowingLaneLink> = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 1)),
        approachSig: String = LanePolicy.signature(approach),
    ) = FollowingLaneEvidence(at, approachSig, LanePolicy.signature(following), connections, source, confidence, complete)

    private fun preferredFor(evidence: FollowingLaneEvidence?) =
        LanePolicy.forFollowing(approach, following, decision, 100.0, limit, evidence).map { it.preferred }

    @Test fun completeHighConfidenceEvidencePrefersOnlyTheContinuingLane() {
        assertEquals(listOf(true, false), preferredFor(proof()))
    }

    @Test fun missingEvidenceGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(null))
    }

    @Test fun incompleteEvidenceGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(complete = false)))
    }

    @Test fun lowConfidenceEvidenceGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(confidence = 0.69)))
        assertEquals(listOf(true, false), preferredFor(proof(confidence = 0.7)))
    }

    @Test fun unsourcedEvidenceGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(source = DataSource.NONE)))
    }

    @Test fun partialCoverageGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(connections = listOf(FollowingLaneLink(0, 0)))))
    }

    @Test fun evidenceForAnotherLayoutOrDecisionGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(approachSig = "other")))
        assertEquals(listOf(false, false), preferredFor(proof(at = LatLon(53.1, -2.0))))
    }

    @Test fun invalidConnectionIndexGivesNoPreferredLane() {
        assertEquals(listOf(false, false), preferredFor(proof(connections = listOf(FollowingLaneLink(0, 0), FollowingLaneLink(1, 5)))))
    }

    @Test fun closingEveryLaneOrAnUnknownLaneGivesNoAdvice() {
        assertNull(LanePolicy.afterLiveClosures(approach, setOf(0, 1)))
        assertNull(LanePolicy.afterLiveClosures(approach, setOf(7)))
    }

    @Test fun liveClosureRemovesOnlyTheClosedLane() {
        val next = LanePolicy.afterLiveClosures(approach, setOf(1))!!
        assertTrue(next[1].closed)
        assertFalse(next[1].valid)
        assertEquals(listOf(0), LanePolicy.usable(next))
    }

    @Test fun laneDisplayLatchesAtOnsetAndNeverResurfacesAfterTheTurn() {
        val display = LaneDisplay()
        assertFalse(display.due("route", "junction", 180.0, limit))
        assertTrue(display.due("route", "junction", 178.0, limit))
        assertTrue("stays latched while approaching", display.due("route", "junction", 150.0, limit))
        assertFalse("passed the decision", display.due("route", "junction", -1.0, limit))
        assertFalse("backward GPS wobble does not resurface", display.due("route", "junction", 50.0, limit))
        assertTrue("a new decision rearms", display.due("route", "next", 100.0, limit))
    }

    @Test fun newRouteInsideOnsetShowsAtOnce() {
        val display = LaneDisplay()
        display.due("route", "junction", -1.0, limit)
        assertTrue(display.due("route2", "junction", 100.0, limit))
    }

    @Test fun roadworksAlertRepeatsOnlyOnMaterialChange() {
        val alerts = LaneAlerts()
        assertEquals("Roadworks ahead. Follow road signs.", alerts.warning("closure-1", "lane 2 closed"))
        assertNull(alerts.warning("closure-1", "lane 2 closed"))
        assertEquals("Roadworks update. Follow road signs.", alerts.warning("closure-1", "lanes 2 and 3 closed"))
        assertNull("blank id gives no claim", alerts.warning("", "lane 2 closed"))
    }
}
