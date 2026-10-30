package com.drivemate.tests.cat00

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.WorldOverview
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * C01.5: REAL global low-zoom PMTiles on an Android MapLibre renderer.
 * The fixture is the SHA-verified public CI output from the map producer:
 * https://github.com/phuk-prog/drivemate-map/actions/runs/41138825847
 * There is no global app map selection, no published worldwide release claim,
 * no road/lane legality acceptance and no physical-phone demonstration.
 */
@RunWith(AndroidJUnit4::class)
class WorldOverviewCandidateScreenTest {
    @get:Rule val compose = createComposeRule()

    private data class City(val label: String, val lat: Double, val lon: Double)
    private val continents = listOf(
        City("north-america", 39.7392, -104.9903),
        City("south-america", -15.7939, -47.8828),
        City("europe", 48.8566, 2.3522),
        City("africa", -1.2921, 36.8219),
        City("asia", 28.6139, 77.2090),
        City("australia", -33.8688, 151.2093),
    )
    private val expectedSha = "7190d9742617465e441a6bc42ece47579c3eef91a719c260b41e8f7cee211fa7"
    private val expectedBytes = 359_853L

    private fun pointerFixture(): WorldOverview.PointerData {
        val sourceAssets = JSONObject()
        for (name in listOf("world-land.geojson", "world-countries.geojson", "world-places.geojson"))
            sourceAssets.put(name, JSONObject()
                .put("tag", "map-data-world-overview-r1-a1")
                .put("sha256", "a".repeat(64)).put("bytes", 3200))
        // Test-only pointer identity; no such immutable world release is claimed to be live.
        return WorldOverview.parse(JSONObject()
            .put("schema", 1)
            .put("kind", "low-zoom-world-overview-geometry-only")
            .put("worldwide_routing", false)
            .put("android_app_map_selected", false)
            .put("attribution", "Natural Earth public domain")
            .put("pipeline_sha256", "b".repeat(64))
            .put("tested_sample_count", 6)
            .put("source_assets", sourceAssets)
            .put("map", JSONObject()
                .put("tag", "map-data-worldtiles-r41138825847-a1")
                .put("name", "world-overview.pmtiles")
                .put("layout", "world-natural-earth-v1")
                .put("sha256", expectedSha)
                .put("bytes", expectedBytes)).toString())
    }

    @Test fun sixContinentLowZoomWorldMapRendersRealLandFeatures() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val app = instrument.targetContext
        val fixture = instrument.context.assets.open("world-overview-candidate.pmtiles")
        val local = File(app.cacheDir, "C01-world-render-candidate.pmtiles")
        val view = AtomicReference<MapView?>()
        val map = AtomicReference<MapLibreMap?>()
        val failure = AtomicReference<String?>()
        val frames = AtomicLong()
        try {
            fixture.use { source -> local.outputStream().use { source.copyTo(it) } }
            assertEquals("Real output must have the expected length", expectedBytes, local.length())
            val actual = MessageDigest.getInstance("SHA-256").digest(local.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals("Real published CI candidate bytes changed", expectedSha, actual)
            val style = WorldOverview.previewStyle(pointerFixture(), local, night = false).toString()
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    MapLibre.getInstance(context)
                    MapView(context).also { v ->
                        view.set(v)
                        v.addOnDidFinishRenderingFrameListener(
                            MapView.OnDidFinishRenderingFrameWithStatsListener { done, _ ->
                                if (done) frames.incrementAndGet()
                            })
                        v.addOnDidFailLoadingMapListener { failure.set("World MapLibre style failed") }
                        v.onCreate(Bundle()); v.onStart(); v.onResume()
                        v.getMapAsync { m ->
                            m.setStyle(Style.Builder().fromJson(style)) { map.set(m) }
                        }
                    }
                })
            }
            compose.waitUntil(45_000) { map.get() != null || failure.get() != null }
            assertTrue("World MapLibre style failed: " + failure.get(), map.get() != null)
            val shots = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            var successfullyQueried = 0
            for (place in continents) {
                val prior = frames.get()
                compose.runOnIdle {
                    map.get()!!.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(place.lat, place.lon))
                            .zoom(3.0).tilt(0.0).build()))
                }
                var land = 0
                compose.waitUntil(45_000) {
                    if (failure.get() != null) return@waitUntil true
                    if (frames.get() <= prior) return@waitUntil false
                    compose.runOnIdle {
                        val v = view.get()
                        if (v != null && v.width > 0 && v.height > 0) {
                            land = map.get()!!.queryRenderedFeatures(RectF(0f, 0f,
                                v.width.toFloat(), v.height.toFloat()), "world-land").size
                        }
                    }
                    land > 0
                }
                assertTrue("Real world land layer missing at " + place.label +
                    "; frames=" + frames.get() + ", failure=" + failure.get(), land > 0)
                successfullyQueried++
                val screenshot = AtomicReference<Bitmap?>()
                compose.runOnIdle { map.get()!!.snapshot { screenshot.set(it) } }
                compose.waitUntil(15_000) { screenshot.get() != null }
                File(shots, "C01-world-" + place.label + "-real-map.png").outputStream().use {
                    screenshot.get()!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.get()!!.recycle()
            }
            assertEquals("Six independent continents must render", 6, successfullyQueried)
            File(shots, "C01-world-source-receipt.json").writeText(JSONObject()
                .put("schema", 1).put("candidate_sha256", expectedSha)
                .put("candidate_bytes", expectedBytes)
                .put("source_ci_run", 41138825847L)
                .put("real_maplibre_rendering", true)
                .put("land_continents_rendered", successfullyQueried)
                .put("worldwide_navigation_verified", false)
                .put("published_production_world_map", false)
                .put("real_phone_accepted", false).toString())
        } finally {
            compose.runOnIdle { view.get()?.let { it.onPause(); it.onStop(); it.onDestroy() } }
            local.delete()
        }
    }
}
