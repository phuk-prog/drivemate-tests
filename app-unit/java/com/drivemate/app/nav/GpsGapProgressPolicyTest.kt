package com.drivemate.app.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** M03 GPS tunnel/stop-and-go regressions; source geometry is illustrative, not road-law truth. */
class GpsGapProgressPolicyTest {
    @Test fun neverCrossesAnUnconfirmedTurnDuringLongTunnel() {
        val cap = GpsGapProgressPolicy.limitAtLoss(100.0, 35.0, 500.0)
        assertEquals(130.0, cap, 0.001)
        var position = 100.0
        repeat(20) {
            position = GpsGapProgressPolicy.advance(position, cap, 10_000L + it * 1000,
                1.0, freshCarSpeedMps = 14.0, lastGpsSpeedMps = 14.0)
        }
        assertEquals("An estimate never proves the junction was passed", 130.0, position, 0.001)
    }

    @Test fun oldGpsSpeedExpiresButFreshCarSpeedMayContinueToTheCap() {
        val cap = 200.0
        assertEquals(110.0, GpsGapProgressPolicy.advance(100.0, cap, 3_500,
            1.0, null, 10.0), 0.001)
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, cap, 8_000,
            1.0, null, 10.0), 0.001)
        assertEquals(107.0, GpsGapProgressPolicy.advance(100.0, cap, 8_000,
            1.0, 7.0, 10.0), 0.001)
    }

    @Test fun stoppedCarSensorOverridesOlderMovingGps() {
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, 180.0, 3_000,
            1.0, freshCarSpeedMps = 0.0, lastGpsSpeedMps = 22.0), 0.001)
    }

    @Test fun shortRouteAndArrivalCannotProduceNegativeProgress() {
        assertEquals(27.0, GpsGapProgressPolicy.limitAtLoss(25.0, 3.0, 40.0), 0.001)
        assertEquals(25.0, GpsGapProgressPolicy.limitAtLoss(25.0, 2.0, 40.0), 0.001)
        assertEquals(0.0, GpsGapProgressPolicy.limitAtLoss(0.0, 0.0, 0.0), 0.001)
        assertEquals(25.0, GpsGapProgressPolicy.limitAtLoss(25.0, Double.NaN, 40.0), 0.001)
    }

    @Test fun clockJumpUntrustedSpeedAndLongLossDoNotMovePosition() {
        val p = 70.0
        assertEquals(p, GpsGapProgressPolicy.advance(p, 100.0, 181_000,
            1.0, 15.0, 15.0), 0.001)
        assertEquals(p, GpsGapProgressPolicy.advance(p, 100.0, 3_000,
            -3.0, 15.0, 15.0), 0.001)
        assertEquals(p, GpsGapProgressPolicy.advance(p, 100.0, 8_000,
            1.0, Double.NaN, Double.NaN), 0.001)
        assertEquals(92.5, GpsGapProgressPolicy.advance(p, 100.0, 3_000,
            90.0, 15.0, 15.0), 0.001)
    }

    @Test fun recoveryRequiresNewMeasuredFixToAdvanceTheNextManeuver() {
        val cap = GpsGapProgressPolicy.limitAtLoss(90.0, 16.0, 180.0)
        assertEquals(101.0, cap, 0.001)
        assertEquals(cap, GpsGapProgressPolicy.advance(cap, cap, 20_000,
            1.0, 15.0, 15.0), 0.001)
        // A new confident GPS fix establishes a new anchor and a later maneuver limit.
        val newCap = GpsGapProgressPolicy.limitAtLoss(110.0, 45.0, 180.0)
        assertEquals(150.0, newCap, 0.001)
    }
}
