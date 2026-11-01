package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Category 8.1 (roundabout ring geometry, unmapped exits): without the map's own exit places the
 * picture spaces the passed exits evenly and says so (othersReal = false). It never claims the
 * real places of exits it has no data for, and it never draws an exit that is not taken.
 */
class Category8RingGeometryTest {
    @Test fun unmappedExitsAreDrawnEvenlyAndFlagged() {
        val arms = RoundaboutPicture.arms(exitAngle = 90f, exit = 3, leftDriving = false, travelled = emptyList())
        assertFalse(arms.othersReal)
        assertEquals(2, arms.others.size) // exits 1 and 2 are passed, so the third is the one taken
    }

    @Test fun noExitNumberDrawsNoPassedExits() {
        val arms = RoundaboutPicture.arms(exitAngle = 90f, exit = 0, leftDriving = false, travelled = emptyList())
        assertFalse(arms.othersReal)
        assertTrue(arms.others.isEmpty())
    }

    @Test fun mappedExitPlacesAreUsedWhenPresent() {
        val arms = RoundaboutPicture.arms(exitAngle = 90f, exit = 2, leftDriving = false,
            travelled = listOf(60, 150, 240), exitTravelled = 150)
        assertTrue(arms.othersReal)
    }

    @Test fun drivingSideSetsTheRingDirectionAndEndsAtTheSameExit() {
        // Right-hand traffic goes anticlockwise, left-hand clockwise; both end at the exit taken.
        val right = RoundaboutPicture.arms(exitAngle = 90f, exit = 2, leftDriving = false, travelled = emptyList())
        val left = RoundaboutPicture.arms(exitAngle = 90f, exit = 2, leftDriving = true, travelled = emptyList())
        assertTrue(right.sweep < 0f)
        assertTrue(left.sweep > 0f)
        assertEquals(right.taken, left.taken, 0.001f)
        assertEquals(right.taken, ((180f + right.sweep) % 360f + 360f) % 360f, 0.001f)
        assertEquals(left.taken, ((180f + left.sweep) % 360f + 360f) % 360f, 0.001f)
    }
}
