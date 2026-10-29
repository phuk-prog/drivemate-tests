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
import com.drivemate.app.data.MapLayout
import com.drivemate.app.data.MapPack
import com.drivemate.app.data.OwnMap
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

/**
 * M01: actual Android/MapLibre road-layer rendering against the immutable UK
 * display map already independently verified as published, across four nations.
 * This is a live network screen test, not a substitute for a signed APK on a phone
 * or proof of every road/zoom/offline tile in the UK.
 */
@RunWith(AndroidJUnit4::class)
class M01NationalMapRenderingScreenTest {
    @get:Rule val compose = createComposeRule()
    private data class Sample(val name: String, val latitude: Double, val longitude: Double)
    private val cities = listOf(
        Sample("manchester", 53.4808, -2.2426),
        Sample("edinburgh", 55.9533, -3.1883),
        Sample("cardiff", 51.4816, -3.1791),
        Sample("belfast", 54.5973, -5.9301),
    )
    private val roads = arrayOf("road-primary", "road-secondary", "road-tertiary",
        "road-minor", "road-service", "road-trunk", "road-motorway")
    private val release = "map-data-ukmap-r40883954274-a1"
    private val pmtiles = "pmtiles://https://github.com/phuk-prog/drivemate-map/releases/download/" +
        "$release/drivemate-v1.pmtiles"

    @Test fun publishedOnePieceUkMapRendersRoadsInAllFourNations() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val original = MapPack.context
        MapPack.context = ctx.applicationContext
        val map = AtomicReference<MapLibreMap?>()
        val view = AtomicReference<MapView?>()
        val failure = AtomicReference<String?>()
        try {
            val style = OwnMap.prepare(OwnMap.appStyle(false, MapLayout.OWN),
                pmtiles, null).toString()
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize().testTag("m01-uk-map"),
                    factory = { appContext ->
                        MapLibre.getInstance(appContext)
                        MapView(appContext).also { v ->
                            view.set(v)
                            v.addOnDidFailLoadingMapListener { failure.set("MapLibre style load failed") }
                            v.onCreate(Bundle()); v.onStart(); v.onResume()
                            v.getMapAsync { m ->
                                m.setStyle(Style.Builder().fromJson(style)) { map.set(m) }
                            }
                        }
                    })
            }
            compose.waitUntil(45_000) { map.get() != null || failure.get() != null }
            assertTrue("Published UK map style failed: ${failure.get()}", map.get() != null)
            val dir = File(requireNotNull(ctx.externalMediaDirs.firstOrNull()), "additional_test_output")
                .apply { mkdirs() }
            for (city in cities) {
                compose.runOnIdle {
                    map.get()!!.moveCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder().target(LatLng(city.latitude, city.longitude))
                            .zoom(14.0).tilt(0.0).build()))
                }
                var foundRoads = false
                compose.waitUntil(45_000) {
                    compose.runOnIdle {
                        val v = view.get()!!
                        val seen = map.get()!!.queryRenderedFeatures(
                            RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()), *roads)
                        foundRoads = seen.isNotEmpty()
                    }
                    foundRoads || failure.get() != null
                }
                assertTrue("No published UK roads rendered at ${city.name}: ${failure.get()}", foundRoads)
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
