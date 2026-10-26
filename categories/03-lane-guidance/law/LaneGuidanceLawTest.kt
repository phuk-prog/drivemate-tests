package com.drivemate.tests.cat03

import com.drivemate.app.data.Lane
import com.drivemate.app.data.LaneDb
import com.drivemate.app.nav.LanePolicy
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

/** Highway Code 133/134, 140–143, 184–186, 258/269 and National Highways Red X.
 * Tests of safety properties; no owner timing or styling choices are legal thresholds.
 * Synthetic OSM-shaped data, not a private journey or a claim to validate real map data.
 */
class LaneGuidanceLawTest {
    private val left = Lane(listOf("left"), true)
    @Test fun prohibitedLanesCannotBeRecommended() {
        for (prohibited in listOf(left.copy(bus = true), left.copy(closed = true),
            left.copy(restriction = "CYCLE"), left.copy(restriction = "HOV"), left.copy(restriction = "SHOULDER"))) {
            assertEquals(listOf(1), LanePolicy.usable(listOf(prohibited, left)))
            val corrected = LanePolicy.withRestrictions(listOf(left, left), listOf(prohibited, left))!!
            assertEquals(listOf(1), LanePolicy.usable(corrected))
        }
    }
    @Test fun uncertainPermissionsAndConflictingLayoutCannotCreateAnOverride() {
        assertFalse(LanePolicy.reliable(listOf(left, left.copy(accessKnown = false))))
        assertNull(LanePolicy.withRestrictions(listOf(left, left, left), listOf(left.copy(bus = true), left)))
    }
    private fun db(extra: String = "", directions: String = "\"oneway\":\"yes\",\"turn:lanes\":\"left|through\"") = LaneDb.parse(listOf("""
        {"ways":[{"id":"synthetic-way","t":{$directions$extra},"g":[[0.0,0.0],[0.0,0.001]]}]}
    """.trimIndent()))
    @Test fun roundaboutAdviceDoesNotAssumeStraightAheadUsesLeftLane() {
        val lanes = db(directions = "\"oneway\":\"yes\",\"junction\":\"roundabout\",\"turn:lanes\":\"left|through\"")
            .lanesAt(LatLon(0.0005, 0.0), 0.0, "straight")!!
        assertEquals(listOf(1), LanePolicy.usable(lanes))
    }
    @Test fun laneCountAloneDoesNotInventArrows() {
        assertNull(db(directions = "\"oneway\":\"yes\",\"lanes\":\"2\"").lanesAt(LatLon(0.0005, 0.0), 0.0, "left"))
    }
}
