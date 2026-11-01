package com.drivemate.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Category 23.1: a human confirmation of a lane, junction or limit is offered only when the car is parked. */
class C23ParkedConfirmationTest {
    @Test fun stoppedFreshFixAllowsTheParkedQuestion() {
        assertTrue(canRequestParkedConfirmation(0f, 0L))
        assertTrue(canRequestParkedConfirmation(0.4f, 5_000L))
    }

    @Test fun movingOrMissingSpeedNeverAllowsIt() {
        assertFalse(canRequestParkedConfirmation(0.5f, 1_000L))
        assertFalse(canRequestParkedConfirmation(null, 1_000L))
        assertFalse(canRequestParkedConfirmation(Float.NaN, 1_000L))
        assertFalse(canRequestParkedConfirmation(-1f, 1_000L))
    }

    @Test fun staleOrFutureFixNeverAllowsIt() {
        assertFalse(canRequestParkedConfirmation(0f, 5_001L))
        assertFalse(canRequestParkedConfirmation(0f, -1L))
    }
}
