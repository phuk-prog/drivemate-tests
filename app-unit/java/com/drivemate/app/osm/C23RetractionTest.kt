package com.drivemate.app.osm

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 23.4: only the submitted note the driver approves can be retracted, and never another note. */
class C23RetractionTest {
    private fun note(identity: String, status: String = "Submitted", noteId: Long = 42L) = JSONObject()
        .put("identity", identity).put("status", status).put("noteId", noteId)

    @Test fun approvedSubmittedNoteCanBeRetracted() {
        assertTrue(OsmNotesController.isApprovedRetraction(note("n-a"), "n-a"))
    }

    @Test fun otherNoteOrNoApprovalNeverRetracts() {
        assertFalse(OsmNotesController.isApprovedRetraction(note("n-a"), "n-b"))
        assertFalse(OsmNotesController.isApprovedRetraction(note("n-a"), ""))
    }

    @Test fun queuedRetractedOrUnconfirmedNotesAreNotRetracted() {
        assertFalse(OsmNotesController.isApprovedRetraction(note("n-a", "Queued", 0L), "n-a"))
        assertFalse(OsmNotesController.isApprovedRetraction(note("n-a", "Retracted"), "n-a"))
        assertFalse(OsmNotesController.isApprovedRetraction(note("n-a", "Retraction unconfirmed"), "n-a"))
    }
}
