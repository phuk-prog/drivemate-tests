package com.drivemate.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.graphics.Bitmap
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.graphics.PointF
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.drivemate.app.controller.NavigationSession
import com.drivemate.app.map.MapController
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Category A basic checks on the real Activity, Android location callbacks and MapLibre renderer.
 * Each step proves one basic behaviour; anything beyond these belongs in unit tests.
 */
class CategoryAEmulatorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private lateinit var activity: MainActivity
    private lateinit var session: NavigationSession
    private lateinit var controller: MapController

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
                report(phase, "passed" to true, "elapsed_ms" to (SystemClock.elapsedRealtime() - start).toInt())
                return
            }
            SystemClock.sleep(100)
        }
        report("failure-detail", "check_id" to phase, "failure_kind" to "timeout", "timeout_ms" to timeoutMs.toInt())
        throw AssertionError(phase)
    }

    private fun nativeMap(): MapLibreMap? = MapController::class.java.getDeclaredField("map")
        .apply { isAccessible = true }.get(controller) as MapLibreMap?

    private fun nativeEnabled(): Boolean = main {
        val component = nativeMap()?.locationComponent
        component != null && component.isLocationComponentActivated && component.isLocationComponentEnabled
    }

    private fun preciseGranted() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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

    @Suppress("MissingPermission", "DEPRECATION")
    private fun installControlledProvider() {
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
        manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false,
            true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    @Suppress("MissingPermission")
    private fun feed(accuracy: Float) {
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 53.4808
            longitude = -2.2374
            this.accuracy = accuracy
            speed = 2.0f
            bearing = 0f
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            time = System.currentTimeMillis()
        })
    }

    private fun freshFix(phase: String) {
        val old = main { session.location?.elapsedRealtimeNanos ?: 0L }
        repeat(3) { sample -> feed(5f); if (sample < 2) SystemClock.sleep(250) }
        await(phase) { main { (session.location?.elapsedRealtimeNanos ?: 0) > old } }
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
        throw AssertionError("permission-dialog")
    }

    /** Places a known amber patch at a fixed screen offset from the fix, then checks the native snapshot shows it. */
    private fun nativeControlPaints(): Boolean {
        val imageId = "category-a-render-control-image"
        val sourceId = "category-a-render-control-source"
        val layerId = "category-a-render-control-layer"
        main {
            val map = nativeMap() ?: throw AssertionError("native-control-map")
            val style = map.style ?: throw AssertionError("native-control-style")
            val fix = map.locationComponent.lastKnownLocation ?: throw AssertionError("native-control-fix")
            val fixPoint = map.projection.toScreenLocation(LatLng(fix.latitude, fix.longitude))
            val dx = if (fixPoint.x + 160f < activity.window.decorView.width) 160f else -160f
            val placed = map.projection.fromScreenLocation(PointF(fixPoint.x + dx, fixPoint.y))
            val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.rgb(255, 160, 0))
            style.addImage(imageId, image)
            style.addSource(GeoJsonSource(sourceId, Feature.fromGeometry(Point.fromLngLat(placed.longitude, placed.latitude))))
            style.addLayer(SymbolLayer(layerId, sourceId).withProperties(
                PropertyFactory.iconImage(imageId),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)))
        }
        var painted = false
        try {
            await("native-control-positive", 15_000) {
                painted = nativeAmberPixels() > 100
                painted
            }
        } finally {
            main {
                nativeMap()?.style?.let { style ->
                    style.removeLayer(layerId)
                    style.removeSource(sourceId)
                    style.removeImage(imageId)
                }
            }
        }
        return painted
    }

    private fun nativeAmberPixels(): Int {
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

    private fun close() {
        if (::activity.isInitialized) main { activity.finish() }
        runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    @Test fun categoryABasics() {
        try {
            installControlledProvider()
            start()
            report("controlled-provider", "passed" to true)

            // A.2 basic: a denied permission never enables location.
            val notice = device.wait(Until.findObject(By.text("Continue")), 15_000)
                ?: throw AssertionError("location-notice")
            notice.click()
            permissionAnswer(false)
            await("permission-denied") { main { !preciseGranted() && !nativeEnabled() } }

            // A.1 basic: a granted permission and a fix enable a reliable position.
            requestPermission()
            permissionAnswer(true)
            await("permission-granted") { preciseGranted() }
            main { session.startLocation() }
            freshFix("grant-first-fix")
            await("grant-native-marker") { nativeEnabled() && main { session.positionReliable } }

            // Basic rendering proof: the native renderer paints a known patch.
            assertTrue("native-control-positive", nativeControlPaints())
        } finally {
            close()
        }
    }
}
