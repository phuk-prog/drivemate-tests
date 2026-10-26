package com.drivemate.tests.cat03

import com.drivemate.app.data.*
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.LanePolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LaneStorageDecisionsTest {
    // Synthetic coordinates; not a recorded journey.
    private val at = LatLon(0.0, 0.0)
    private fun fix() = LaneCorrections.Fix(at, 90.0, listOf(listOf("left"), listOf("straight")), 0,
        maneuver = "turn|left", exitHeading = 0.0, sourceSignature = "way-1:version-1", updatedAt = 1L)
    @Test fun q3_17_correctionRequiresReviewedSourceAndMatchingDirection() {
        val prefs = MemoryPreferences(); val storage = LaneCorrections(prefs); val fix = fix()
        storage.save(fix)
        assertNotNull(storage.find(at, 90.0, "turn|left", 0, 0.0, fix.sourceSignature))
        assertNull(storage.find(at, 270.0, "turn|left", 0, 0.0, fix.sourceSignature))
        assertNull(storage.find(at, 90.0, "turn|right", 0, 0.0, fix.sourceSignature))
        assertNull(storage.find(at, 90.0, "turn|left", 1, 0.0, fix.sourceSignature))
        assertNull(storage.find(at, 90.0, "turn|left", 0, 0.0, "way-1:version-2"))
        storage.noteSource(at, 90.0, "turn|left", 0, 0.0, "way-1:version-2")
        val reloaded = LaneCorrections(prefs)
        assertTrue(reloaded.needsReviewCurrent(reloaded.list().single()))
        assertNull(reloaded.find(at, 90.0, "turn|left", 0, 0.0, fix.sourceSignature))
        reloaded.save(fix.copy(sourceSignature = "way-1:version-2"))
        assertNotNull(reloaded.find(at, 90.0, "turn|left", 0, 0.0, "way-1:version-2"))
    }
    @Test fun q3_17_legacyUnboundCorrectionsCannotOverride() {
        assertFalse(LaneCorrections.applicable(fix().copy(sourceSignature = ""), ""))
        assertFalse(LaneCorrections.applicable(fix().copy(maneuver = ""), fix().sourceSignature))
    }
    @Test fun q3_18_retainWithoutExpiryDeleteOneAndDeleteAll() {
        val prefs = MemoryPreferences(); val storage = LaneCorrections(prefs)
        val a = fix(); storage.save(a)
        assertFalse(LaneCorrections.needsReview(a, Long.MAX_VALUE))
        storage.save(a.copy(at = LatLon(0.01, 0.0), id = "second"))
        assertEquals(2, LaneCorrections(prefs).list().size)
        storage.delete(a.id); assertEquals("second", LaneCorrections(prefs).list().single().id)
        storage.deleteAll(); assertTrue(LaneCorrections(prefs).list().isEmpty())
    }
    @Test fun q3_5_16_20_reportsSurviveRestartWithDirectionWithoutApprovingGuidance() {
        val prefs = MemoryPreferences(); val reports = LaneReports(prefs)
        val report = LaneReports.Report("synthetic", at, 90.0, 0.0, "turn|left", 0, "source", emptyList(), true)
        reports.add(report)
        assertEquals(report, LaneReports(prefs).list().single())
        assertTrue(LaneCorrections(prefs).list().isEmpty())
        reports.delete(report.id); assertTrue(LaneReports(prefs).list().isEmpty())
        reports.add(report); reports.clear(); assertTrue(LaneReports(prefs).list().isEmpty())
    }
    @Test fun q3_17_restrictionChangeChangesSourceIdentity() {
        val lanes = listOf(Lane(listOf("left"), true), Lane(listOf("straight"), false))
        assertNotEquals(LanePolicy.signature(lanes), LanePolicy.signature(lanes.map { it.copy(bus = true) }))
        assertNotEquals(LanePolicy.signature(lanes), LanePolicy.signature(lanes.reversed()))
    }
    @Test fun q3_16_18_malformedReportsAreIgnoredWithoutApprovingOrDeletingValidReports() {
        val prefs = MemoryPreferences()
        val report = LaneReports.Report("valid", at, 90.0, 0.0, "turn|left", 0, "source", emptyList(), true)
        LaneReports(prefs).add(report)
        val records = JSONArray(prefs.getString("laneReviewReports", "[]"))
        val invalid = JSONObject(records.getJSONObject(0).toString()).put("id", "invalid").put("lat", 91.0)
        records.put(invalid).put(JSONObject().put("id", "missing-fields"))
        prefs.edit().putString("laneReviewReports", records.toString()).apply()
        assertEquals(listOf(report), LaneReports(prefs).list())
        assertTrue(LaneCorrections(prefs).list().isEmpty())
    }
    @Test fun q3_16_invalidReportInputIsRejectedBeforePersistence() {
        val prefs = MemoryPreferences()
        val reports = LaneReports(prefs)
        val valid = LaneReports.Report("valid", at, 90.0, 0.0, "turn|left", 0, "source", emptyList(), true)
        for (invalid in listOf(valid.copy(at = LatLon(Double.NaN, 0.0)), valid.copy(heading = Double.POSITIVE_INFINITY),
            valid.copy(sourceSignature = ""), valid.copy(maneuver = ""), valid.copy(lanes = List(13) { Lane(listOf("straight"), true) }))) {
            try { reports.add(invalid); fail("Invalid report must be rejected") }
            catch (_: IllegalArgumentException) { /* expected, with no partial write */ }
        }
        assertTrue(LaneReports(prefs).list().isEmpty())
    }
    @Test fun q3_18_deletingAConflictingCorrectionAlsoDeletesItsConflictRecord() {
        val prefs = MemoryPreferences(); val storage = LaneCorrections(prefs); val fix = fix()
        storage.save(fix)
        storage.noteSource(at, 90.0, "turn|left", 0, 0.0, "way-1:version-2")
        assertTrue(prefs.getStringSet("laneCorrectionConflicts", mutableSetOf()).orEmpty().contains(fix.id))
        storage.delete(fix.id)
        assertFalse(prefs.getStringSet("laneCorrectionConflicts", mutableSetOf()).orEmpty().contains(fix.id))
        assertTrue(LaneCorrections(prefs).list().isEmpty())
    }
    @Test fun q3_17_conflictingRestoredCorrectionsNeverChooseArbitrarily() {
        val prefs = MemoryPreferences(); val fix = fix(); LaneCorrections(prefs).save(fix)
        val records = JSONArray(prefs.getString("laneCorrections", "[]"))
        records.put(JSONObject(records.getJSONObject(0).toString()).put("id", "conflicting")
            .put("use", 1).put("valid", JSONArray(listOf(1))).put("preferred", 1))
        prefs.edit().putString("laneCorrections", records.toString()).apply()
        assertNull(LaneCorrections(prefs).find(at, 90.0, "turn|left", 0, 0.0, fix.sourceSignature))
    }

}
