package com.drivemate.tests.cat04

import android.Manifest
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.R
import com.drivemate.app.map.CarMarker
import com.drivemate.app.map.ImmersiveLayers
import com.drivemate.app.nav.*
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.geometry.LatLng
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic coordinates only. The real MapLibre location component and production marker layers draw on the emulator. */
@RunWith(AndroidJUnit4::class)
class JunctionMapScreenTest {
    @get:Rule val compose = createComposeRule()
    private val centre = LatLon(53.0, -2.0)
    @Test fun q4_3_4_12_14_15_geographicMapPointerAndMarkers() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        val ready = AtomicReference<MapLibreMap?>()
        val view = AtomicReference<MapView?>()
        val path = listOf(Geo.offset(centre, 180.0, 400.0), centre, Geo.offset(centre, 45.0, 100.0))
        val ring = (0..360 step 10).map { Geo.offset(centre, it.toDouble(), 40.0) }
        val roads = ImmersiveLayers.roads(listOf(path to null, ring to null))
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize().testTag("junction-map"), factory = { ctx ->
                MapLibre.getInstance(ctx)
                MapView(ctx).also { v ->
                    view.set(v); v.onCreate(Bundle()); v.onStart(); v.onResume()
                    v.getMapAsync { map ->
                        map.setStyle(Style.Builder().fromJson("""{"version":8,"sources":{},"layers":[{"id":"land","type":"background","paint":{"background-color":"#101820"}}]}""")) { style ->
                            style.addSource(GeoJsonSource("roads", roads))
                            style.addLayer(LineLayer("road-test", "roads").withProperties(lineColor("#8b969e"), lineWidth(18f)))
                            ImmersiveLayers.add(style, ctx.resources.displayMetrics.density) { style.addLayer(it) }
                            val boards = ImmersiveMap.countdownBoards(400.0, 0.0, ImmersiveMap.Kind.MOTORWAY_EXIT) { d -> Geo.offset(path.first(), 0.0, d) }
                            style.getSourceAs<GeoJsonSource>(ImmersiveLayers.SRC_BOARDS)!!.setGeoJson(ImmersiveLayers.boards(boards))
                            ImmersiveLayers.showMarks(style, true, instant = true)
                            CarMarker.show(map.locationComponent, ctx, style, R.drawable.ic_car_arrow, true)
                            map.locationComponent.isLocationComponentEnabled = true
                            map.locationComponent.renderMode = RenderMode.GPS
                            ready.set(map)
                        }
                    }
                }
            })
        }
        compose.waitUntil(30000) { ready.get() != null }
        val positions = mutableListOf<Float>()
        for (distance in listOf(250.0, 40.0)) {
            compose.runOnIdle {
                val map = ready.get()!!
                val car = Geo.offset(centre, 180.0, distance)
                val focus = JunctionViewPolicy.cameraCentre(car, centre, 1.0)
                map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                    .target(LatLng(focus.lat, focus.lon)).zoom(16.5).tilt(60.0).bearing(0.0).build()))
                map.locationComponent.forceLocationUpdate(Location("gps").apply {
                    latitude = car.lat; longitude = car.lon; accuracy = 3f; bearing = 0f; speed = 10f
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(); time = System.currentTimeMillis()
                })
                assertEquals(car.lat, map.locationComponent.lastKnownLocation!!.latitude, 1e-8)
                positions += map.projection.toScreenLocation(LatLng(car.lat, car.lon)).y
            }
        }
        assertTrue("The measured pointer changes screen position, rather than being fixed at the bottom", kotlin.math.abs(positions[0] - positions[1]) > 4f)
        val ringPositions = mutableListOf<Float>()
        for (angle in listOf(270.0, 0.0, 90.0)) compose.runOnIdle {
            val map = ready.get()!!
            val car = Geo.offset(centre, angle, 40.0)
            val focus = JunctionViewPolicy.cameraCentre(car, centre, 1.0)
            map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder()
                .target(LatLng(focus.lat, focus.lon)).zoom(17.0).tilt(60.0).bearing(0.0).build()))
            map.locationComponent.forceLocationUpdate(Location("gps").apply {
                latitude = car.lat; longitude = car.lon; accuracy = 3f; bearing = ((angle + 90.0) % 360).toFloat()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(); time = System.currentTimeMillis()
            })
            assertEquals(car.lon, map.locationComponent.lastKnownLocation!!.longitude, 1e-8)
            ringPositions += map.projection.toScreenLocation(LatLng(car.lat, car.lon)).x
        }
        assertTrue("Measured pointer travels around the ring", ringPositions.max() - ringPositions.min() > 4f)
        compose.onAllNodesWithTag("junction-map").assertCountEquals(1)
        val snapshot = AtomicReference<Bitmap?>()
        compose.runOnIdle { ready.get()!!.snapshot { snapshot.set(it) } }
        compose.waitUntil(30000) { snapshot.get() != null }
        val dir = File(context.externalMediaDirs.first(), "additional_test_output"); dir.mkdirs()
        File(dir, "q4_3_4_12_14_15_geographicMapPointerAndMarkers.png").outputStream().use {
            snapshot.get()!!.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.runOnIdle {
            val map = ready.get()!!
            map.locationComponent.isLocationComponentEnabled = JunctionViewPolicy.showPointer(true, false)
            assertFalse(map.locationComponent.isLocationComponentEnabled)
            view.get()!!.onPause(); view.get()!!.onStop(); view.get()!!.onDestroy()
        }
    }
    @Test fun q4_15_16_countdownAndExitBitmapsUseProductionColours() {
        val motorway = ImmersiveLayers.boardBitmap(3, true, 1f)
        val primary = ImmersiveLayers.boardBitmap(2, false, 1f)
        fun contains(bitmap: Bitmap, colour: Int): Boolean = (0 until bitmap.width).any { x -> (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == colour } }
        assertTrue(contains(motorway, ImmersiveLayers.MOTORWAY_BLUE))
        assertTrue(contains(primary, ImmersiveLayers.PRIMARY_GREEN))
        assertTrue(contains(ImmersiveLayers.exitBitmap(3, true, 1f), ImmersiveLayers.YOUR_EXIT))
        assertFalse(contains(ImmersiveLayers.exitBitmap(2, false, 1f), ImmersiveLayers.YOUR_EXIT))
        val night = JunctionScene.Palette(true); val day = JunctionScene.Palette(false)
        assertNotEquals(day.land, night.land)
    }
}
