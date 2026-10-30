package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M03: estimated frame motion is never proof that an unconfirmed junction was passed. */
class M03ArrowJunctionCapTest {
    @Test fun fastArrowAnimationStopsBeforeUnconfirmedJunction() {
        val limit = GpsGapProgressPolicy.limitAtLoss(100.0, 20.0, 500.0)
        assertEquals(115.0, limit, 0.001)
        val arrow = ArrowPredictor()
        arrow.onFix(1_000_000_000L, 100.0, 12.0, 0.0, true, maxAlong = limit)
        assertEquals(112.0, arrow.alongAt(2_000_000_000L)!!, 0.001)
        assertEquals(limit, arrow.alongAt(3_000_000_000L)!!, 0.001)
        assertEquals(limit, arrow.alongAt(3_500_000_000L)!!, 0.001)
        assertNull("A stale fix cannot continue animation", arrow.alongAt(4_100_000_000L))
    }

    @Test fun onlyFreshMeasuredFixOpensTheNextJunctionBoundary() {
        val arrow = ArrowPredictor()
        arrow.onFix(1_000_000_000L, 100.0, 15.0, 0.0, true,
            GpsGapProgressPolicy.limitAtLoss(100.0, 20.0, 500.0))
        assertEquals(115.0, arrow.alongAt(3_000_000_000L)!!, 0.001)
        // Vehicle is now *measured* past the junction. That is new evidence, not prediction.
        arrow.onFix(3_200_000_000L, 123.0, 12.0, 0.0, true,
            GpsGapProgressPolicy.limitAtLoss(123.0, 80.0, 500.0))
        assertEquals(135.0, arrow.alongAt(4_200_000_000L)!!, 0.001)
    }

    @Test fun brakingStillRespectsBothStopAndJunctionLimits() {
        val arrow = ArrowPredictor()
        arrow.onFix(1_000_000_000L, 90.0, 10.0, -4.0, true, maxAlong = 105.0)
        assertEquals(102.0, arrow.alongAt(3_000_000_000L)!!, 0.001)
        // MAX_SECONDS is two seconds: braking never extends extrapolation past that boundary.
        assertEquals(102.0, arrow.alongAt(3_500_000_000L)!!, 0.001)
    }

    @Test fun invalidBoundaryFailsClosedAndUnusableSpeedCannotMoveArrow() {
        val arrow = ArrowPredictor()
        arrow.onFix(1_000_000_000L, 100.0, 20.0, 0.0, true, Double.NaN)
        assertEquals(100.0, arrow.alongAt(3_000_000_000L)!!, 0.001)
        arrow.onFix(4_000_000_000L, 100.0, 20.0, 0.0, false, 200.0)
        assertNull(arrow.alongAt(5_000_000_000L))
    }
}
