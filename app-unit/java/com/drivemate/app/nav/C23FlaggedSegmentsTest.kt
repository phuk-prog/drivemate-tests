package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Category 23.2: after parking, only the segments that were flagged are offered, and drive-only items never become notes. */
class C23FlaggedSegmentsTest {
    private fun obs(id: String, answer: ReviewAnswer = ReviewAnswer.UNANSWERED, publicNote: Boolean = true) = RoadObservation(
        id = id, latitude = 53.40, longitude = -2.20, roadSection = "A6 Hillgate", direction = "north",
        question = "Is the limit 30?", firstLabel = "30 mph", secondLabel = "40 mph",
        firstProvider = "Provider A", secondProvider = "Provider B", observedAtMs = 1_800_000_000_000L,
        answer = answer, publicNote = publicNote,
    )

    @Test fun onlyFlaggedUnansweredSegmentsAreOffered() {
        val review = PostDriveReview(listOf(obs("flag-1"), obs("flag-2"), obs("done", ReviewAnswer.FIRST)))
        assertEquals(listOf("flag-1", "flag-2"), review.pending().map { it.id })
    }

    @Test fun driveOnlyItemIsNeverOfferedAsAPublicNote() {
        val review = PostDriveReview(listOf(obs("traffic", ReviewAnswer.FIRST, publicNote = false)))
        assertNull(review.noteDraft("traffic"))
    }
}
