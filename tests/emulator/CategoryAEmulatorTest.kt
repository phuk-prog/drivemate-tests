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
    private var snapshotWidth = 0
    private var snapshotHeight = 0
    private var coloredPixels = 0
    private var markerColoredPixels = 0

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
        calibrateNativeSymbolCapture()
    }

    private fun amberPixels(): Int {
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        main { nativeMap()!!.snapshot { image -> bitmap = image; latch.countDown() } }
        if (!latch.await(10, TimeUnit.SECONDS)) throw AssertionError("native-snapshot-timeout")
        val image = bitmap ?: throw AssertionError("native-snapshot-missing")
        snapshotWidth = image.width
        snapshotHeight = image.height
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        fun distinctive(color: Int): Boolean {
            val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
            return maxOf(r, g, b) - minOf(r, g, b) >= 60 && minOf(r, g, b) <= 170
        }
        coloredPixels = pixels.count(::distinctive)
        val centre = main {
            val map = nativeMap() ?: return@main null
            val last = map.locationComponent.lastKnownLocation ?: return@main null
            map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(last.latitude, last.longitude))
        }
        markerColoredPixels = if (centre == null) 0 else {
            val x = centre.x.toInt(); val y = centre.y.toInt()
            val left = (x - 52).coerceIn(0, image.width)
            val right = (x + 52).coerceIn(0, image.width)
            val top = (y - 52).coerceIn(0, image.height)
            val bottom = (y + 52).coerceIn(0, image.height)
            var n = 0
            for (py in top until bottom) for (px in left until right)
                if (distinctive(pixels[py * image.width + px])) n++
            n
        }
        return pixels.count { color ->
            val red = color shr 16 and 255; val green = color shr 8 and 255; val blue = color and 255
            red > 220 && green in 110..210 && blue < 80
        }
    }

    /** Android's actual compositor, independent of MapLibre's native map snapshot. */
    private fun displayedAmber(x: Int, y: Int, radius: Int): Pair<Int, Boolean> {
        // UiDevice.takeScreenshot(): Bitmap requires UI Automator 2.4; this test uses 2.3.
        val shot = instrumentation.uiAutomation.takeScreenshot() ?: return 0 to false
        return try {
            val left = (x - radius).coerceIn(0, shot.width)
            val right = (x + radius).coerceIn(0, shot.width)
            val top = (y - radius).coerceIn(0, shot.height)
            val bottom = (y + radius).coerceIn(0, shot.height)
            if (left >= right || top >= bottom) return 0 to false
            var amber = 0
            for (py in top until bottom) for (px in left until right) {
                val c = shot.getPixel(px, py)
                val red = c shr 16 and 255
                val green = c shr 8 and 255
                val blue = c and 255
                if (red > 220 && green in 110..210 && blue < 80) amber++
            }
            amber to true
        } finally { shot.recycle() }
    }

    /** Prove the display observer sees a known amber patch and rejects a dark patch. */
    private fun calibrateDisplayCapture() {
        val patch = android.view.View(activity).apply {
            setBackgroundColor(android.graphics.Color.rgb(255, 160, 0))
        }
        val rect = main {
            val view = controller.mapView
            val x = (view.width / 2 - 24).coerceAtLeast(0)
            val y = (view.height / 2 - 24).coerceAtLeast(0)
            view.addView(patch, android.widget.FrameLayout.LayoutParams(48, 48).apply {
                leftMargin = x; topMargin = y
            })
            val origin = IntArray(2)
            view.getLocationOnScreen(origin)
            intArrayOf(origin[0] + x + 24, origin[1] + y + 24)
        }
        try {
            SystemClock.sleep(300)
            val positive = displayedAmber(rect[0], rect[1], 12)
            main { patch.setBackgroundColor(android.graphics.Color.BLACK) }
            SystemClock.sleep(300)
            val negative = displayedAmber(rect[0], rect[1], 12)
            report("capture-control",
                "positive_amber_pixels" to positive.first,
                "negative_amber_pixels" to negative.first,
                "display_capture_available" to (positive.second && negative.second))
            assertTrue("screen-capture-unavailable", positive.second && negative.second)
            assertTrue("positive-control-not-visible", positive.first > 100)
            assertEquals("negative-control-false-amber", 0, negative.first)
        } finally {
            main { controller.mapView.removeView(patch) }
        }
    }


    /** Validate the real native symbol renderer and compositor after every style load. */
    private fun calibrateNativeSymbolCapture() {
        val imageId = "category-a-render-control-image"
        val sourceId = "category-a-render-control-source"
        val layerId = "category-a-render-control-layer"
        var nativePositive = 0
        var displayPositive = 0
        main {
            val map = nativeMap() ?: throw AssertionError("native-control-map")
            val style = map.style ?: throw AssertionError("native-control-style")
            val fix = map.locationComponent.lastKnownLocation ?: throw AssertionError("native-control-fix")
            val image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            image.eraseColor(android.graphics.Color.rgb(255, 160, 0))
            style.addImage(imageId, image)
            style.addSource(org.maplibre.android.style.sources.GeoJsonSource(sourceId,
                org.maplibre.geojson.Feature.fromGeometry(
                    org.maplibre.geojson.Point.fromLngLat(fix.longitude, fix.latitude))))
            style.addLayer(org.maplibre.android.style.layers.SymbolLayer(layerId, sourceId).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(imageId),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)))
        }
        try {
            await("capture-control", 10_000) {
                nativePositive = amberPixels()
                val display = markerOnDisplay()
                displayPositive = display.first
                report("capture-control", "native_symbol_amber_pixels" to nativePositive,
                    "positive_amber_pixels" to displayPositive,
                    "display_capture_available" to display.second)
                nativePositive > 100 && display.second && displayPositive > 100
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
        await("capture-control", 10_000) {
            val nativeNegative = amberPixels()
            val display = markerOnDisplay()
            report("capture-control", "native_symbol_amber_pixels" to nativeNegative,
                "negative_amber_pixels" to display.first,
                "display_capture_available" to display.second)
            nativeNegative == 0 && display.second && display.first == 0
        }
    }

    private fun markerOnDisplay(): Pair<Int, Boolean> {
        val centre = main {
            val map = nativeMap() ?: return@main null
            val lc = map.locationComponent
            val last = if (lc.isLocationComponentActivated) lc.lastKnownLocation else null
            last?.let {
                val pixel = map.projection.toScreenLocation(
                    org.maplibre.android.geometry.LatLng(it.latitude, it.longitude))
                val origin = IntArray(2)
                controller.mapView.getLocationOnScreen(origin)
                intArrayOf(origin[0] + pixel.x.toInt(), origin[1] + pixel.y.toInt())
            }
        } ?: return 0 to false
        return displayedAmber(centre[0], centre[1], 72)
    }

    /** No quality-coloured marker is drawn. When position is reliable, the standard icon
     * must be visibly painted in both the native snapshot and independent screen capture.
     */
    private fun marker(phase: String, weak: Boolean) {
        var lastEvidence = ""
        var nextFreshFixAt = 0L
        await(phase) {
            // Reliable-marker observation needs an ongoing live feed. A single fix
            // expires while native rendering/capture waits; gap tests stop feeding separately.
            if (!weak && SystemClock.elapsedRealtime() >= nextFreshFixAt) {
                feed(5f)
                nextFreshFixAt = SystemClock.elapsedRealtime() + 500L
            }
            val amber = amberPixels()
            val nativeMarker = markerColoredPixels
            val display = markerOnDisplay()
            val actualScreenMarker = normalMarkerOnDisplay()
            val noHeldLayer = main { nativeMap()?.style?.getLayer("dm-held-position-marker") == null }
            val optionsMatch = main {
                val map = nativeMap() ?: return@main false
                val lc = map.locationComponent
                val expected = MapController::class.java.getDeclaredMethod("carDrawable")
                    .apply { isAccessible = true }.invoke(controller) as Int?
                val icon = MapController::class.java.getDeclaredField("carIcon")
                    .apply { isAccessible = true }.get(controller) as CarIcon
                val actual = if (lc.renderMode == org.maplibre.android.location.modes.RenderMode.GPS)
                    lc.locationComponentOptions.gpsDrawable() else lc.locationComponentOptions.foregroundDrawable()
                icon == CarIcon.DOT || expected == actual
            }
            val evidence = "$amber-$nativeMarker-${actualScreenMarker.first}-$noHeldLayer-$optionsMatch"
            if (evidence != lastEvidence) {
                report("marker-sample", "amber_pixels" to amber, "expected_weak" to weak,
                    "snapshot_width" to snapshotWidth, "snapshot_height" to snapshotHeight,
                    "marker_colored_pixels" to coloredPixels,
                    "marker_colored_nearby_pixels" to nativeMarker)
                report("marker-display", "screen_amber_pixels" to display.first,
                    "screen_marker_colored_pixels" to actualScreenMarker.first,
                    "display_capture_available" to (display.second && actualScreenMarker.second),
                    "expected_weak" to weak)
                markerDiagnostics()
                lastEvidence = evidence
            }
            val noAmber = amber == 0 && noHeldLayer && optionsMatch
            if (weak) noAmber
            else noAmber && nativeEnabled() && nativeMarker > 20 &&
                actualScreenMarker.second && actualScreenMarker.first > 20
        }
    }

    /** Colour contrast confined to the projected ordinary marker, independent of amber. */
    private fun normalMarkerOnDisplay(): Pair<Int, Boolean> {
        val centre = main {
            val map = nativeMap() ?: return@main null
            val last = map.locationComponent.lastKnownLocation ?: return@main null
            val pixel = map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(last.latitude, last.longitude))
            val origin = IntArray(2)
            controller.mapView.getLocationOnScreen(origin)
            intArrayOf(origin[0] + pixel.x.toInt(), origin[1] + pixel.y.toInt())
        } ?: return 0 to false
        val shot = instrumentation.uiAutomation.takeScreenshot() ?: return 0 to false
        return try {
            val left = (centre[0] - 52).coerceIn(0, shot.width)
            val right = (centre[0] + 52).coerceIn(0, shot.width)
            val top = (centre[1] - 52).coerceIn(0, shot.height)
            val bottom = (centre[1] + 52).coerceIn(0, shot.height)
            var pixels = 0
            for (y in top until bottom) for (x in left until right) {
                val c = shot.getPixel(x, y)
                val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
                if (maxOf(r, g, b) - minOf(r, g, b) >= 60 && minOf(r, g, b) <= 170) pixels++
            }
            pixels to true
        } finally { shot.recycle() }
    }

    private fun markerDiagnostics() {
        if (!::controller.isInitialized) return
        runCatching {
            val fields = main {
                val map = nativeMap() ?: return@main emptyArray<Pair<String, Any>>()
                val lc = map.locationComponent
                val style = map.style
                val last = if (lc.isLocationComponentActivated) lc.lastKnownLocation else null
                val point = last?.let { map.projection.toScreenLocation(org.maplibre.android.geometry.LatLng(it.latitude, it.longitude)) }
                val expectedDrawable = MapController::class.java.getDeclaredMethod("carDrawable")
                    .apply { isAccessible = true }.invoke(controller) as Int?
                val options = if (lc.isLocationComponentActivated) lc.locationComponentOptions else null
                val actualDrawable = if (lc.isLocationComponentActivated && lc.renderMode == org.maplibre.android.location.modes.RenderMode.GPS)
                    options?.gpsDrawable() else options?.foregroundDrawable()
                val layers = style?.layers?.filter { it.id.startsWith("mapbox-location-") }.orEmpty()
                // Only counts and booleans leave the emulator; no coordinates or screenshots.
                val locationSource = style?.getSource("mapbox-location-source") as?
                    org.maplibre.android.style.sources.GeoJsonSource
                val sourceFeatureList = runCatching { locationSource?.querySourceFeatures(null).orEmpty() }
                    .getOrDefault(emptyList())
                val sourceFeatures = sourceFeatureList.size
                val locationFeature = sourceFeatureList.firstOrNull()
                val staleKey = "mapbox-property-location-stale"
                val fgKey = "mapbox-property-foreground-icon"
                val fgStaleKey = "mapbox-property-foreground-stale-icon"
                val sourceIsStale = locationFeature?.takeIf { it.hasProperty(staleKey) }
                    ?.getBooleanProperty(staleKey) == true
                val foregroundImage = locationFeature?.takeIf { it.hasProperty(fgKey) }
                    ?.getStringProperty(fgKey)
                val foregroundStaleImage = locationFeature?.takeIf { it.hasProperty(fgStaleKey) }
                    ?.getStringProperty(fgStaleKey)
                val foregroundLayer = style?.getLayer("mapbox-location-foreground-layer")
                val backgroundLayer = style?.getLayer("mapbox-location-background-layer")
                val foregroundRendered = runCatching {
                    if (point == null) 0 else map.queryRenderedFeatures(
                        point, "mapbox-location-foreground-layer").size
                }.getOrDefault(-1)
                val backgroundRendered = runCatching {
                    if (point == null) 0 else map.queryRenderedFeatures(
                        point, "mapbox-location-background-layer").size
                }.getOrDefault(-1)
                val renderedFeatures = runCatching {
                    if (point == null || layers.isEmpty()) 0
                    else map.queryRenderedFeatures(point, *layers.map { it.id }.toTypedArray()).size
                }.getOrDefault(-1)
                val nearbyFeatures = runCatching {
                    if (point == null || layers.isEmpty()) 0
                    else map.queryRenderedFeatures(android.graphics.RectF(
                        point.x - 40f, point.y - 40f, point.x + 40f, point.y + 40f
                    ), *layers.map { it.id }.toTypedArray()).size
                }.getOrDefault(-1)
                val sprite = style?.getImage("mapbox-location-icon")
                var spriteAmber = 0
                if (sprite != null) {
                    val colors = IntArray(sprite.width * sprite.height)
                    sprite.getPixels(colors, 0, sprite.width, 0, 0, sprite.width, sprite.height)
                    spriteAmber = colors.count { color ->
                        val r = color shr 16 and 255; val g = color shr 8 and 255; val b = color and 255
                        r > 220 && g in 110..210 && b < 80
                    }
                }
                arrayOf<Pair<String, Any>>(
                    "native_activated" to lc.isLocationComponentActivated,
                    "native_enabled" to (lc.isLocationComponentActivated && lc.isLocationComponentEnabled),
                    "native_has_fix" to (last != null),
                    "native_on_screen" to (point != null && point.x >= 0 && point.x < controller.mapView.width &&
                        point.y >= 0 && point.y < controller.mapView.height),
                    "style_loaded" to (style?.isFullyLoaded == true),
                    "native_asset_matches_controller" to (expectedDrawable != null && expectedDrawable == actualDrawable),
                    "location_layer_count" to layers.size,
                    "location_source_present" to (locationSource != null),
                    "location_source_features" to sourceFeatures,
                    "source_location_stale" to sourceIsStale,
                    "source_foreground_icon_registered" to (foregroundImage == "mapbox-location-icon"),
                    "source_foreground_stale_icon_registered" to (foregroundStaleImage == "mapbox-location-stale-icon"),
                    "foreground_layer_visible" to (foregroundLayer?.visibility?.value == "visible"),
                    "background_layer_visible" to (backgroundLayer?.visibility?.value == "visible"),
                    "foreground_rendered_features" to foregroundRendered,
                    "background_rendered_features" to backgroundRendered,
                    "rendered_location_features" to renderedFeatures,
                    "rendered_nearby_location_features" to nearbyFeatures,
                    "visible_location_layers" to layers.count { it.visibility.value == "visible" },
                    "sprite_amber_pixels" to spriteAmber,
                    "render_mode" to if (lc.isLocationComponentActivated) lc.renderMode else 0,
                    "snapshot_width" to snapshotWidth, "snapshot_height" to snapshotHeight,
                    "marker_colored_pixels" to coloredPixels)
            }
            report("marker-native-diagnostics", *fields)
        }.onFailure { report("marker-native-diagnostics", "diagnostics_available" to false) }
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
            calibrateDisplayCapture()

            for (icon in CarIcon.entries) {
                main { session.updateSettings(session.settings.copy(carIcon = icon)) }
                freshFix("marker-good-fix")
                await("marker-setting") { main {
                    MapController::class.java.getDeclaredField("carIcon").apply { isAccessible = true }.get(controller) == icon
                } }
                marker("marker-good-${icon.ordinal}", false)
                freshFix("marker-good-fix")
                // A rejected poor fix must not advance navigation or create an alternate marker.
                val heldTimestamp = main { session.location!!.elapsedRealtimeNanos }
                feed(80f)
                await("weak-state") { main { session.locationAvailability.value.weakAccuracy && !session.positionReliable } }
                assertEquals("poor-fix-must-not-move-position", heldTimestamp, main { session.location!!.elapsedRealtimeNanos })
                marker("marker-weak-${icon.ordinal}", true)
                isolatedStyle("style-reloaded-${icon.ordinal}")
                marker("marker-reloaded-${icon.ordinal}", true)
                feed(50f)
                await("exact-accuracy-threshold") { main { !session.locationAvailability.value.weakAccuracy } }
                // 50 m clears weak accuracy, but guidance requires <=25 m. Restore a
                // guidance-quality fix before requiring a reliably visible marker.
                freshFix("marker-recovered-fix")
                await("reliable-recovery") { main { !session.locationAvailability.value.weakAccuracy && session.positionReliable } }
                marker("marker-threshold-${icon.ordinal}", false)
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
            markerDiagnostics()
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
            markerDiagnostics()
            trackerDiagnostics()
            report("batch-error", "error_type" to error.javaClass.simpleName)
            throw error
        } finally { close() }
    }
}
