package com.drivemate.tests.cat04

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.OfflineRouter
import com.drivemate.app.data.PublishedRegionCatalog
import com.drivemate.app.data.RegionPacks
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
            receipt("publishedStockportInstalledByProductionRegionDownloader",
                JSONObject().put("schema",1)
                    .put("scope","published_stockport_display_install_only")
                    .put("published_catalog_sha256",source.getString("catalog_sha256"))
                    .put("region_id","stockport")
                    .put("published_map_tag",region.map.tag)
                    .put("installed_sha256",sha(installed))
                    .put("installed_bytes",installed.length())
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
        assertTrue(OfflineRouter.covers(begin))
        assertTrue(OfflineRouter.covers(end))
        OfflineRouter.context=app
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
            val routes=OfflineRouter.route(listOf(begin,end),null,false,emptySet())
            assertTrue("Bundled Greater Manchester graph did not calculate a real route",routes.isNotEmpty())
            val route=routes.first()
            assertTrue("Actual route has insufficient geometry",route.points.size>=2)
            val progress=NavigationEngine(route).update(begin,null,0.0,3.0)
            assertNotNull(progress)
            disconnectedAfter=!reachable()
            assertTrue("Network became reachable during route computation",disconnectedAfter)
            receipt("bundledManchesterRouterActuallyComputesWithoutNetwork",
                JSONObject().put("schema",1)
                    .put("scope","bundled_greater_manchester_offline_route_only")
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
            // Do not contaminate another test even when native Valhalla fails.
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            shell("svc data enable")
            Thread.sleep(1500)
            assertTrue("Network was not restored after test",reachable())
        }
    }
}
