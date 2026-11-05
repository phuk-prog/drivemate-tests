package com.drivemate.app

import com.drivemate.app.nav.LocationSelectionPolicy
import com.drivemate.app.nav.LocationStatusPolicy
import com.drivemate.app.nav.LocationTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Position quality (category A, old 6.3/6.4/6.5): precise fixes keep their GPS or Google status, poor or
 * network fixes are approximate, guidance trusts only fixes inside its accuracy range, and a precise fix
 * after an approximate one restores the precise status. Needs the LocationStatusPolicy extraction
 * (app candidate 5d1b8e4).
 */
class LocationStatusTest {
    private val gps = LocationSelectionPolicy.Source.GPS
    private val google = LocationSelectionPolicy.Source.GOOGLE
    private val network = LocationSelectionPolicy.Source.NETWORK

    @Test fun preciseGpsAndGoogleFixesKeepTheirStatus() {
        assertEquals(LocationTracker.Status.GPS, LocationStatusPolicy.status(gps, 5f))
        assertEquals(LocationTracker.Status.GOOGLE, LocationStatusPolicy.status(google, 5f))
    }

    @Test fun fixAtTheThresholdIsStillPrecise() {
        assertEquals(LocationTracker.Status.GPS, LocationStatusPolicy.status(gps, 50f))
        assertEquals(LocationTracker.Status.GOOGLE, LocationStatusPolicy.status(google, 50f))
    }

    @Test fun fixJustOverTheThresholdIsApproximate() {
        assertEquals(LocationTracker.Status.APPROXIMATE, LocationStatusPolicy.status(gps, 50.5f))
        assertEquals(LocationTracker.Status.APPROXIMATE, LocationStatusPolicy.status(google, 50.5f))
    }

    @Test fun networkFixesAreAlwaysApproximate() {
        assertEquals(LocationTracker.Status.APPROXIMATE, LocationStatusPolicy.status(network, 1f))
    }

    @Test fun guidanceTrustsOnlyTheFixesInsideItsRange() {
        assertTrue(LocationStatusPolicy.guidanceHealthy(0.1f))
        assertTrue(LocationStatusPolicy.guidanceHealthy(25f))
        assertFalse(LocationStatusPolicy.guidanceHealthy(0.05f))
        assertFalse(LocationStatusPolicy.guidanceHealthy(25.1f))
        assertFalse(LocationStatusPolicy.guidanceHealthy(Float.NaN))
        assertFalse(LocationStatusPolicy.guidanceHealthy(Float.POSITIVE_INFINITY))
    }

    /** Recovery after a poor stretch: the status follows each fix, so a precise fix restores GPS. */
    @Test fun preciseFixAfterApproximateFixRestoresGps() {
        assertEquals(LocationTracker.Status.APPROXIMATE, LocationStatusPolicy.status(gps, 120f))
        assertEquals(LocationTracker.Status.GPS, LocationStatusPolicy.status(gps, 8f))
    }
}
