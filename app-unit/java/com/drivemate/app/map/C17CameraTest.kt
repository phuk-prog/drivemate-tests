package com.drivemate.app.map

import com.drivemate.app.nav.PositionCameraPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** C17.2/C17.3: follow/re-centre transitions and a camera heading without spins or jumps. */
class C17CameraTest {
    @Test fun autoRecentreOnlyWhileNavigatingNotFollowingAndOnAFollowablePosition() {
        assertTrue(PositionCameraPolicy.mayAutoRecenter(navigating = true, following = false, reliable = true))
        assertFalse("already following", PositionCameraPolicy.mayAutoRecenter(true, true, true))
        assertFalse("browsing keeps the driver's own view", PositionCameraPolicy.mayAutoRecenter(false, false, true))
        assertFalse("a GPS gap never pulls the view away", PositionCameraPolicy.mayAutoRecenter(true, false, false))
    }

    @Test fun firstOpeningCentresButAnUncertainReturnKeepsTheChosenView() {
        assertTrue(PositionCameraPolicy.mayInitiallyCenter(hasPriorView = false, reliable = false))
        assertFalse(PositionCameraPolicy.mayInitiallyCenter(hasPriorView = true, reliable = false))
        assertTrue(PositionCameraPolicy.mayInitiallyCenter(hasPriorView = true, reliable = true))
    }

    @Test fun headingHoldsWhenStoppedOrTheBearingIsPoor() {
        val h = CameraHeading()
        assertNull(h.update(0, null, 10.0))
        assertEquals(90.0, h.update(0, 90.0, 10.0)!!, 1e-9)
        assertEquals("stopped at lights: no spin", 90.0, h.update(1_000, 180.0, 0.5)!!, 1e-9)
        assertEquals("poor bearing ignored", 90.0, h.update(2_000, 180.0, 10.0, bearingAccuracyDeg = 60.0)!!, 1e-9)
    }

    @Test fun turnsTheShortWayRoundAndSmoothly() {
        val h = CameraHeading()
        h.update(0, 350.0, 15.0)
        val next = h.update(250, 10.0, 15.0)!!
        assertTrue("moved toward 10° via north, not back through 180°: $next", next > 350.0 || next < 10.0)
        var last = next
        for (t in 2..20) {
            val v = h.update(t * 250L, 10.0, 15.0)!!
            val step = ((v - last + 540) % 360) - 180
            assertTrue("each 250 ms step is small and forward: $step", step >= -1e-9 && step < 15.0)
            last = v
        }
        assertEquals(10.0, last, 0.5)
    }

    @Test fun crawlingTurnsNoFasterThanTheCrawlRate() {
        val h = CameraHeading()
        h.update(0, 0.0, 2.0)
        val v = h.update(1_000, 170.0, 2.0)!!
        assertTrue(v <= CameraHeading.CRAWL_RATE + 1e-9)
    }

    @Test fun afterATunnelGapItFacesTheNewDirectionAtOnce() {
        val h = CameraHeading()
        h.update(0, 0.0, 20.0)
        assertEquals(120.0, h.update(6_000, 120.0, 20.0)!!, 1e-9)
    }
}
