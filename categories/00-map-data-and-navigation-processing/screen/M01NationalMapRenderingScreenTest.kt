package com.drivemate.tests.cat00

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.Http
import com.drivemate.app.data.MapLayout
import com.drivemate.app.data.MapPack
import com.drivemate.app.data.OwnMap
import com.drivemate.app.data.MapReleaseSource
import com.drivemate.app.data.UkJoin
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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong

/**
 * M01: actual Android/MapLibre road-layer rendering against the immutable UK
 * display map selected from the current publisher catalog, across four nations.
 * This is a live network screen test, not a substitute for a signed APK on a phone
 * or proof of every road/zoom/offline tile in the UK.
 */
@RunWith(AndroidJUnit4::class)
class M01NationalMapRenderingScreenTest {
    @get:Rule val compose = createComposeRule()
    private data class Sample(val name: String, val latitude: Double, val longitude: Double, val zoom: Double = 14.0,
                              val everyQuadrant: Boolean = false)
    private val cities = listOf(
        Sample("manchester", 53.4808, -2.2426),
        Sample("edinburgh", 55.9533, -3.1883),
        Sample("cardiff", 51.4816, -3.1791),
        Sample("belfast", 54.5973, -5.9301),
        // More real UK viewports relevant to the reported M60/Scotland map gaps.
        // These are renderer probes, NOT road-law, physical GPS or every-tile proof.
        Sample("stockport-m60", 53.4120, -2.1690),
        Sample("glasgow", 55.8610, -4.2510),
        Sample("inverness", 57.4778, -4.2247),
        Sample("glasgow-midzoom", 55.8610, -4.2510, 12.0),
        // C01.3: the 27 Oct report showed a zoomed-out Scotland view with blank rectangles while only
        // regional archives were published. With the national map, zoomed-out Scotland must draw
        // roads, and at zoom 8 every quarter of the screen must have roads (no missing rectangle).
        Sample("scotland-zoomed-out", 57.0, -4.0, 6.5),
        Sample("scotland-quadrants", 56.9, -4.2, 8.0, everyQuadrant = true),
    )
    private val roads = arrayOf("road-primary", "road-secondary", "road-tertiary",
        "road-minor", "road-service", "road-trunk", "road-motorway")
    private data class PublishedMap(val tiles: String, val tag: String, val sha256: String, val bytes: Long)

    private fun publishedMap(): PublishedMap {
        val base = MapReleaseSource.requireBase()
        val catalog = Http.getBounded("$base/${UkJoin.MAP_POINTER}", 64 * 1024, 15_000)
        val asset = UkJoin.map(catalog)
        require(asset.sha256.matches(Regex("[0-9a-f]{64}")) && asset.bytes > 0) {
            "Published UK map catalog resolved invalid asset receipt: " +
                "tag=${asset.tag}, sha256=${asset.sha256}, bytes=${asset.bytes}"
        }
        val url = "$base/${asset.tag}/${MapLayout.OWN.archive}"
        return PublishedMap("pmtiles://$url", asset.tag, asset.sha256, asset.bytes)
    }

