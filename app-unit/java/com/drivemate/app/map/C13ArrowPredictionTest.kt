package com.drivemate.app.map

import com.drivemate.app.nav.GpsGapProgressPolicy
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C13.3 (pure logic, JVM): while GPS is lost the arrow's predicted position moves smoothly along
 * the route, is capped before an unconfirmed turn, and the camera anchor does not jump when stopped.
 */
class C13ArrowPredictionTest {
    @Test fun predictionIsCappedFiveMetresBeforeAnUnconfirmedTurn() {
        // Confirmed at 100 m, next turn 20 m ahead: the boundary sits at 115 m, not past the turn.
        assertEquals(115.0, GpsGapProgressPolicy.limitAtLoss(100.0, 20.0, 500.0), 1e-9)
        assertEquals("no distance to next: nothing to pass", 100.0, GpsGapProgressPolicy.limitAtLoss(100.0, -1.0, 500.0), 1e-9)
        assertEquals("never beyond the route", 500.0, GpsGapProgressPolicy.limitAtLoss(490.0, 400.0, 500.0), 1e-9)
    }

    @Test fun aLongGapAtSpeedStopsAtTheCapAndNeverPassesTheTurn() {
        val limit = GpsGapProgressPolicy.limitAtLoss(100.0, 20.0, 500.0)
        var along = 100.0
        var previous = along
        for (second in 1..60) {
            along = GpsGapProgressPolicy.advance(along, limit, gapMs = second * 1_000L, elapsedSeconds = 1.0,
                freshCarSpeedMps = null, lastGpsSpeedMps = 15.0)
            assertTrue("never past the unconfirmed turn at ${second}s: $along", along <= limit + 1e-9)
            assertTrue("never goes backwards", along >= previous - 1e-9)
            assertTrue("no jump larger than 1.5 s of travel: ${along - previous}", along - previous <= 15.0 * 1.5 + 1e-9)
            previous = along
        }
        assertEquals("waits at the cap", limit, along, 1e-9)
    }

    @Test fun aFreshStoppedCarHoldsTheArrowEvenIfLastGpsSpeedWasHigh() {
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, 115.0, gapMs = 30_000L, elapsedSeconds = 1.0,
            freshCarSpeedMps = 0.0, lastGpsSpeedMps = 15.0), 1e-9)
    }

    @Test fun lastGpsSpeedIsUsedOnlyBrieflyAfterLoss() {
        assertEquals("expired after 5 s: arrow holds", 100.0, GpsGapProgressPolicy.advance(100.0, 115.0, gapMs = 6_000L,
            elapsedSeconds = 1.0, freshCarSpeedMps = null, lastGpsSpeedMps = 15.0), 1e-9)
        assertEquals("a gap over 180 s is not an estimate any more", 100.0, GpsGapProgressPolicy.advance(100.0, 115.0,
            gapMs = 200_000L, elapsedSeconds = 1.0, freshCarSpeedMps = 15.0, lastGpsSpeedMps = 15.0), 1e-9)
    }

    @Test fun cameraAnchorDoesNotShuffleTheMapWhenStoppedAndReleasesWhenMoving() {
        val anchor = CameraAnchor()
        val home = LatLon(53.4000, -2.1600)
        val wander = LatLon(53.4000 + 2.0 / 111_320.0, -2.1600) // about 2 m of GPS wander
        assertEquals(home, anchor.anchor(home, 0.0))
        assertEquals("wander while stopped keeps the view", home, anchor.anchor(wander, 0.0))
        val moved = LatLon(53.4000 + 10.0 / 111_320.0, -2.1600) // 10 m, clearly moving
        assertEquals("moving on releases the hold", moved, anchor.anchor(moved, 6.0))
    }
}
