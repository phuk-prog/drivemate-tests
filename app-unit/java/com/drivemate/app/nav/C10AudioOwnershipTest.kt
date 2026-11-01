package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 10.3 pure checks: after a call, a device change or a background/resume, a voice
 * route is only restored for the device that is still in use, and a question answer survives
 * an interruption while stale directions do not. No AudioManager is involved.
 */
class C10AudioOwnershipTest {
    /** 10.3: the previous communication mode is restored only while still in a call and on the same or newly accepted device. */
    @Test fun q10_3_restoreOnlyForTheDeviceStillInUse() {
        assertFalse("call already ended", VoiceRoutingOwnership.mayRestoreMode(false, 1, 1, 1))
        assertTrue("same device still in use", VoiceRoutingOwnership.mayRestoreMode(true, 1, 1, 1))
        assertTrue("no device reported, still in a call", VoiceRoutingOwnership.mayRestoreMode(true, 1, null, null))
        assertFalse("device changed and was never accepted", VoiceRoutingOwnership.mayRestoreMode(true, 1, null, 2))
        assertTrue("the newly accepted device is in use", VoiceRoutingOwnership.mayRestoreMode(true, 1, 2, 2))
        assertFalse("a different device from the accepted one", VoiceRoutingOwnership.mayRestoreMode(true, 1, 3, 2))
    }

    /** 10.3: after an interruption, only the driver's question answer is kept; directions are not replayed. */
    @Test fun q10_3_interruptionKeepsOnlyAnswers() {
        val q = VoiceMessageQueue()
        assertTrue(q.add("Turn left", alert = false, interrupt = false, now = 1_000L))
        assertTrue(q.add("Here is your answer", alert = true, interrupt = false, now = 1_000L, answer = true))
        q.keepAnswers()
        assertEquals("Here is your answer", q.next(1_500L)!!.text)
        assertNull(q.next(1_500L))
    }
}
