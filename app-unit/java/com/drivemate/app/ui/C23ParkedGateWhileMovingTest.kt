package com.drivemate.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

/** Category 23.5: while the car is moving, the parked question is never offered and the speed is not treated as zero. */
class C23ParkedGateWhileMovingTest {
    @Test fun anyMovingSpeedBlocksTheParkedQuestion() {
        for (speed in listOf(0.5f, 1f, 5f, 13.4f, 30f)) {
            assertFalse("speed $speed m/s must not allow confirmation", canRequestParkedConfirmation(speed, 1_000L))
        }
    }

    @Test fun staleSpeedIsNotReadAsStopped() {
        assertNull(measuredSpeed(0f, 5_001L))
        assertFalse(canRequestParkedConfirmation(measuredSpeed(0f, 5_001L), 5_001L))
    }

    @Test fun freshMeasuredSpeedIsKeptAsItIs() {
        assertEquals(13.4f, measuredSpeed(13.4f, 1_000L)!!, 0f)
    }
}
