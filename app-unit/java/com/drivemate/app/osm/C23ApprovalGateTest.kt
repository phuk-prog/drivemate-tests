package com.drivemate.app.osm

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 23.3: a queued OSM note is sent only when the driver has approved that exact note. */
class C23ApprovalGateTest {
    private val now = 1_800_000_000_000L

    private fun queued(identity: String, text: String = "Speed sign reads 30 mph.") = JSONObject()
        .put("identity", identity)
        .put("text", text)
        .put("status", "Queued")
        .put("nextAttempt", 0L)
        .put("firstHandConfirmed", true)
        .put("previewConsentedAt", now - 1_000L)
        .put("previewSha256", OsmNotesController.hexDigest(text))

    @Test fun queuedNoteIsNotSentWithoutItsOwnApproval() {
        val waiting = queued("note-a")
        assertFalse("another note's approval never sends this note", OsmNotesController.isApprovedUpload(waiting, "note-b", now))
        assertFalse("no approval at all never sends it", OsmNotesController.isApprovedUpload(waiting, "", now))
    }

    @Test fun approvedNoteIsSentOnlyWhenItMatchesThePreviewedText() {
        assertTrue(OsmNotesController.isApprovedUpload(queued("note-a"), "note-a", now))
        val edited = queued("note-a").put("text", "Speed sign reads 60 mph.")
        assertFalse("text changed after the preview is never sent", OsmNotesController.isApprovedUpload(edited, "note-a", now))
    }

    @Test fun uploadedOrUnconfirmedNotesAreNeverResent() {
        assertFalse(OsmNotesController.isApprovedUpload(queued("note-a").put("status", "Uploaded"), "note-a", now))
        assertFalse(OsmNotesController.isApprovedUpload(queued("note-a").put("firstHandConfirmed", false), "note-a", now))
        assertFalse(OsmNotesController.isApprovedUpload(queued("note-a").put("nextAttempt", now + 60_000L), "note-a", now))
    }
}
