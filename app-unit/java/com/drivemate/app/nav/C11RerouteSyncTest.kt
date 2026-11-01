package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 11.5 pure checks: after a reroute, directions from the old route are never spoken,
 * the new route's first direction is, and a manoeuvre is not announced twice.
 */
class C11RerouteSyncTest {
    /** 11.5: a direction queued before the reroute is dropped, and the new route's direction is spoken. */
    @Test fun q11_5_oldDirectionDroppedNewDirectionSpoken() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Turn left", alert = false, interrupt = false, now = 1_000L))
        q.newRoute()
        assertTrue(q.add("Turn right", alert = false, interrupt = false, now = 1_200L))
        assertEquals("Turn right", q.next(1_300L)!!.text)
        assertNull("no stale announcement after the reroute", q.next(1_300L))
    }

    /** 11.5: advancing to a manoeuvre drops directions queued for the previous one. */
    @Test fun q11_5_manoeuvreAdvanceDropsPreviousDirection() {
        val q = VoiceMessageQueue()
        assertTrue(q.newManoeuvre(1))
        assertTrue(q.add("In 300 yards, turn left", alert = false, interrupt = false, now = 1_000L))
        assertTrue(q.newManoeuvre(2))
        assertNull(q.next(1_100L))
    }

    /** 11.5: the same manoeuvre index is not treated as new, so it is not announced again. */
    @Test fun q11_5_sameManoeuvreIsNotAnnouncedTwice() {
        val q = VoiceMessageQueue()
        assertTrue(q.newManoeuvre(2))
        assertFalse(q.newManoeuvre(2))
    }
}
