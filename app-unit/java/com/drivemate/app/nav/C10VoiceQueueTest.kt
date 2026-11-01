package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 10 pure queue checks: 10.1 (prompt timing and duplicate-cue suppression) and 10.4
 * (stale speech dropped; a new route or manoeuvre removes directions, not warnings).
 * These drive VoiceMessageQueue directly, with no Android TextToSpeech or AudioManager.
 */
class C10VoiceQueueTest {
    /** 10.1: the same direction is not repeated within 10 s, and is allowed again after that. */
    @Test fun q10_1_sameDirectionNotRepeatedWithinTenSeconds() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("In 300 yards, turn left", alert = false, interrupt = false, now = 1_000L))
        q.markSpoken(q.next(1_000L)!!, 1_000L)
        assertFalse("duplicate cue within 10 s", q.add("In 300 yards, turn left", alert = false, interrupt = false, now = 5_000L))
        assertTrue("allowed again after 10 s", q.add("In 300 yards, turn left", alert = false, interrupt = false, now = 12_000L))
    }

    /** 10.1: a repeated warning is held back for 30 s, not 10 s. */
    @Test fun q10_1_warningNotRepeatedWithinThirtySeconds() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 1_000L))
        q.markSpoken(q.next(1_000L)!!, 1_000L)
        assertFalse(q.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 20_000L))
        assertTrue(q.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 31_001L))
    }

    /** 10.1: the same text queued twice at once is spoken once, not twice. */
    @Test fun q10_1_identicalQueuedTextIsSpokenOnce() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Turn left now", alert = false, interrupt = false, now = 1_000L))
        assertFalse(q.add("Turn left   now", alert = false, interrupt = false, now = 1_001L))
        assertEquals("Turn left now", q.next(1_002L)!!.text)
        assertNull(q.next(1_003L))
    }

    /** 10.4: a direction older than the 8 s life is dropped and never spoken late. */
    @Test fun q10_4_staleDirectionExpires() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Turn left", alert = false, interrupt = false, now = 1_000L))
        assertNull("not spoken once it is out of date", q.next(1_000L + VoiceMessage.DIRECTION_LIFE_MS + 1))
    }

    /** 10.4: a new route removes queued directions so the old instruction cannot be spoken. */
    @Test fun q10_4_newRouteDropsQueuedDirections() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Turn left", alert = false, interrupt = false, now = 1_000L))
        q.newRoute()
        assertNull(q.next(1_500L))
    }

    /** 10.4: a warning still queued when the route changes is kept, because it is not a direction. */
    @Test fun q10_4_warningSurvivesNewRoute() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Roadworks ahead. Follow road signs.", alert = true, interrupt = false, now = 1_000L))
        q.newRoute()
        assertEquals("Roadworks ahead. Follow road signs.", q.next(1_500L)!!.text)
    }
}
