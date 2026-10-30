package com.drivemate.tests.cat04

import android.app.Application
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import org.junit.Rule
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.dmn.Dmn
import com.drivemate.app.data.PublishedRegionCatalog
import com.drivemate.app.data.RegionPacks
import com.drivemate.app.data.MapPack
import com.drivemate.app.data.MapLayout
import com.drivemate.app.data.OwnMap
import com.drivemate.app.nav.LatLon
import com.drivemate.app.nav.NavigationEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

/**
 * Two intentionally separate production-path tests on genuine UK locations.
 *
 * (1) The Android app's RegionPacks.sync downloads authentic Stockport display
 *     PMTiles from a publisher release and verifies its installed file SHA.
 * (2) A genuine Greater Manchester route is calculated using the *bundled*
 *     offline graph with on-device network reachability observed before/during.
 *
 * Neither alone establishes published *whole UK graph*, screen guidance,
 * dated road-law correctness or end-to-end source -> UI acceptance.
 */
@RunWith(AndroidJUnit4::class)
class PublishedStockportOfflineScreenTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf=ByteArray(64 * 1024)
            while (true) {
                val n=stream.read(buf)
                if (n<0) break
                digest.update(buf,0,n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun receipt(test: String, body: JSONObject) {
        val dir = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
            "additional_test_output").apply { mkdirs() }
        File(dir, "$test-receipt.json").writeText(body.toString())
    }


    /**
     * C01.2/C01.3: the exact production-installed, SHA-verified Stockport PMTiles
     * must render real road layers in MapLibre while the *emulator* has no network.
     * Regional component acceptance only: does not certify nationwide offline map.
     */
    private fun verifyInstalledStockportRendersOffline(installed: File): Int {
        assertTrue("Network precondition is required for an honest offline proof", reachable())
        val originalMapContext = MapPack.context
        val map = AtomicReference<MapLibreMap?>()
        val view = AtomicReference<MapView?>()
        val failure = AtomicReference<String?>()
        val completedFrames = AtomicLong()
        var roadCount = 0
        try {
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            shell("svc data disable")
            Thread.sleep(1500)
            assertFalse("Device can still reach the Internet after isolation", reachable())
            val tiles = "pmtiles://file://" + installed.absolutePath
            val style = OwnMap.prepare(OwnMap.appStyle(false, MapLayout.OWN), tiles, null).toString()
            MapPack.context = app
            compose.setContent {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    MapLibre.getInstance(context)
                    MapView(context).also { v ->
                        view.set(v)
                        v.addOnDidFinishRenderingFrameListener(
                            MapView.OnDidFinishRenderingFrameWithStatsListener { done, _ ->
                                if (done) completedFrames.incrementAndGet()
                            })
                        v.addOnDidFailLoadingMapListener { failure.set("Offline local PMTiles style failed") }
                        v.onCreate(Bundle()); v.onStart(); v.onResume()
                        v.getMapAsync { m ->
                            m.setStyle(Style.Builder().fromJson(style)) { map.set(m) }
                        }
                    }
                })
            }
            compose.waitUntil(45_000) { map.get() != null || failure.get() != null }
            assertNotNull("MapLibre failed to load offline file: " + failure.get(), map.get())
            val before = completedFrames.get()
            compose.runOnIdle {
                map.get()!!.moveCamera(CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder().target(LatLng(53.4051, -2.1630))
                        .zoom(14.0).tilt(0.0).build()))
            }
            val roadLayers = arrayOf("road-primary", "road-secondary", "road-tertiary",
                "road-minor", "road-service", "road-trunk", "road-motorway")
            compose.waitUntil(45_000) {
                if (completedFrames.get() <= before) return@waitUntil false
                compose.runOnIdle {
                    val v = view.get()
                    if (v != null && v.width > 0 && v.height > 0) {
                        roadCount = map.get()!!.queryRenderedFeatures(
                            RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()), *roadLayers).size
                    }
                }
                roadCount > 0 || failure.get() != null
            }
            assertTrue("Local offline PMTiles has no rendered Stockport roads: " + failure.get(), roadCount > 0)
            assertFalse("Network became reachable during local rendered map proof", reachable())
            val screenshot = AtomicReference<Bitmap?>()
            compose.runOnIdle { map.get()!!.snapshot { screenshot.set(it) } }
            compose.waitUntil(15_000) { screenshot.get() != null }
            val output = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            File(output, "C01-stockport-verified-offline-render.png").outputStream().use {
                screenshot.get()!!.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.get()!!.recycle()
            return roadCount
        } finally {
            compose.runOnIdle { view.get()?.let { it.onPause(); it.onStop(); it.onDestroy() } }
            MapPack.context = originalMapContext
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            shell("svc data enable")
            Thread.sleep(1500)
            assertTrue("Network not restored after local map test", reachable())
        }
    }

    @Test fun publishedStockportInstalledByProductionRegionDownloader() {
        val data=instrumentation.context.assets.open("stockport-live-region.json")
            .bufferedReader().use { it.readText() }
        val json=JSONObject(data)
        val source=json.getJSONObject("test_source")
        assertEquals("actual_published_catalog_subset",source.getString("kind"))
        assertEquals("stockport",source.getString("region"))
        assertFalse(source.getBoolean("offline_navigation_accepted"))
        val catalog=PublishedRegionCatalog.parse(data)
        assertEquals(1,catalog.regions.size)
        val region=catalog.regions.single()
        assertEquals("stockport",region.id)
        try {
            // Calls the unmodified *production* release downloader and installer.
            RegionPacks.sync(app,catalog) {}
            val installed=requireNotNull(RegionPacks.local(region)) {
                "Production Stockport map is absent after sync"
            }
            assertTrue(installed.isFile)
            assertEquals(region.map.bytes,installed.length())
            assertEquals(region.map.sha256,sha(installed))
            val offlineRoads = verifyInstalledStockportRendersOffline(installed)
            receipt("publishedStockportInstalledByProductionRegionDownloader",
                JSONObject().put("schema",1)
                    .put("scope","published_stockport_display_install_only")
                    .put("published_catalog_sha256",source.getString("catalog_sha256"))
                    .put("region_id","stockport")
                    .put("published_map_tag",region.map.tag)
                    .put("installed_sha256",sha(installed))
                    .put("installed_bytes",installed.length())
                    .put("offline_map_screen_rendered",true)
                    .put("offline_rendered_road_features",offlineRoads)
                    .put("network_isolated_during_render",true)
                    .put("whole_uk_offline_map_accepted",false)
                    .put("production_installer","RegionPacks.sync")
                    .put("offline_route_accepted",false)
                    .put("screen_guidance_accepted",false))
        } finally {
            // Emulator-only installation; do not leave test map packs in app data.
            RegionPacks.deleteAll(app)
        }
    }

    private fun shell(command: String) {
        // Shell exit status alone is not network-isolation evidence; real device
        // socket probes check transport before and after the routed journey.
        val fd = instrumentation.uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use {
            val buffer=ByteArray(1024)
            while (it.read(buffer)>0) { }
        }
    }

    private fun reachable(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("1.1.1.1",443),2500) }
        true
    } catch (_: Exception) {
        false
    }

    @Test fun bundledManchesterRouterActuallyComputesWithoutNetwork() {
        val begin = LatLon(53.4051,-2.1630) // Stockport station area (real UK position)
        val end = LatLon(53.4106,-2.1591)   // Stockport town-centre area
        assertTrue(Dmn.covers(listOf(begin)))
        assertTrue(Dmn.covers(listOf(end)))
        Dmn.context=app
        assertTrue("Online baseline is required for a real isolation test",reachable())
        var disconnectedBefore=false
        var disconnectedAfter=false
        try {
            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            shell("svc data disable")
            Thread.sleep(1500)
            disconnectedBefore=!reachable()
            assertTrue("Network still reachable after isolation; no offline proof",disconnectedBefore)
            val routes=Dmn.route(listOf(begin,end),null)
            assertTrue("Bundled Greater Manchester graph did not calculate a real route",routes.isNotEmpty())
            val route=routes.first()
            assertTrue("Actual route has insufficient geometry",route.points.size>=2)
            val progress=NavigationEngine(route).update(begin,null,0.0,3.0)
            assertNotNull(progress)
            disconnectedAfter=!reachable()
            assertTrue("Network became reachable during route computation",disconnectedAfter)
            receipt("bundledManchesterRouterActuallyComputesWithoutNetwork",
                JSONObject().put("schema",1)
                    .put("scope","bundled_dmn_greater_manchester_offline_route_only")
                    .put("origin","stockport_station_area")
                    .put("destination","stockport_town_centre_area")
                    .put("graph_source","bundled_app_asset_not_published_whole_uk")
                    .put("network_blocked_before_route",disconnectedBefore)
                    .put("network_blocked_after_route",disconnectedAfter)
                    .put("route_points",route.points.size)
                    .put("route_steps",route.steps.size)
                    .put("published_whole_uk_graph_verified",false)
                    .put("production_guidance_screen_verified",false)
                    .put("legal_truth_verified",false)
                    .put("full_offline_acceptance",false))
        } finally {
            // Restore connectivity after the network-isolated DMN route check.
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            shell("svc data enable")
            Thread.sleep(1500)
            assertTrue("Network was not restored after test",reachable())
        }
    }
}
