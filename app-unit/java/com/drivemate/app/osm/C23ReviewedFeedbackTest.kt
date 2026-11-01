package com.drivemate.app.osm

import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Category 23.3 and 23.4: edits and notes are only prepared from the driver's own sign reading, never guessed. */
class C23ReviewedFeedbackTest {
    private val base = LatLon(53.40, -2.20)
    private fun north(metres: Double) = LatLon(base.lat + metres / 111_195.0, base.lon)

    private val road = OsmLimitEdit.Way(1L, 3, (0..3).map { OsmLimitEdit.Node(it.toLong() + 10, north(it * 100.0)) },
        mapOf("highway" to "residential", "name" to "Hillgate"))
    private val path = listOf(north(10.0), north(150.0), north(290.0))

    @Test fun signSeenAlongWholeRoadPreparesOneEditWithSurveySource() {
        val outcome = OsmLimitEdit.plan(path, 0.0, null, "30 mph", listOf(road))
        val plan = (outcome as OsmLimitEdit.Outcome.Edit).plan
        assertEquals("maxspeed:forward", plan.key)
        assertEquals(1L, plan.wayId)
        val xml = OsmLimitEdit.changesetXml(plan.comment, "1.0")
        assertTrue(xml.contains("<tag k=\"source\" v=\"survey\"/>"))
    }

    @Test fun partialRoadSightingBecomesANoteNotAnEdit() {
        val short = listOf(north(100.0), north(150.0), north(180.0))
        assertTrue(OsmLimitEdit.plan(short, 0.0, null, "30 mph", listOf(road)) is OsmLimitEdit.Outcome.NoteInstead)
    }

    @Test fun twoPossibleRoadsNeedAPersonToChoose() {
        val twin = road.copy(id = 2L)
        val reason = (OsmLimitEdit.plan(path, 0.0, null, "30 mph", listOf(road, twin)) as OsmLimitEdit.Outcome.NoteInstead).reason
        assertTrue(reason, reason.contains("needs a person to choose"))
    }

    @Test fun roadChangedSincePreviewIsNotOverwritten() {
        val changed = road.copy(tags = road.tags + ("maxspeed" to "60 mph"))
        val reason = (OsmLimitEdit.plan(path, 0.0, 48.0, "30 mph", listOf(changed)) as OsmLimitEdit.Outcome.NoteInstead).reason
        assertTrue(reason, reason.contains("changed here since your map"))
    }

    @Test fun specialOsmValuesAreNeverOverwritten() {
        val national = road.copy(tags = road.tags + ("maxspeed" to "national"))
        assertTrue(OsmLimitEdit.plan(path, 0.0, null, "30 mph", listOf(national)) is OsmLimitEdit.Outcome.NoteInstead)
    }

    @Test fun observationRefusesUnconfirmedOrPrivateText() {
        val good = PublicObservation("obs-1", 53.40, -2.20, "A6 northbound", "north", "Speed sign reads 30 mph.", 1_800_000_000_000L, true)
        assertTrue(good.preview().contains("[DriveMate observation"))
        assertFailsWith { good.copy(firstHandConfirmed = false).preview() }
        assertFailsWith { good.copy(description = "See https://example.org").preview() }
        assertFailsWith { good.copy(description = "Parked at 53.4012, -2.2034").preview() }
    }

    private fun assertFailsWith(block: () -> Unit) {
        try { block() } catch (expected: IllegalArgumentException) { return }
        fail("expected the observation to be refused")
    }
}
