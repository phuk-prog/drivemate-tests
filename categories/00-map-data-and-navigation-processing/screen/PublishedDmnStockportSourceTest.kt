package com.drivemate.tests.cat00

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.Http
import com.drivemate.tests.cat18.HttpServiceConsumerScreenTest
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
 * Category 1 prerequisite: read an actually published Stockport DMNPACK2 piece
 * with the unmodified production Android graph reader. No UI, speech, whole UK,
 * regional MapPack worker or independently verified road legality is claimed.
 */
@RunWith(AndroidJUnit4::class)
class PublishedDmnStockportSourceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as Application

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val length = input.read(buffer)
                if (length < 0) break
                digest.update(buffer, 0, length)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test fun realPublishedStockportPieceLoadsThroughProductionAndroidDmnReader() {
        val indexText = instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
            .bufferedReader().use { it.readText() }
        val index = JSONObject(indexText)
        assertEquals(1, index.getInt("schema"))
        assertEquals("DMNPACK2", index.getString("format"))
        val piece = index.getJSONObject("tiles").getJSONObject("213_-9")
        val name = piece.getString("file")
        assertTrue(name.matches(Regex("^dmn-[A-Za-z0-9_-]+\\.dmnpack$")))
        val expectedSha = piece.getString("sha256")
        val expectedBytes = piece.getLong("bytes")
        assertTrue(expectedBytes in 1..(200L * 1024 * 1024))
        val dir = File(app.cacheDir, "cat1-real-dmn-stockport").apply {
            deleteRecursively()
            require(mkdirs())
        }
        try {
            val file = File(dir, name)
            instrumentation.context.assets.open("cat1-dmn-stockport-piece.dmnpack")
                .use { source -> file.outputStream().use { source.copyTo(it) } }
            assertEquals(expectedBytes, file.length())
            assertEquals(expectedSha, sha(file))
            File(dir, name + ".sha256").writeText(expectedSha)
            File(dir, "dmn-uk.json").writeText(indexText)
            val graph = file.inputStream().use { DmnPack.read(it) }
            assertTrue("Published DMN graph has no nodes", graph.nodeCount > 0)
            assertTrue("Published DMN graph has no edges", graph.edgeCount > 0)
            val routeArea = listOf(LatLon(53.4051, -2.1630), LatLon(53.4106, -2.1591))
            assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(dir, routeArea))
            val router = DmnTiles.router(dir, routeArea)
            assertNotNull("Real published graph could not be loaded by DmnTiles.router", router)
            assertTrue(requireNotNull(router).pack.edgeCount > 0)
            val receipt = JSONObject()
                .put("schema", 1)
                .put("issue", "CAT1-PUBLISHED-DMN-ANDROID-READER-01")
                .put("scope", "published_stockport_piece_android_parser_only")
                .put("tile_key", "213_-9")
                .put("published_release", index.getString("release"))
                .put("index_built", index.optString("built"))
                .put("index_source", index.optString("source"))
                .put("installed_sha256", sha(file))
                .put("installed_bytes", file.length())
                .put("node_count", graph.nodeCount)
                .put("edge_count", graph.edgeCount)
                .put("reader", "DmnPack.read + DmnTiles.router")
                .put("map_pack_worker_verified", false)
                .put("complete_uk_verified", false)
                .put("offline_route_computed", false)
                .put("guidance_screen_verified", false)
                .put("speech_verified", false)
                .put("independent_legal_truth_verified", false)
                .put("release_allowed", false)
            val outDir = File(requireNotNull(app.externalMediaDirs.firstOrNull()),
                "additional_test_output").apply { mkdirs() }
            File(outDir, "cat1-published-dmn-stockport-reader-receipt.json")
                .writeText(receipt.toString())
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Authentic published bytes over controlled TLS, using the production HTTP downloader
     * and installer. This is a declared one-tile subset, never whole-UK coverage. */
    @Test fun publishedRoadPackDownloadsInstallsRepairsAndRoutesOffline() {
        val index = JSONObject(instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
            .bufferedReader().use { it.readText() })
        val piece = index.getJSONObject("tiles").getJSONObject("213_-9")
        val name = piece.getString("file")
        val bytes = instrumentation.context.assets.open("cat1-dmn-stockport-piece.dmnpack")
            .use { it.readBytes() }
        val subset = JSONObject(index.toString()).apply {
            put("tiles", JSONObject().put("213_-9", piece))
            remove("main"); remove("regions")
        }.toString()
        val dir = File(app.cacheDir, "m02-real-published-dmn-install").apply {
            deleteRecursively(); require(mkdirs())
        }
        val oldText = DmnTiles.getText
        val oldDownload = DmnTiles.download
        var corruptResponse = true
        var downloadCalls = 0
        try {
            DmnTiles.forget()
            HttpServiceConsumerScreenTest().withServer({ path, socket ->
                val body = when (path) {
                    "/index" -> subset.toByteArray()
                    "/piece" -> if (corruptResponse) ByteArray(bytes.size) else bytes
                    else -> throw AssertionError("Unexpected request $path")
                }
                socket.outputStream.write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                socket.outputStream.write(body); socket.outputStream.flush()
            }) { server ->
                DmnTiles.getText = { Http.getBounded("${server.url}/index", 4 * 1024 * 1024) }
                DmnTiles.download = { url, target, limit ->
                    assertTrue(url.endsWith("/${index.getString("release")}/$name"))
                    assertEquals(piece.getLong("bytes"), limit)
                    downloadCalls++
                    Http.download("${server.url}/piece", target, limit)
                }
                try {
                    DmnTiles.fetchAll(dir)
                    fail("Corrupted published response must fail installation")
                } catch (_: java.io.IOException) { }
                assertFalse(File(dir, name).exists())
                assertFalse(File(dir, "$name.sha256").exists())
                corruptResponse = false
                DmnTiles.fetchAll(dir)
                val installed = File(dir, name)
                assertEquals(piece.getLong("bytes"), installed.length())
                assertEquals(piece.getString("sha256"), sha(installed))
                assertEquals(piece.getString("sha256"), File(dir, "$name.sha256").readText())
                DmnTiles.fetchAll(dir)
                assertEquals("Already verified file must not download again", 2, downloadCalls)
                installed.writeBytes(ByteArray(bytes.size))
                DmnTiles.fetchAll(dir)
                assertEquals("Matching stamp cannot hide local corruption", 3, downloadCalls)
                assertEquals(piece.getString("sha256"), sha(installed))
                val requestsAfterInstall = server.paths.size
                DmnTiles.getText = { throw AssertionError("Route must not fetch metadata") }
                DmnTiles.download = { _, _, _ -> throw AssertionError("Route must not download pieces") }
                val points = listOf(LatLon(53.4051, -2.1630), LatLon(53.4106, -2.1591))
                assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(dir, points))
                val router = requireNotNull(DmnTiles.router(dir, points))
                val start = requireNotNull(router.snap(points[0], null, 150.0))
                val finish = requireNotNull(router.snap(points[1], null, 150.0))
                assertNotEquals(start, finish)
                val plan = router.plan(start, finish)
                assertTrue("Real offline route must traverse roads", plan.edges.isNotEmpty())
                assertTrue(plan.metres > 100.0)
                assertTrue(plan.seconds > 0.0)
                assertEquals(requestsAfterInstall, server.paths.size)
                val receipt = JSONObject().put("schema", 1)
                    .put("scope", "published_stockport_subset_tls_download_install_offline_route")
                    .put("published_release", index.getString("release"))
                    .put("installed_sha256", sha(installed)).put("installed_bytes", installed.length())
                    .put("download_calls", downloadCalls).put("checksum_rejection_verified", true)
                    .put("corruption_repair_verified", true).put("offline_route_computed", true)
                    .put("route_metres", plan.metres).put("route_edges", plan.edges.size)
                    .put("route_time_network_requests", server.paths.size - requestsAfterInstall)
                    .put("complete_uk_verified", false).put("independent_legal_truth_verified", false)
                val out = File(requireNotNull(app.externalMediaDirs.firstOrNull()), "additional_test_output")
                    .apply { mkdirs() }
                File(out, "m02-published-dmn-install-route-receipt.json").writeText(receipt.toString())
            }
        } finally {
            DmnTiles.getText = oldText; DmnTiles.download = oldDownload
            DmnTiles.forget(); dir.deleteRecursively()
        }
    }

}
