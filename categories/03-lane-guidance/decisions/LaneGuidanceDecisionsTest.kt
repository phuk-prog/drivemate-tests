package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.data.VoiceCommand
import com.drivemate.app.nav.LaneDisplay
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LaneTiming
import org.junit.Assert.*
import org.junit.Test

class LaneGuidanceDecisionsTest {
    private fun lane(valid: Boolean = true, preferred: Boolean = true) = Lane(listOf("straight"), valid, preferred)

    @Test fun q3_23_24_speedLimitBandsReplaceHistoricalMinimum() {
        for ((mph, seconds) in listOf(20 to 20, 30 to 20, 40 to 45, 50 to 45, 60 to 90, 70 to 90)) {
            assertEquals(mph * 0.44704 * seconds, LaneTiming.showFromLimit(mph * 1.609344)!!, 0.01)
        }
        assertEquals(178.816, LaneTiming.showFromLimit(20 * 1.609344)!!, 0.001)
        for (limit in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0, 15.0, 200.0))
            assertNull(LaneTiming.showFromLimit(limit))
    }
    @Test fun q3_6_7_stoppedTrafficLimitChangesAndRouteReplacement() {
        val state = LaneDisplay()
        assertFalse(state.due("route-a", "junction-a", 200.0, 20 * 1.609344))
        assertTrue(state.due("route-a", "junction-a", 178.0, 20 * 1.609344))
        assertTrue(state.due("route-a", "junction-a", 180.0, null))
        assertFalse(state.due("route-b", "junction-a", 180.0, null))
        assertTrue(state.due("route-b", "junction-a", 150.0, 20 * 1.609344))
        assertFalse(state.due("route-b", "junction-a", -1.0, 20 * 1.609344))
        assertFalse(state.due("route-b", "junction-b", Double.NaN, 20 * 1.609344))
    }
    @Test fun q3_1_19_22_sameDirectionLanesAndComplexMotorwayOnly() {
        assertTrue(LanePolicy.inScope(false, 2, false))
        assertFalse(LanePolicy.inScope(false, 1, false))
        assertFalse(LanePolicy.inScope(true, 3, false))
        assertTrue(LanePolicy.inScope(true, 3, true))
    }
    @Test fun q3_2_3_10_allUsableLanesRemainWithDistinctPreference() {
        val lanes = listOf(lane(false), lane(preferred = false), lane(preferred = true))
        assertEquals(listOf(1, 2), LanePolicy.usable(lanes))
        assertEquals(listOf(2), LanePolicy.preferred(lanes))
        assertEquals(emptyList<Int>(), LanePolicy.preferred(listOf(lane(), lane())))
    }
    @Test fun q3_4_5_9_15_partialDataNeverBecomesAdviceOfflineOrOnline() {
        assertTrue(LanePolicy.reliable(listOf(lane(), lane(false))))
        for (unknown in listOf(emptyList<String>(), listOf("unknown"), listOf("straight", "unknown")))
            assertFalse(LanePolicy.reliable(listOf(lane(), Lane(unknown, false))))
        assertFalse(LanePolicy.reliable(listOf(lane(false), lane(false))))
        assertFalse(LanePolicy.reliable(emptyList()))
    }
    @Test fun q3_8_preferenceNeedsConnectivityNotFollowingTurnAngle() {
        val input = listOf(lane(), lane())
        assertTrue(com.drivemate.app.ui.preferLanes(input, -90f).none { it.preferred })
        assertEquals(listOf(0, 1), LanePolicy.usable(input))
    }
    @Test fun q3_11_restrictedAndUnknownEligibility() {
        val bus = lane().copy(bus = true)
        assertEquals(listOf(1), LanePolicy.usable(listOf(bus, lane())))
        assertFalse(LanePolicy.reliable(listOf(lane(), lane().copy(accessKnown = false))))
        assertEquals(listOf(1), LanePolicy.usable(listOf(lane().copy(closed = true), lane())))
    }
    @Test fun q3_12_14_alertOnceAndAgainOnlyAfterMaterialChange() {
        val alerts = com.drivemate.app.nav.LaneAlerts()
        assertEquals("Roadworks ahead. Follow road signs.", alerts.warning("a", "works"))
        assertNull(alerts.warning("a", "works"))
        assertNotNull(alerts.warning("a", "lane-closed"))
        assertNull(alerts.warning("a", "lane-closed"))
        assertNull(alerts.warning("", ""))
    }
    @Test fun q3_13_16_handsFreeReportNeverClaimsCurrentLane() {
        assertEquals(VoiceCommand.LaneWrong, VoiceCommand.parse("Lane guidance is wrong"))
        assertEquals(VoiceCommand.LaneWrong, VoiceCommand.parse("Hey DriveMate lane guidance is wrong"))
    }
}
