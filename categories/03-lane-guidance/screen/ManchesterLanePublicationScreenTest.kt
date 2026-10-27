package com.drivemate.tests.cat03

import android.app.Application
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.MapData
import com.drivemate.app.data.OwnMap
import com.drivemate.app.nav.LatLon
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

/**
 * J27-01: tests the PUBLIC, actually published Stockport source file through
 * DriveMate's OWN on-device map downloader, not a manufactured lane layout.
 *
 * An asset being published does not establish the 2026 legal lane arrows or
 * guarantee the owner's installed release contains this data; that remains
 * provisional and needs an independent dated road-truth comparison.
 */
@RunWith(AndroidJUnit4::class)
class ManchesterLanePublicationScreenTest {
    @Test fun stockportLaneSquareCanBeDiscoveredDownloadedAndIndexedByTheApp() {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as Application
        val aroundPyramid = LatLon(53.40913, -2.17411)
        val square = "lanes-106_-5.json"
        MapData.init(app)
        MapData.focusRegion(aroundPyramid.lat, aroundPyramid.lon)
        val until = SystemClock.elapsedRealtime() + 50_000L
        var names = MapData.publishedNames()
        while (square !in names.orEmpty() && SystemClock.elapsedRealtime() < until) {
            Thread.sleep(500)
            names = MapData.publishedNames()
        }
        assertTrue(
            "PUBLISHED_LANE_SOURCE_UNAVAILABLE: app cannot discover the published Stockport " +
                "square via map catalogue + UK join; this is a source/runtime failure, not " +
                "proof that 2026 lane markings are missing",
            square in names.orEmpty()
        )
        val loader = OwnMap::class.java.getDeclaredField("laneLoader").apply { isAccessible=true }.get(OwnMap)
        val requests = loader.javaClass.getDeclaredField("requests").apply { isAccessible=true }.get(loader) as AtomicLong
        var db = OwnMap.lanesIfLoaded()
        var attempts = 0
        var receipt = ""
        var generationChanged = false
        var requestSuperseded = false
        do {
            val before = MapData.switchedAt()
            val request = OwnMap.beginLaneRequest()
            OwnMap.loadAlong(app, listOf(aroundPyramid), request)
            db = OwnMap.lanesIfLoaded()
            val after = MapData.switchedAt()
            val superseded = requests.get() != request
            generationChanged = generationChanged || before != after
            requestSuperseded = requestSuperseded || superseded
            attempts++
            val file = File(app.filesDir, "lanes/106_-5.json")
            receipt = "attempt=$attempts generation_changed=${before != after} request_superseded=$superseded cache_bytes=${if (file.isFile) file.length() else 0}"
            println("STOCKPORT_SOURCE_RECEIPT $receipt")
            // Match the production generation/request fence: discovery alone is not a stable
            // snapshot. Retry only a proven replacement, never an unexplained download failure.
            if (db != null || before == after && !superseded) break
        } while (attempts < 3 && SystemClock.elapsedRealtime() < until)
        val output = File(app.externalMediaDirs.first(), "additional_test_output").apply { mkdirs() }
        File(output, "stockport-source-receipt.json").writeText(JSONObject()
            .put("attempts", attempts).put("generation_changed", generationChanged)
            .put("request_superseded", requestSuperseded).put("index_loaded", db != null)
            .put("scope", "Published source availability, not verified road-law truth").toString())
        assertNotNull(
            "PUBLISHED_LANE_SOURCE_NOT_LOADED: app found the published square but could " +
                "not download/verify/index it; $receipt", db
        )
        val nearby = db!!.nearbySegments(aroundPyramid)
        assertTrue(
            "STOCKPORT_SOURCE_GEOMETRY_MISSING: indexed published square contains no " +
                "road pieces at this source-backed interchange vicinity",
            nearby.isNotEmpty()
        )
        val tagged = nearby.count { s -> s.way.tags.keys.any { it.startsWith("turn:lanes") } }
        // Counting a marked road in this test is NOT a recommendation: a route
        // manoeuvre, direction and full verified layout are required separately.
        println("J27_01_REAL_STOCKPORT_SOURCE indexed_roads=${nearby.size}; " +
            "roads_with_turn_lane_tags=$tagged; route_lane_guidance=NOT_TESTED; " +
            "2026_legal_lane_truth=UNKNOWN")
    }
}
