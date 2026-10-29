package com.drivemate.tests.cat00

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.dmn.DmnPack
import com.drivemate.app.dmn.DmnTiles
import com.drivemate.app.nav.LatLon
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Category 1 / TF-07 prerequisite: calculate a real route using ONE authentic
 * published Stockport DMNPACK2 road piece. Sample coordinates are the existing
 * source-reader test's fixed real Stockport endpoints, not fabricated road
 * topology. A failed snap or no-route is diagnostic RED, never silently skipped.
 *
 * NOT a MapPack.Worker install, full-UK route, network-isolation, rendered screen,
 * voice, independently verified UK legal road truth, or release acceptance.
 */
@RunWith(AndroidJUnit4::class)
class PublishedDmnStockportRouteTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = inst.targetContext.applicationContext as Application

    private fun sha(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { src ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = src.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun realPublishedStockportPieceActuallyComputesDmnRoute() {
        val indexText = inst.context.assets.open("cat1-dmn-stockport-index.json")
            .bufferedReader().use { it.readText() }
        val index = JSONObject(indexText)
        assertEquals(1, index.getInt("schema"))
        assertEquals("DMNPACK2", index.getString("format"))
        val piece = index.getJSONObject("tiles").getJSONObject("213_-9")
        val name = piece.getString("file")
        assertTrue(name.matches(Regex("^dmn-[A-Za-z0-9_-]+\\.dmnpack$")))
        val expectedSha = piece.getString("sha256")
        val expectedBytes = piece.getLong("bytes")
        val dir = File(app.cacheDir, "cat1-real-stockport-route").apply {
            deleteRecursively()
            require(mkdirs())
        }
        try {
            val file = File(dir, name)
            inst.context.assets.open("cat1-dmn-stockport-piece.dmnpack")
                .use { source -> file.outputStream().use { source.copyTo(it) } }
            assertEquals(expectedBytes, file.length())
            assertEquals(expectedSha, sha(file))
            File(dir, name + ".sha256").writeText(expectedSha)
            File(dir, "dmn-uk.json").writeText(indexText)
            val graph = file.inputStream().use { DmnPack.read(it) }
            val points = listOf(LatLon(53.4051, -2.1630), LatLon(53.4106, -2.1591))
            assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(dir, points))
            val router = requireNotNull(DmnTiles.router(dir, points)) {
                "Authentic published Stockport piece did not load into production router"
            }
            val start = requireNotNull(router.snap(points[0], null, 25.0)) {
                "START_NOT_MAPPED: real Stockport sample coordinate cannot snap"
            }
            val finish = requireNotNull(router.snap(points[1], null, 25.0)) {
                "FINISH_NOT_MAPPED: real Stockport sample coordinate cannot snap"
            }
            val route = router.plan(start, finish)
            assertTrue("EMPTY_DMN_ROUTE", route.edges.isNotEmpty())
            assertTrue("ZERO_OR_INVALID_ROUTE_LENGTH", route.metres > 0.0 && route.metres.isFinite())
            assertTrue("ZERO_OR_INVALID_ROUTE_TIME", route.seconds > 0.0 && route.seconds.isFinite())
            assertEquals(start, route.start)
            assertTrue(route.finish in 0 until graph.edgeCount)
            var last = start
            for (edge in route.edges) {
                assertTrue("OUT_OF_RANGE_EDGE", edge in 0 until graph.edgeCount)
                assertEquals("DISCONNECTED_ROUTE_EDGE", graph.to[last], graph.from[edge])
                assertFalse("MAPPED_BANNED_TURN", graph.banned(last, edge))
                assertFalse("MIDROAD_UTURN", router.midroadUturn(last, edge))
                last = edge
            }
            assertEquals("ROUTE_END_EDGE_MISMATCH", route.finish, last)

            val receipt = JSONObject()
                .put("schema", 1)
                .put("issue", "CAT1-PUBLISHED-DMN-ROUTE-01")
                .put("scope", "published_stockport_single_piece_android_router_plan_only")
                .put("tile_key", "213_-9")
                .put("published_release", index.getString("release"))
                .put("installed_sha256", sha(file))
                .put("installed_bytes", file.length())
                .put("node_count", graph.nodeCount)
                .put("edge_count", graph.edgeCount)
                .put("start_lat", points[0].lat).put("start_lon", points[0].lon)
                .put("finish_lat", points[1].lat).put("finish_lon", points[1].lon)
                .put("start_edge", start).put("finish_edge", route.finish)
                .put("planned_edges", route.edges.size)
                .put("planned_metres", route.metres).put("planned_seconds", route.seconds)
                .put("route_contiguous_and_turns_checked", true)
                .put("dmn_route_computed", true)
                .put("map_pack_worker_installed", false)
                .put("whole_uk_route_verified", false)
                .put("device_network_isolated", false)
                .put("guidance_screen_verified", false)
                .put("speech_verified", false)
                .put("independent_legal_truth_verified", false)
                .put("release_allowed", false)
            val out = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            File(out, "cat1-published-dmn-route-receipt.json").writeText(receipt.toString())
        } finally {
            dir.deleteRecursively()
        }
    }
}
