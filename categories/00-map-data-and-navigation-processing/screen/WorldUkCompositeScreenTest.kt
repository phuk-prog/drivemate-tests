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
import com.drivemate.app.data.Http
import com.drivemate.app.data.MapLayout
import com.drivemate.app.data.MapPack
import com.drivemate.app.data.MapReleaseSource
import com.drivemate.app.data.OwnMap
import com.drivemate.app.data.UkJoin
import com.drivemate.app.data.WorldOverview
import com.drivemate.app.data.WorldUkPreviewComposer
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
 * C01.5 — One MapLibre preview style must display genuine world low-zoom land and
 * current published UK road geometry, in the SAME map session.
 *
 * Exact Natural Earth candidate PMTiles (SHA verified), plus the real immutable
 * UK source selected from live UK publisher metadata. This is NOT shipped navigation,
 * whole-world high-zoom coverage, offline route legality or physical-device evidence.
 */
@RunWith(AndroidJUnit4::class)
class WorldUkCompositeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val worldSha = "7190d9742617465e441a6bc42ece47579c3eef91a719c260b41e8f7cee211fa7"
    private val worldBytes = 359853L
    private data class Location(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val zoom: Double,
        val layerNames: Array<String>
    )
    private val probes = listOf(
        Location("world-north-america", 39.7392, -104.9903, 3.0, arrayOf("world-land")),
        Location("world-australia", -33.8688, 151.2093, 3.0, arrayOf("world-land")),
        Location("uk-stockport-m60", 53.4120, -2.1690, 14.0,
            arrayOf("road-primary", "road-secondary", "road-tertiary",
                "road-minor", "road-motorway", "road-trunk"))
    )

    private fun candidatePointer(): WorldOverview.PointerData {
        val sources = JSONObject()
        for (name in listOf("world-land.geojson", "world-countries.geojson", "world-places.geojson"))
            sources.put(name, JSONObject().put("tag", "map-data-world-overview-r1-a1")
                .put("sha256", "a".repeat(64)).put("bytes", 3200))
        // Test-only receipt, never a claim that this worldwide release was published.
        return WorldOverview.parse(JSONObject()
            .put("schema", 1).put("kind", "low-zoom-world-overview-geometry-only")
            .put("worldwide_routing", false).put("android_app_map_selected", false)
            .put("attribution", "Natural Earth public domain")
            .put("pipeline_sha256", "b".repeat(64)).put("tested_sample_count", 6)
            .put("source_assets", sources)
            .put("map", JSONObject().put("tag", "map-data-worldtiles-r41138825847-a1")
                .put("name", "world-overview.pmtiles").put("layout", "world-natural-earth-v1")
                .put("sha256", worldSha).put("bytes", worldBytes)).toString())
    }

    @Test fun verifiedWorldLandAndPublishedUkRoadsRenderInOneMapLibreStyle() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val ctx = instrument.targetContext
        val previousMapContext = MapPack.context
        val localWorld = File(ctx.cacheDir, "C01-world-uk-composite.pmtiles")
        val view = AtomicReference<MapView?>()
        val map = AtomicReference<MapLibreMap?>()
        val failure = AtomicReference<String?>()
        val frames = AtomicLong()
        MapPack.context = ctx.applicationContext
        try {
            instrument.context.assets.open("world-overview-candidate.pmtiles").use { input ->
                localWorld.outputStream().use { input.copyTo(it) }
            }
            assertEquals(worldBytes, localWorld.length())
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(localWorld.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("World candidate must match the published producer CI artifact", worldSha, actualHash)
            val base = MapReleaseSource.requireBase()
            val publishedCatalog = Http.getBounded("$base/${UkJoin.MAP_POINTER}", 64 * 1024, 15_000)
            val uk = UkJoin.map(publishedCatalog)
            assertTrue(uk.sha256.matches(Regex("[a-f0-9]{64}")) && uk.bytes > 1_000_000)
            val ukUrl = "pmtiles://$base/${uk.tag}/${MapLayout.OWN.archive}"
            val ukStyle = OwnMap.prepare(OwnMap.appStyle(false, MapLayout.OWN), ukUrl, null)
            val originalStyle = ukStyle.toString()
            val combined = WorldUkPreviewComposer.compose(ukStyle, candidatePointer(), localWorld, false)
            assertEquals("Composing world preview must not mutate UK style", originalStyle, ukStyle.toString())
            assertEquals(ukUrl, combined.getJSONObject("sources")
                .getJSONObject("drivemate").getString("url"))
            assertTrue(combined.getJSONObject("metadata").getBoolean("drivemate:world_preview_only"))
            assertTrue(!combined.getJSONObject("metadata").getBoolean("drivemate:worldwide_routing"))
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    MapLibre.getInstance(context)
                    MapView(context).also { v ->
                        view.set(v)
                        v.addOnDidFinishRenderingFrameListener(
                            MapView.OnDidFinishRenderingFrameWithStatsListener { done, _ ->
                                if (done) frames.incrementAndGet()
                            })
                        v.addOnDidFailLoadingMapListener { failure.set("Combined UK/world MapLibre style failed") }
                        v.onCreate(Bundle()); v.onStart(); v.onResume()
                        v.getMapAsync { m ->
                            m.setStyle(Style.Builder().fromJson(combined.toString())) { map.set(m) }
                        }
                    }
                })
            }
            compose.waitUntil(45_000) { map.get() != null || failure.get() != null }
            assertTrue("World+UK map style did not load: " + failure.get(), map.get() != null)
            val output = File(requireNotNull(ctx.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            var rendered = 0
            for (probe in probes) {
                val before = frames.get()
                compose.runOnIdle {
                    map.get()!!.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(probe.latitude, probe.longitude))
                            .zoom(probe.zoom).tilt(0.0).build()))
                }
                var features = 0
                compose.waitUntil(45_000) {
                    if (failure.get() != null) return@waitUntil true
                    if (frames.get() <= before) return@waitUntil false
                    compose.runOnIdle {
                        val v = view.get()
                        if (v != null && v.width > 0 && v.height > 0) {
                            features = map.get()!!.queryRenderedFeatures(
                                RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()),
                                *probe.layerNames).size
                        }
                    }
                    features > 0
                }
                assertTrue("No rendered world/UK features at " + probe.name + ": " +
                    failure.get(), features > 0)
                rendered += 1
                val screenshot = AtomicReference<Bitmap?>()
                compose.runOnIdle { map.get()!!.snapshot { screenshot.set(it) } }
                compose.waitUntil(15_000) { screenshot.get() != null }
                File(output, "C01-composite-" + probe.name + ".png").outputStream().use {
                    screenshot.get()!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.get()!!.recycle()
            }
            assertEquals(3, rendered)
            File(output, "C01-world-uk-composite-receipt.json").writeText(JSONObject()
                .put("schema", 1)
                .put("scope", "single_map_style_world_lowzoom_and_published_uk_roads")
                .put("world_sha256", worldSha).put("world_bytes", worldBytes)
                .put("uk_map_tag", uk.tag).put("uk_map_sha256", uk.sha256)
                .put("real_maplibre_rendered_viewports", rendered)
                .put("production_app_map_changed", false)
                .put("worldwide_road_navigation_accepted", false)
                .put("physical_phone_accepted", false).toString())
        } finally {
            compose.runOnIdle { view.get()?.let { it.onPause(); it.onStop(); it.onDestroy() } }
            MapPack.context = previousMapContext
            localWorld.delete()
        }
    }
}