    @Test fun publishedOnePieceUkMapRendersRoadsInAllFourNations() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val original = MapPack.context
        MapPack.context = ctx.applicationContext
        val map = AtomicReference<MapLibreMap?>()
        val view = AtomicReference<MapView?>()
        val failure = AtomicReference<String?>()
        val renderedFrames = AtomicLong()
        val rendererState = AtomicReference("not-created")
        try {
            val published = publishedMap()
            val style = OwnMap.prepare(OwnMap.appStyle(false, MapLayout.OWN),
                published.tiles, null).toString()
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize().testTag("m01-uk-map"),
                    factory = { appContext ->
                        MapLibre.getInstance(appContext)
                        MapView(appContext).also { v ->
                            view.set(v)
                            rendererState.set("view-created:${v.width}x${v.height}")
                            v.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameWithStatsListener { done, _ ->
                                if (done) renderedFrames.incrementAndGet()
                            })
                            v.addOnDidFailLoadingMapListener { failure.set("MapLibre style load failed") }
                            v.onCreate(Bundle()); v.onStart(); v.onResume()
                            v.getMapAsync { m ->
                                rendererState.set("map-async-ready:${v.width}x${v.height}")
                                m.setStyle(Style.Builder().fromJson(style)) {
                                    map.set(m)
                                    rendererState.set("style-ready:${v.width}x${v.height}")
                                }
                            }
                        }
                    })
            }
            try {
                compose.waitUntil(45_000) { map.get() != null || failure.get() != null }
            } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                throw AssertionError("Timed out loading published UK map style; state=${rendererState.get()}, " +
                    "failure=${failure.get()}, frames=${renderedFrames.get()}", e)
            }
            assertTrue("Published UK map style failed: ${failure.get()}; state=${rendererState.get()}", map.get() != null)
            val dir = File(requireNotNull(ctx.externalMediaDirs.firstOrNull()), "additional_test_output")
                .apply { mkdirs() }
            File(dir, "M01-published-map-source.json").writeText(org.json.JSONObject()
                .put("release", published.tag).put("published_sha256", published.sha256)
                .put("published_bytes", published.bytes)
                .put("scope", "catalog-selected immutable source and representative rendering")
                .put("entire_archive_independently_hashed", false).toString())
            for (city in cities) {
                val beforeMove = renderedFrames.get()
                rendererState.set("moving:${city.name};frames=$beforeMove")
                compose.runOnIdle {
                    map.get()!!.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(city.latitude, city.longitude))
                            .zoom(city.zoom).tilt(0.0).build()))
                }
                var foundRoads = false
                try {
                    compose.waitUntil(45_000) {
                        // MapLibre's native renderer has no actor before the first completed
                        // post-camera frame. Querying it early crashes the whole Android
                        // instrumentation process instead of reporting a missing map.
                        if (failure.get() != null) return@waitUntil true
                        if (renderedFrames.get() <= beforeMove) return@waitUntil false
                        compose.runOnIdle {
                            val v = view.get()
                            if (v != null && v.width > 0 && v.height > 0 &&
                                renderedFrames.get() > beforeMove) {
                                val seen = map.get()!!.queryRenderedFeatures(
                                    RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()), *roads)
                                val w = v.width / 2f; val h = v.height / 2f
                                val emptyQuadrants = if (!city.everyQuadrant) emptyList<String>() else
                                    listOf("top-left" to RectF(0f, 0f, w, h), "top-right" to RectF(w, 0f, 2 * w, h),
                                        "bottom-left" to RectF(0f, h, w, 2 * h), "bottom-right" to RectF(w, h, 2 * w, 2 * h))
                                        .filter { (_, r) -> map.get()!!.queryRenderedFeatures(r, *roads).isEmpty() }
                                        .map { it.first }
                                foundRoads = seen.isNotEmpty() && emptyQuadrants.isEmpty()
                                rendererState.set("queried:${city.name};viewport=${v.width}x${v.height};" +
                                    "features=${seen.size};emptyQuadrants=$emptyQuadrants;frames=${renderedFrames.get()}")
                            }
                        }
                        foundRoads || failure.get() != null
                    }
                } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
                    throw AssertionError("Timed out rendering ${city.name}; state=${rendererState.get()}, " +
                        "failure=${failure.get()}, frames=${renderedFrames.get()}, " +
                        "framesBeforeMove=$beforeMove, viewport=${view.get()?.width}x${view.get()?.height}", e)
                }
                assertTrue("No published UK roads rendered at ${city.name}: ${failure.get()}; " +
                    "state=${rendererState.get()}, frames=${renderedFrames.get()}", foundRoads)
                val shot = AtomicReference<Bitmap?>()
                compose.runOnIdle { map.get()!!.snapshot { shot.set(it) } }
                compose.waitUntil(15_000) { shot.get() != null }
                File(dir, "M01-${city.name}-real-uk-map.png").outputStream().use {
                    shot.get()!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                shot.get()!!.recycle()
            }
        } finally {
            compose.runOnIdle { view.get()?.let { it.onPause(); it.onStop(); it.onDestroy() } }
            MapPack.context = original
        }
    }
}
