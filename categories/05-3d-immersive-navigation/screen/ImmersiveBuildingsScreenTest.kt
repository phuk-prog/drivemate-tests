package com.drivemate.tests.cat05

import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.map.BuildingLayers
import com.drivemate.app.nav.LatLon
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ImmersiveBuildingsScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun q5_7_8_9_10_13_18_buildingsFootprintsOcclusionAndStyleReload() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Both bundled layouts, day/night, use zero height for unknown buildings, not guessed 5m.
        for (asset in listOf("day", "night", "own-day", "own-night")) {
            val layers = JSONObject(ctx.assets.open("map-style/$asset.json").bufferedReader().use { it.readText() }).getJSONArray("layers")
            val footprint = (0 until layers.length()).map { layers.getJSONObject(it) }.first { it.getString("id") == "building" }
            assertFalse(footprint.has("maxzoom"))
        }
        val ref = AtomicReference<MapLibreMap?>(); val view = AtomicReference<MapView?>()
        val building = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"height":20},"geometry":{"type":"Polygon","coordinates":[[[-2.0002,52.9998],[-1.9998,52.9998],[-1.9998,53.0002],[-2.0002,53.0002],[-2.0002,52.9998]]]}}]}"""
        compose.setContent {
            AndroidView(Modifier.fillMaxSize().testTag("immersive-buildings"), factory = { context ->
                MapLibre.getInstance(context)
                MapView(context).also { v ->
                    view.set(v); v.onCreate(Bundle()); v.onStart(); v.onResume()
                    v.getMapAsync { map ->
                        map.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"land","type":"background","paint":{"background-color":"#e8ebe6"}}]}""")) { style ->
                            style.addSource(GeoJsonSource("buildings", building))
                            style.addLayer(FillLayer("building", "buildings").withProperties(fillColor("#a0a0a0")))
                            style.addLayer(FillExtrusionLayer("building-3d", "buildings").withProperties(fillExtrusionColor("#87959f")))
                            BuildingLayers.apply(style, true, false)
                            map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(53.0,-2.0)).zoom(17.0).tilt(50.0).build()))
                            ref.set(map)
                        }
                    }
                }
            })
        }
        compose.waitUntil(30000) { ref.get() != null }
        compose.runOnIdle {
            val map = ref.get()!!; val style = map.style!!
            val layer = style.getLayerAs<FillExtrusionLayer>("building-3d")!!
            assertEquals(0.75f, layer.fillExtrusionOpacity.value!!, 0.001f)
            BuildingLayers.apply(style, false, false)
            assertEquals(0f, layer.fillExtrusionOpacity.value!!, 0f)
            BuildingLayers.apply(style, true, true)
            assertEquals(0f, layer.fillExtrusionOpacity.value!!, 0f)
            BuildingLayers.apply(style, true, false)
            assertEquals(0.75f, layer.fillExtrusionOpacity.value!!, 0.001f)
        }
        val shot = AtomicReference<Bitmap?>()
        compose.runOnIdle { ref.get()!!.snapshot { shot.set(it) } }
        compose.waitUntil(30000) { shot.get() != null }
        val dir = File(ctx.externalMediaDirs.first(), "additional_test_output"); dir.mkdirs()
        File(dir,"q5_7_8_9_10_13_18_buildingsFootprintsOcclusionAndStyleReload.png").outputStream().use { shot.get()!!.compress(Bitmap.CompressFormat.PNG,100,it) }
        compose.runOnIdle { view.get()!!.onPause(); view.get()!!.onStop(); view.get()!!.onDestroy() }
    }
}
