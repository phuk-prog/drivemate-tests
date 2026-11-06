package com.drivemate.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.data.CarIcon
import com.drivemate.app.map.MapController
import com.drivemate.app.map.StyleGeneration
import com.drivemate.app.nav.LocationTracker
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs the real Activity, Android location callbacks and MapLibre native renderer. No native stubs. */
class CategoryAEmulatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private lateinit var activity: MainActivity
    private lateinit var session: NavigationSession
    private lateinit var controller: MapController
    private var lastInjectedAt = 0L
    private var injectedSamples = 0

    private fun <T> main(block: () -> T): T {
        var value: T? = null
        var error: Throwable? = null
        instrumentation.runOnMainSync { try { value = block() } catch (t: Throwable) { error = t } }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST") return value as T
    }

    private fun report(phase: String, vararg fields: Pair<String, Any>) {
        val json = JSONObject().put("phase", phase)
        fields.forEach { json.put(it.first, it.second) }
        instrumentation.sendStatus(2, Bundle().apply { putString("category_a", json.toString()) })
    }

    private fun await(phase: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (condition()) {
                report(phase, "passed" to true, "elapsed_ms" to (SystemClock.elapsedRealtime() - start))
                return
            }
            SystemClock.sleep(100)
        }
        trackerDiagnostics()
        report(phase, "passed" to false, "elapsed_ms" to (SystemClock.elapsedRealtime() - start))
        throw AssertionError(phase)
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun trackerDiagnostics() {
        if (!::session.isInitialized) return
        runCatching {
            val fields = main {
                val tracker = NavigationSession::class.java.getDeclaredField("tracker").apply { isAccessible = true }
                    .get(session) as LocationTracker
                val state = tracker.state.value
                val listeners = LocationTracker::class.java.getDeclaredField("listeners").apply { isAccessible = true }
                    .get(tracker) as Map<*, *>
                val running = LocationTracker::class.java.getDeclaredField("running").apply { isAccessible = true }
                    .getBoolean(tracker)
                val latest = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
                arrayOf<Pair<String, Any>>(
                    "status" to state.status.name, "precise" to state.precisePermission,
                    "services_enabled" to state.servicesEnabled, "authoritative" to state.authoritativeFix,
                    "reliable" to state.guidanceReliable, "weak" to state.weakAccuracy,
                    "tracker_running" to running, "listener_count" to listeners.size,
                    "injected_samples" to injectedSamples, "has_tracked_fix" to (session.location != null),
                    "provider_has_fix" to (latest != null),
                    "provider_fix_is_mock" to (latest?.isFromMockProvider == true),
                    "provider_fix_matches_injected" to (latest != null && latest.elapsedRealtimeNanos == lastInjectedAt),
                    "tracked_fix_age_ms" to (session.location?.let {
                        (SystemClock.elapsedRealtime() - it.elapsedRealtimeNanos / 1_000_000).coerceIn(0, 10_000_000)
                    } ?: 0L))
            }
            report("tracker-diagnostics", *fields)
        }.onFailure { report("tracker-diagnostics", "diagnostics_available" to false) }
    }

    private fun start() {
        activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        main {
            session = (activity.application as DriveMateApp).navigationSession
            controller = MainActivity::class.java.getDeclaredField("mapController").apply { isAccessible = true }
                .get(activity) as MapController
        }
        await("activity-ready", 45_000) { main { session.settingsReady && nativeMap() != null } }
    }

    private fun nativeMap(): MapLibreMap? = MapController::class.java.getDeclaredField("map")
        .apply { isAccessible = true }.get(controller) as MapLibreMap?

    private fun nativeEnabled(): Boolean = main {
        val component = nativeMap()?.locationComponent
        component != null && component.isLocationComponentActivated && component.isLocationComponentEnabled
    }

    private fun preciseGranted() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun permissionAnswer(allow: Boolean) {
        val ids = listOf("com.android.packageinstaller", "com.android.permissioncontroller")
        val button = if (allow) "permission_allow_button" else "permission_deny_button"
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            for (pkg in ids) {
                device.findObject(By.res(pkg, button))?.let { it.click(); device.waitForIdle(); return }
            }
            SystemClock.sleep(100)
        }
        report("permission-dialog", "passed" to false,
            "allow_buttons" to ids.sumOf { device.findObjects(By.res(it, "permission_allow_button")).size },
            "deny_buttons" to ids.sumOf { device.findObjects(By.res(it, "permission_deny_button")).size },
            "precise" to preciseGranted(),
            "resumed" to main { activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) },
            "window_focused" to main { activity.hasWindowFocus() },
            "rationale" to main { activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) })
        throw AssertionError("permission-dialog")
    }

    private fun requestPermission() {
        await("permission-request-ready") { main {
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus()
        } }
        main {
            @Suppress("UNCHECKED_CAST")
            val launcher = MainActivity::class.java.getDeclaredField("permissionLauncher").apply { isAccessible = true }
                .get(activity) as ActivityResultLauncher<Array<String>>
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun installControlledProvider() {
        // Replacing GPS with Android's test provider removes the emulator's repeating GNSS feed.
        // Android still delivers through the app's normal LocationManager listener.
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false,
            true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        report("controlled-provider", "provider_enabled" to manager.isProviderEnabled(LocationManager.GPS_PROVIDER),
            "provider_available" to (LocationManager.GPS_PROVIDER in manager.allProviders))
    }

    @Suppress("MissingPermission")
    private fun feed(accuracy: Float, index: Int = 0): Long {
        val timestamp = SystemClock.elapsedRealtimeNanos()
        lastInjectedAt = timestamp
        injectedSamples++
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 53.4808 + index * 0.000005
            longitude = -2.2374
            this.accuracy = accuracy
            speed = 2.0f; bearing = 0f
            elapsedRealtimeNanos = timestamp
            time = System.currentTimeMillis()
        })
        return timestamp / 1_000_000
    }

    private fun freshFix(phase: String, accuracy: Float = 5f, index: Int = 0) {
        val old = main { session.location?.elapsedRealtimeNanos ?: 0L }
        // A short measured stream exercises normal Android delivery and permits the app's
        // existing three-sample outlier reacquisition; never bypass that safety filter.
        repeat(3) { sample ->
            feed(accuracy, index)
            if (sample < 2) SystemClock.sleep(250)
        }
        await(phase) { main { (session.location?.elapsedRealtimeNanos ?: 0) > old } }
    }

    private fun isolatedStyle(phase: String) {
        var loaded = false
        main {
            val map = nativeMap() ?: throw AssertionError("native-map-missing")
            val generations = MapController::class.java.getDeclaredField("generations").apply { isAccessible = true }
                .get(controller) as StyleGeneration
            val json = """{"version":8,"sources":{},"layers":[{"id":"test-background","type":"background","paint":{"background-color":"#F5F5F5"}}]}"""
            val method = MapController::class.java.getDeclaredMethod("applyStyle", MapLibreMap::class.java,
                Style.Builder::class.java, Long::class.javaPrimitiveType, String::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType).apply { isAccessible = true }
            method.invoke(controller, map, Style.Builder().fromJson(json), generations.next(), json, false, true)
        }
        await(phase, 30_000) { main {
            val style = MapController::class.java.getDeclaredField("style").apply { isAccessible = true }.get(controller) as Style?
            loaded = style?.getLayer("test-background") != null
            loaded
        } }
        assertTrue(loaded)
    }

    private fun amberPixels(): Int {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        main { nativeMap()!!.snapshot { image -> bitmap = image; latch.countDown() } }
        if (!latch.await(10, TimeUnit.SECONDS)) throw AssertionError("native-snapshot-timeout")
        val image = bitmap ?: throw AssertionError("native-snapshot-missing")
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.count { color ->
            val red = color shr 16 and 255; val green = color shr 8 and 255; val blue = color and 255
            red > 220 && green in 110..210 && blue < 80
        }
    }

    private fun marker(phase: String, weak: Boolean) {
        await(phase) {
            val pixels = amberPixels()
            report("marker-sample", "amber_pixels" to pixels, "expected_weak" to weak)
            (pixels > 0) == weak
        }
    }

    private fun close() {
        if (::activity.isInitialized) main { activity.finish() }
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    @Test fun permissionMarkersWarningAndGapRecovery() {
        try {
            // Replace emulator GNSS before the Activity can register listeners or receive a
            // default emulator position. Replacing an active provider is not a clean fixture.
            installControlledProvider()
            start()
            assertFalse(preciseGranted())
            assertFalse(nativeEnabled())
            val notice = device.wait(Until.findObject(By.text("Continue")), 15_000)
                ?: throw AssertionError("location-notice")
            notice.click()
            permissionAnswer(false)
            // Absence of a grant is true even before the dialog result is delivered. Wait for
            // the Activity's actual denial callback before launching a second permission request.
            await("denial-callback") { main {
                session.message == "DriveMate needs your location to navigate" &&
                    activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            } }
            await("permission-denied") { main { !preciseGranted() && !session.locationAvailability.value.authoritativeFix } }
            assertFalse(nativeEnabled())
            requestPermission()
            permissionAnswer(true)
            await("permission-granted") { preciseGranted() }
            main { session.startLocation() }
            freshFix("grant-first-fix")
            await("grant-native-marker") { nativeEnabled() && main { session.positionReliable } }
            isolatedStyle("native-style-loaded")

            for (icon in CarIcon.entries) {
                main { session.updateSettings(session.settings.copy(carIcon = icon)) }
                freshFix("marker-good-fix")
                await("marker-setting") { main {
                    MapController::class.java.getDeclaredField("carIcon").apply { isAccessible = true }.get(controller) == icon
                } }
                marker("marker-good-${icon.ordinal}", false)
                freshFix("marker-good-fix")
                // A rejected poor fix must change quality indication without advancing position.
                val heldTimestamp = main { session.location!!.elapsedRealtimeNanos }
                feed(80f)
                await("weak-state") { main { session.accuracyWeak && !session.positionReliable } }
                assertEquals("poor-fix-must-not-move-position", heldTimestamp, main { session.location!!.elapsedRealtimeNanos })
                marker("marker-weak-${icon.ordinal}", true)
                isolatedStyle("style-reloaded-${icon.ordinal}")
                marker("marker-reloaded-${icon.ordinal}", true)
                feed(50f)
                await("exact-accuracy-threshold") { main { !session.accuracyWeak } }
                marker("marker-threshold-${icon.ordinal}", false)
                freshFix("marker-recovered-fix")
                await("reliable-recovery") { main { !session.accuracyWeak && session.positionReliable } }
                marker("marker-recovered-${icon.ordinal}", false)
            }

            freshFix("gap-baseline")
            val lastFixMs = main { session.location!!.elapsedRealtimeNanos / 1_000_000 }
            val start = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - lastFixMs < 9_500) {
                assertFalse("warning-before-ten-seconds", main { session.positionUncertain })
                SystemClock.sleep(100)
            }
            await("ten-second-warning", 6_000) { main { session.positionUncertain } }
            val delay = SystemClock.elapsedRealtime() - lastFixMs
            val unchanged = main { session.location!!.elapsedRealtimeNanos / 1_000_000 == lastFixMs }
            report("warning-timing", "delay_ms" to delay, "fix_unchanged" to unchanged)
            assertTrue("controlled-feed-must-stop", unchanged)
            assertTrue("warning-before-ten-seconds", delay >= 9_500)
            assertTrue("warning-too-late", delay <= 15_000)
            assertTrue(device.wait(Until.hasObject(By.text("Position uncertain")), 3_000))
            await("stale-feed") { main { !session.locationAvailability.value.authoritativeFix } }
            while (SystemClock.elapsedRealtime() - start < 20_000) SystemClock.sleep(100)
            freshFix("gap-recovered-fix", index = 1)
            await("gap-recovered-state") { main { session.positionReliable && !session.positionUncertain } }
            for (index in 2..4) {
                SystemClock.sleep(500)
                freshFix("simulated-movement", index = index)
            }
            report("batch-complete", "passed" to true)
        } catch (error: Throwable) {
            trackerDiagnostics()
            report("batch-error", "error_type" to error.javaClass.simpleName)
            throw error
        } finally { close() }
    }

    /** Host revokes the permission between invocations because Android kills the app on revoke. */
    @Test fun revokedPermissionThenRestoredCallback() {
        try {
            installControlledProvider()
            start()
            report("revoked-start", "precise" to preciseGranted(), "native_enabled" to nativeEnabled())
            assertFalse(preciseGranted())
            assertFalse(nativeEnabled())
            val notice = device.wait(Until.findObject(By.text("Continue")), 10_000)
            if (notice != null) notice.click() else requestPermission()
            permissionAnswer(true)
            await("restored-permission") { preciseGranted() }
            main { session.startLocation() }
            freshFix("restored-fix")
            isolatedStyle("restored-native-style")
            await("restored-native-marker") { nativeEnabled() && main { session.positionReliable } }
            report("revocation-complete", "passed" to true)
        } catch (error: Throwable) {
            trackerDiagnostics()
            report("batch-error", "error_type" to error.javaClass.simpleName)
            throw error
        } finally { close() }
    }
}
