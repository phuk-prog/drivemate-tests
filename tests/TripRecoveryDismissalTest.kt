package com.drivemate.app

import com.drivemate.app.controller.NavigationReadiness
import com.drivemate.app.controller.TripRecovery
import com.drivemate.app.data.Place
import com.drivemate.app.data.TripStore
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Test

class TripRecoveryDismissalTest {
    private val now = 1_001_000L
    private val destination = Place("Synthetic destination", "", LatLon(53.48, -2.24))
    private val saved = TripStore.Saved(null, destination, emptyList(), 1_000_000L)

    private fun ready(age: Long? = 0L, accuracy: Double? = 10.0, authoritative: Boolean = true,
                      precise: Boolean = true, services: Boolean = true, settings: Boolean = true,
                      quality: Int? = 100): Boolean =
        NavigationReadiness.canStart(settings, precise, services, age, accuracy, quality, authoritative)

    private fun dismiss(positionReady: Boolean, here: LatLon = destination.pos, accuracy: Double = 10.0) =
        TripRecovery.canDismissAsArrived(saved, here, accuracy, now, positionReady)

    @Test fun staleMissingAndFutureFixesPreserveTrip() {
        for (age in listOf<Long?>(8_001L, 60_000L, null, -1L))
            assertFalse("Unsafe fix age $age must preserve trip", dismiss(ready(age = age)))
    }

    @Test fun untrustedFixPreservesTrip() {
        assertFalse(dismiss(ready(authoritative = false)))
    }

    @Test fun unavailableSettingsPermissionOrServicesPreserveTrip() {
        assertFalse(dismiss(ready(settings = false)))
        assertFalse(dismiss(ready(precise = false)))
        assertFalse(dismiss(ready(services = false)))
    }

    @Test fun missingOrPoorQualityPreservesTrip() {
        assertFalse(dismiss(ready(quality = null)))
        assertFalse(dismiss(ready(quality = 0)))
    }

    @Test fun invalidAccuracyPreservesTrip() {
        for (accuracy in listOf<Double?>(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 50.1))
            assertFalse(dismiss(ready(accuracy = accuracy), accuracy = accuracy ?: Double.NaN))
    }

    @Test fun freshTrustedDestinationFixStillDismissesTrip() {
        for (age in listOf(0L, 8_000L)) for (accuracy in listOf(0.1, 10.0, 50.0))
            assertTrue(dismiss(ready(age = age, accuracy = accuracy), accuracy = accuracy))
    }

    @Test fun freshFixAwayFromDestinationPreservesTrip() {
        assertFalse(dismiss(ready(), here = LatLon(53.50, -2.20)))
    }

    @Test fun invalidSavedTripCannotBeDismissedAsArrived() {
        val expired = TripStore.Saved(null, destination, emptyList(), now - TripRecovery.MAX_AGE_MS - 1)
        assertFalse(TripRecovery.canDismissAsArrived(expired, destination.pos, 10.0, now, ready()))
    }
}
