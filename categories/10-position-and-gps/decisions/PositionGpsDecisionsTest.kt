package com.drivemate.app.nav

import org.junit.Assert.*
import org.junit.Test

/** Engineering choices, not legal thresholds. All category 10 answers need owner review. */
class PositionGpsDecisionsTest {
    private fun fix(time: Long, lat: Double = 53.42, accuracy: Double = 5.0,
                    source: LocationSelectionPolicy.Source = LocationSelectionPolicy.Source.GPS) =
        LocationSelectionPolicy.Fix(source, time, lat, -2.26, accuracy, 0.0, 20.0)

    @Test fun coherentPreciseFixesRecoverFromBadStartupPosition() {
        val policy = LocationSelectionPolicy()
        assertTrue(policy.accept(fix(100_000, lat = 54.0), 100_000, true))
        assertFalse(policy.accept(fix(101_000), 101_000, true))
        assertFalse(policy.accept(fix(102_000), 102_000, true))
        assertTrue(policy.accept(fix(103_000), 103_000, true))
        assertEquals(53.42, policy.lastDelivered!!.latitude, 0.000001)
    }

    @Test fun impreciseFixesCannotEstablishJumpRecovery() {
        val policy = LocationSelectionPolicy()
        assertTrue(policy.accept(fix(100_000, lat = 54.0), 100_000, true))
        for (time in listOf(101_000L, 102_000L, 103_000L, 104_000L))
            assertFalse(policy.accept(fix(time, accuracy = 35.0), time, true))
        assertEquals(54.0, policy.lastDelivered!!.latitude, 0.000001)
    }

    @Test fun alternatingSourcesCannotPoolRecoveryEvidence() {
        val policy = LocationSelectionPolicy()
        assertTrue(policy.accept(fix(100_000, lat = 54.0), 100_000, true))
        assertFalse(policy.accept(fix(101_000), 101_000, true))
        assertFalse(policy.accept(fix(102_000, source = LocationSelectionPolicy.Source.GOOGLE), 102_000, true))
        assertFalse(policy.accept(fix(103_000), 103_000, true))
    }

    @Test fun staleInvalidAndNetworkCannotDriveNavigation() {
        val policy = LocationSelectionPolicy()
        assertFalse(policy.accept(fix(100_000), 108_001, true))
        assertFalse(policy.accept(fix(110_000, lat = Double.NaN), 110_000, true))
        assertFalse(policy.accept(fix(110_000, source = LocationSelectionPolicy.Source.NETWORK), 110_000, true))
        assertTrue(policy.accept(fix(110_000), 110_000, true))
        assertFalse(policy.accept(fix(110_000), 110_001, true))
        assertFalse(policy.authoritative(118_001))
    }

    @Test fun threeSecondUncertaintyDoesNotWaitForSpokenLossWarning() {
        val uncertainty = PositionUncertainty()
        assertFalse(uncertainty.update(100_000, false))
        assertTrue(uncertainty.update(103_000, false))
        assertFalse(GpsLossPolicy.shouldWarn(100_000, 159_999))
        assertTrue(GpsLossPolicy.shouldWarn(100_000, 160_000))
        assertFalse(uncertainty.update(104_000, true))
    }

    @Test fun browsingBacksOffButMovementResumesImmediately() {
        val policy = TrackingMotionPolicy()
        assertFalse(policy.stationary(0f, 100_000))
        assertTrue(policy.stationary(0f, 160_000))
        assertFalse(policy.stationary(2f, 160_001))
    }

    @Test fun unreliableCurrentRoadIsBlankAndHeadingHasNoInventedDefault() {
        assertEquals("", CurrentStreetPolicy.forNavigation("Main Street", 5.0, 5.0, "Other Road", 0.9))
        assertEquals("Main Street", CurrentStreetPolicy.forNavigation("Main Street", 5.0, 5.0, null, 0.0))
        assertNull(StartHeading.choose(null, 0f, null))
        assertEquals(90f, StartHeading.choose(90f, 3f, null)!!, 0.001f)
    }

    @Test fun parkedHeadingRejectsCorruptionAndKeepsOnlyNearbyRecentHeading() {
        var stored: String? = "NaN,-2.26,90,100000"
        var now = 100_000L
        val parked = ParkedHeading({ stored }, { stored = it }, { now })
        assertNull(parked.recall(LatLon(53.42, -2.26)))
        stored = null
        parked.note(LatLon(53.42, -2.26), -1f, 90f)
        assertNull(stored)
        parked.note(LatLon(53.42, -2.26), 0f, 90f)
        assertEquals(90f, parked.recall(LatLon(53.42, -2.26))!!, 0.001f)
        assertNull(parked.recall(LatLon(54.0, -2.26)))
        now += ParkedHeading.KEEP_MS + 1
        assertNull(parked.recall(LatLon(53.42, -2.26)))
        assertNull(stored)
    }
}
