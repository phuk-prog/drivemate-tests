package com.drivemate.app

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.drivemate.app.nav.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import java.time.Duration

/** A.1–A.4: real Android tracker callbacks, quality rejection, stale evidence and recovery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class,
    shadows = [PositionCategoryTest.NoGoogleServices::class])
@LooperMode(LooperMode.Mode.PAUSED)
class PositionCategoryTest {
    @Implements(GoogleApiAvailability::class)
    class NoGoogleServices {
        @Implementation fun isGooglePlayServicesAvailable(context: Context): Int = ConnectionResult.SERVICE_MISSING
    }

    private fun evidence(label: String, tracker: LocationTracker, delivered: Int) {
        val s = tracker.state.value
        println("Position diagnostics: check=$label status=${s.status} precise=${s.precisePermission} " +
            "authoritative=${s.authoritativeFix} reliable=${s.guidanceReliable} weak=${s.weakAccuracy} delivered=$delivered")
    }

    @Test fun permissionQualityLossAndRecoveryUseActualTrackerCallbacks() {
        val app = RuntimeEnvironment.getApplication() as Application
        val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val shadowApp = Shadows.shadowOf(app)
        val manager = app.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val shadowLocation = Shadows.shadowOf(manager)
        shadowLocation.setLocationEnabled(true)
        shadowLocation.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowApp.denyPermissions(*permissions)
        var delivered = 0
        val tracker = LocationTracker(app, { delivered++ })
        fun idle() = Shadows.shadowOf(Looper.getMainLooper()).idle()
        fun fix(accuracy: Float) {
            SystemClock.sleep(100)
            shadowLocation.simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 53.4808; longitude = -2.2374
                this.accuracy = accuracy
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                time = System.currentTimeMillis()
            })
            idle()
        }
        try {
            tracker.start(LocationTracker.Mode.NAVIGATION)
            evidence("denied", tracker, delivered)
            assertEquals(LocationTracker.Status.PERMISSION_REQUIRED, tracker.state.value.status)
            assertEquals(0, delivered)

            shadowApp.grantPermissions(*permissions)
            tracker.start(LocationTracker.Mode.NAVIGATION)
            fix(5f)
            evidence("grant-and-fix", tracker, delivered)
            assertEquals(1, delivered)
            assertTrue(tracker.state.value.authoritativeFix)
            assertTrue(tracker.state.value.guidanceReliable)

            fix(80f)
            evidence("poor-fix-rejected", tracker, delivered)
            assertEquals(1, delivered)
            assertTrue(tracker.state.value.weakAccuracy)
            assertFalse(tracker.state.value.guidanceReliable)

            fix(8f)
            evidence("quality-recovery", tracker, delivered)
            assertEquals(2, delivered)
            assertFalse(tracker.state.value.weakAccuracy)
            assertTrue(tracker.state.value.guidanceReliable)

            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
            evidence("stale", tracker, delivered)
            assertFalse(tracker.state.value.authoritativeFix)
            assertFalse(tracker.state.value.guidanceReliable)

            fix(8f)
            evidence("gap-recovery", tracker, delivered)
            assertEquals(3, delivered)
            assertTrue(tracker.state.value.authoritativeFix)
            assertTrue(tracker.state.value.guidanceReliable)

            shadowApp.denyPermissions(*permissions)
            tracker.refreshAvailability()
            evidence("revoked", tracker, delivered)
            assertEquals(LocationTracker.Status.PERMISSION_REQUIRED, tracker.state.value.status)
            assertFalse(tracker.state.value.authoritativeFix)
            assertFalse(tracker.state.value.guidanceReliable)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
            shadowApp.grantPermissions(*permissions)
            tracker.start(LocationTracker.Mode.NAVIGATION)
            fix(5f)
            evidence("permission-restored", tracker, delivered)
            assertEquals(4, delivered)
            assertTrue(tracker.state.value.guidanceReliable)
        } finally {
            tracker.stop()
            evidence("stopped", tracker, delivered)
            assertFalse(tracker.state.value.authoritativeFix)
            assertFalse(tracker.state.value.weakAccuracy)
        }
    }

    @Test fun boundariesRejectInvalidEvidenceAndRestoreReliablePosition() {
        val selected = LocationSelectionPolicy.Fix(LocationSelectionPolicy.Source.GPS, 1_000,
            53.4808, -2.2374, 5.0)
        val poor = selected.copy(elapsedMs = 1_100, accuracyM = 80.0)
        println("Position diagnostics: check=quality-observation now_ms=1100 selected_ms=1000")
        assertTrue(LocationStatusPolicy.observesQuality(poor, selected, 1_100))
        assertFalse(LocationStatusPolicy.observesQuality(poor.copy(source = LocationSelectionPolicy.Source.NETWORK), selected, 1_100))
        assertFalse(LocationStatusPolicy.observesQuality(poor.copy(elapsedMs = 999), selected, 1_100))
        assertFalse(LocationStatusPolicy.observesQuality(poor.copy(accuracyM = Double.NaN), selected, 1_100))
        assertFalse(LocationStatusPolicy.observesQuality(poor, selected, 10_000))
        assertFalse(LocationStatusPolicy.observesQuality(poor.copy(elapsedMs = 1_101), selected, 1_100))
        var checked = 0
        for (accuracy in listOf<Double?>(null, -1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 25.1, 50.1)) {
            println("Position diagnostics: check=invalid-accuracy index=$checked")
            assertFalse(PositionUncertainty.reliable(true, true, accuracy, 0))
            checked++
        }
        for (age in listOf<Long?>(null, -1, 2_501, 60_001)) {
            println("Position diagnostics: check=invalid-age index=$checked")
            assertFalse(PositionUncertainty.reliable(true, true, 5.0, age))
            checked++
        }
        assertTrue(PositionUncertainty.reliable(true, true, 25.0, 2_500))
        assertFalse(PositionUncertainty.reliable(false, true, 5.0, 0))
        assertFalse(PositionUncertainty.reliable(true, false, 5.0, 0))
        assertTrue(PositionUncertainty.followable(true, true, 50.0, 6_000))
        assertFalse(PositionUncertainty.followable(true, true, 50.0, 6_001))
        val warning = PositionUncertainty()
        assertFalse(warning.update(0, false))
        assertFalse(warning.update(9_999, false))
        assertTrue(warning.update(10_000, false))
        assertFalse(warning.update(10_001, true))
        assertFalse(warning.update(10_002, false))
        assertFalse(warning.update(20_001, false))
        assertTrue(warning.update(20_002, false))
        println("Position diagnostics: check=boundaries-complete invalid_cases=$checked")
    }

    @Test fun estimateExpiresAtSixtySecondsWithoutPassingUnconfirmedManeuver() {
        val limit = GpsGapProgressPolicy.limitAtLoss(100.0, 30.0, 1_000.0)
        println("Position diagnostics: check=estimate limit=$limit gaps_ms=59999,60000,60001")
        assertEquals(125.0, limit, 0.0)
        assertEquals(110.0, GpsGapProgressPolicy.advance(100.0, limit, 59_999, 1.0, 10.0, 10.0), 0.0)
        assertEquals(110.0, GpsGapProgressPolicy.advance(100.0, limit, 60_000, 1.0, 10.0, 10.0), 0.0)
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, limit, 60_001, 1.0, 10.0, 10.0), 0.0)
        assertEquals(125.0, GpsGapProgressPolicy.advance(120.0, limit, 4_000, 1.0, 10.0, 10.0), 0.0)
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, limit, 5_001, 1.0, null, 10.0), 0.0)
        assertEquals(100.0, GpsGapProgressPolicy.advance(100.0, limit, 4_000, 1.0, 0.0, 10.0), 0.0)
    }
}
