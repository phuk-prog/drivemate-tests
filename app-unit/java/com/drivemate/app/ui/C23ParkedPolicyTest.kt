package com.drivemate.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** Category 23.1: the allowed parked-only confirmations, and answers frozen with their evidence and missing facts. */
class C23ParkedPolicyTest {
    @Test fun everyAllowedKindIsOfferedOnlyWhenParked() {
        assertEquals(ParkedConfirmationKind.values().toSet(), ParkedConfirmationPolicy.askable(0f, 1_000L))
        assertTrue(ParkedConfirmationPolicy.askable(13.4f, 1_000L).isEmpty())
        assertTrue(ParkedConfirmationPolicy.askable(null, 1_000L).isEmpty())
        assertTrue(ParkedConfirmationPolicy.askable(0f, 5_001L).isEmpty())
    }

    @Test fun answerIsFrozenWithItsEvidence() {
        val answer = ParkedConfirmationPolicy.freeze(ParkedConfirmationKind.SPEED_LIMIT, " ev-1 ", " 30 ", emptyList())
        assertEquals(ParkedAnswer(ParkedConfirmationKind.SPEED_LIMIT, "ev-1", "30", emptyList()), answer)
    }

    @Test fun unknownAnswerKeepsItsMissingFacts() {
        val answer = ParkedConfirmationPolicy.freeze(ParkedConfirmationKind.LANE_COUNT, "ev-2", null, listOf("sign hidden by van", " "))
        assertEquals(listOf("sign hidden by van"), answer!!.missingFacts)
        assertNull(answer.value)
    }

    @Test fun answerWithoutEvidenceOrWithoutAnyFactIsRefused() {
        assertNull(ParkedConfirmationPolicy.freeze(ParkedConfirmationKind.EXIT_COUNT, " ", "2", emptyList()))
        assertNull(ParkedConfirmationPolicy.freeze(ParkedConfirmationKind.EXIT_COUNT, "ev-3", "  ", listOf(" ")))
        assertNull(ParkedConfirmationPolicy.freeze(ParkedConfirmationKind.EXIT_COUNT, "ev-3", "x".repeat(81), emptyList()))
        assertFalse(ParkedConfirmationPolicy.askable(0.2f, 0L).isEmpty())
    }
}
