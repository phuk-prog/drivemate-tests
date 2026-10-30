package com.drivemate.tests.cat00

import android.app.Application
import android.content.ContextWrapper
import com.drivemate.app.dmn.Dmn
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


    /** Representative graph-data smoke routes, not externally verified journeys or road law. */
    @Test fun fourNationPublishedRoadPacksDownloadInstallSelectAndRouteOffline() {
        val index = JSONObject(instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
            .bufferedReader().use { it.readText() })
        val samples = JSONObject(instrumentation.context.assets.open("m02-four-nation-dmn-samples.json")
            .bufferedReader().use { it.readText() })
        val nations = listOf("england_manchester", "wales_cardiff",
            "northern_ireland_belfast", "scotland_edinburgh")
        assertEquals(nations.toSet(), samples.keys().asSequence().toSet())
        val tiles = JSONObject()
        val bodies = nations.associate { nation ->
            val sample = samples.getJSONObject(nation)
            val square = sample.getString("square")
            tiles.put(square, index.getJSONObject("tiles").getJSONObject(square))
            "/${sample.getString("file")}" to instrumentation.context.assets.open("m02-$nation.dmnpack")
                .use { it.readBytes() }
        }
        val subset = JSONObject(index.toString()).apply {
            put("tiles", tiles); remove("regions"); remove("main")
        }.toString().toByteArray()
        val dir = File(app.cacheDir, "m02-real-four-nation-install").apply {
            deleteRecursively(); require(mkdirs())
        }
        val oldText = DmnTiles.getText
        val oldDownload = DmnTiles.download
        try {
            DmnTiles.forget()
            HttpServiceConsumerScreenTest().withServer({ path, socket ->
                val body = if (path == "/index") subset else requireNotNull(bodies[path])
                socket.outputStream.write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\n" +
                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                socket.outputStream.write(body); socket.outputStream.flush()
            }) { server ->
                val requestedPieces = mutableListOf<String>()
                DmnTiles.getText = { Http.getBounded("${server.url}/index", 4 * 1024 * 1024) }
                DmnTiles.download = { url, target, limit ->
                    val name = url.substringAfterLast('/')
                    assertTrue(url.endsWith("/${index.getString("release")}/$name"))
                    requestedPieces += name
                    Http.download("${server.url}/$name", target, limit)
                }
                DmnTiles.fetchAll(dir)
                assertEquals(4, requestedPieces.size)
                assertEquals(tiles.keys().asSequence().sorted().map {
                    tiles.getJSONObject(it).getString("file") }.toList(), requestedPieces)
                val beforeRoutes = server.paths.size
                DmnTiles.getText = { throw AssertionError("Offline route must not request metadata") }
                DmnTiles.download = { _, _, _ -> throw AssertionError("Offline route must not download") }
                val receipts = JSONObject()
                for (nation in nations) {
                    val entry = samples.getJSONObject(nation)
                    val file = File(dir, entry.getString("file"))
                    assertEquals(entry.getLong("bytes"), file.length())
                    assertEquals(entry.getString("sha256"), sha(file))
                    assertEquals(entry.getString("sha256"), File(dir, file.name + ".sha256").readText())
                    val square = entry.getString("square").split('_').map { it.toInt() }
                    val centre = LatLon((square[0] + 0.5) / 4, (square[1] + 0.5) / 4)
                    val points = listOf(centre, centre)
                    assertEquals(DmnTiles.Offline.READY, DmnTiles.offline(dir, points))
                    val router = requireNotNull(DmnTiles.router(dir, points))
                    val graph = router.pack
                    val sourceGraph = file.inputStream().use { DmnPack.read(it) }
                    assertEquals(sourceGraph.nodeCount, graph.nodeCount)
                    assertEquals(sourceGraph.edgeCount, graph.edgeCount)
                    var chosenStart = -1
                    var chosenFinish = -1
                    var route: com.drivemate.app.dmn.DmnRouter.Plan? = null
                    // Pick genuine adjacent open edges. No synthetic coordinates/roads and no
                    // assertion that these are externally verified public legal journeys.
                    for (start in 0 until graph.edgeCount) {
                        if (graph.access[start].toInt() != 0 || graph.startAccess[start].toInt() == 2) continue
                        val node = graph.to[start]
                        for (k in graph.outStart[node] until graph.outStart[node + 1]) {
                            val finish = graph.outEdges[k]
                            if (graph.access[finish].toInt() != 0 || graph.finishAccess[finish].toInt() == 2 ||
                                graph.reverse[start] == finish || graph.metres[finish] < 5f ||
                                graph.banned(start, finish)) continue
                            route = runCatching { router.plan(start, finish) }.getOrNull()
                            if (route != null && route.edges.isNotEmpty() && route.metres >= 5) {
                                chosenStart = start; chosenFinish = finish; break
                            }
                        }
                        if (chosenStart >= 0) break
                    }
                    val plan = requireNotNull(route) { "No traversable real graph-data sample for $nation" }
                    assertTrue(chosenStart >= 0 && chosenFinish >= 0)
                    receipts.put(nation, JSONObject().put("square", entry.getString("square"))
                        .put("installed_sha256", sha(file)).put("installed_bytes", file.length())
                        .put("node_count", graph.nodeCount).put("edge_count", graph.edgeCount)
                        .put("start_edge", chosenStart).put("finish_edge", chosenFinish)
                        .put("start_osm_way", graph.wayId[graph.way[chosenStart]])
                        .put("finish_osm_way", graph.wayId[graph.way[chosenFinish]])
                        .put("route_edges", plan.edges.size).put("route_metres", plan.metres))
                }
                assertEquals(beforeRoutes, server.paths.size)
                val receipt = JSONObject().put("schema", 1)
                    .put("scope", "four_nation_published_tile_tls_install_graph_route_smoke")
                    .put("published_release", index.getString("release"))
                    .put("samples", receipts).put("download_calls", requestedPieces.size)
                    .put("route_time_network_requests", server.paths.size - beforeRoutes)
                    .put("complete_uk_verified", false).put("independent_legal_truth_verified", false)
                val out = File(requireNotNull(app.externalMediaDirs.firstOrNull()), "additional_test_output")
                    .apply { mkdirs() }
                File(out, "m02-four-nation-dmn-install-route-receipt.json").writeText(receipt.toString())
            }
        } finally {
            DmnTiles.getText = oldText; DmnTiles.download = oldDownload
            DmnTiles.forget(); dir.deleteRecursively()
        }
    }


    private fun actualDmnRouteUsesInstalledOrBundledGraph(installed: Boolean) {
        val root = File(app.cacheDir, "m02-dmn-route-selection-$installed").apply {
            deleteRecursively(); require(mkdirs())
        }
        val dir = File(root, "dmn").apply { require(mkdirs()) }
        val oldContext = Dmn.context
        val oldSource = Dmn.source
        val oldTestRouter = Dmn.testRouter
        val oldText = DmnTiles.getText
        val oldDownload = DmnTiles.download
        var builtInOpens = 0
        var metadataRequests = 0
        try {
            Dmn.forget(); DmnTiles.forget(); Dmn.testRouter = null
            Dmn.context = object : ContextWrapper(app) {
                override fun getNoBackupFilesDir(): File = root
            }
            // A real usable fallback, rather than throwing and allowing old code to
            // silently reach the installed graph after trying the stale bundle first.
            Dmn.source = { builtInOpens++; app.assets.open(Dmn.ASSET) }
            if (installed) {
                val index = JSONObject(instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
                    .bufferedReader().use { it.readText() })
                val piece = index.getJSONObject("tiles").getJSONObject("213_-9")
                val name = piece.getString("file")
                val file = File(dir, name)
                instrumentation.context.assets.open("cat1-dmn-stockport-piece.dmnpack")
                    .use { input -> file.outputStream().use { input.copyTo(it) } }
                assertEquals(piece.getLong("bytes"), file.length())
                assertEquals(piece.getString("sha256"), sha(file))
                File(dir, "$name.sha256").writeText(piece.getString("sha256"))
                File(dir, "dmn-uk.json").writeText(index.toString())
            }
            DmnTiles.getText = {
                metadataRequests++
                throw java.io.IOException("Offline test: no metadata transport")
            }
            DmnTiles.download = { _, _, _ -> throw AssertionError("Route selection must never download") }
            val points = listOf(LatLon(53.4051, -2.1630), LatLon(53.4106, -2.1591))
            val routes = Dmn.route(points, null)
            val out = File(requireNotNull(app.externalMediaDirs.firstOrNull()), "additional_test_output")
                .apply { mkdirs() }
            File(out, "m02-actual-dmn-selection-$installed.json").writeText(JSONObject()
                .put("schema", 1).put("installed_published_tile", installed)
                .put("builtin_opener_calls", builtInOpens).put("metadata_requests", metadataRequests)
                .put("routes", routes.size)
                .put("route_metres", routes.firstOrNull()?.distance ?: 0.0).toString())
            assertEquals(if (installed) "Current installed graph must supersede the old Manchester bundle"
                else "Missing installed graph must preserve genuine bundled offline fallback",
                if (installed) 0 else 1, builtInOpens)
            assertEquals("Manchester installed selection/bundled fallback must not request metadata",
                0, metadataRequests)
            assertFalse("Actual DMN routing must work with the selected genuine pack", routes.isEmpty())
            assertTrue(routes.first().distance > 100.0)
        } finally {
            Dmn.context = oldContext; Dmn.source = oldSource; Dmn.testRouter = oldTestRouter
            DmnTiles.getText = oldText; DmnTiles.download = oldDownload
            Dmn.forget(); DmnTiles.forget(); root.deleteRecursively()
        }
    }

    @Test fun actualDmnRoutePrefersCurrentInstalledPublishedGraphOverBundledManchester() {
        actualDmnRouteUsesInstalledOrBundledGraph(true)
    }

    @Test fun actualDmnRouteRetainsGenuineBundledFallbackWhenInstalledGraphIsMissing() {
        actualDmnRouteUsesInstalledOrBundledGraph(false)
    }


    /**
     * M03 real-geometry replay. Positions and adjacent ways are read from the actual
     * published Stockport DMNPACK2 bytes, not hand-drawn or asserted road-law facts.
     * An intersection shared by two allowed ways is ambiguous without travel heading.
     * Slip-road midpoints must never be confidently reported as a *different* way.
     */
    @Test fun publishedStockportGraphRefusesAmbiguousRoadAndWrongSlipWay() {
        val indexText = instrumentation.context.assets.open("cat1-dmn-stockport-index.json")
            .bufferedReader().use { it.readText() }
        val index = JSONObject(indexText)
        val piece = index.getJSONObject("tiles").getJSONObject("213_-9")
        val bytes = instrumentation.context.assets.open("cat1-dmn-stockport-piece.dmnpack")
            .use { it.readBytes() }
        assertEquals(piece.getLong("bytes"), bytes.size.toLong())
        assertEquals(piece.getString("sha256"), MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) })
        val graph = bytes.inputStream().use { DmnPack.read(it) }
        val router = com.drivemate.app.dmn.DmnRouter(graph)

        var junction = -1
        for (node in 0 until graph.nodeCount) {
            var firstWay = -1
            for (i in graph.outStart[node] until graph.outStart[node + 1]) {
                val edge = graph.outEdges[i]
                if (graph.access[edge].toInt() != 0) continue
                val way = graph.way[edge]
                if (firstWay == -1) firstWay = way
                else if (firstWay != way) { junction = node; break }
            }
            if (junction >= 0) break
        }
        assertTrue("Published Stockport graph has no shared traversable junction", junction >= 0)
        val junctionFix = LatLon(graph.lat[junction], graph.lon[junction])
        val started = android.os.SystemClock.elapsedRealtimeNanos()
        assertNull("Two genuine adjacent ways must remain unknown without bearing",
            router.snapForMatch(junctionFix, null, 10.0))
        assertNotNull("Observational uncertainty must not disable endpoint planning",
            router.snap(junctionFix, null))
        assertNull("Malformed GPS accuracy must never assert a real road",
            router.snapForMatch(junctionFix, null, Double.NaN))

        val slips = (0 until graph.edgeCount).asSequence().filter { e ->
            graph.access[e].toInt() == 0 && graph.metres[e] >= 6f &&
                graph.wayHighway[graph.way[e]].endsWith("_link")
        }.take(5).toList()
        assertTrue("Published Stockport tile must contain real traversable slip/link edges", slips.isNotEmpty())
        var confidentlyMatchedLinks = 0
        for (edge in slips) {
            val from = graph.from[edge]
            val to = graph.to[edge]
            val a = LatLon(graph.lat[from], graph.lon[from])
            val b = LatLon(graph.lat[to], graph.lon[to])
            val midpoint = LatLon((a.lat + b.lat) / 2, (a.lon + b.lon) / 2)
            val heading = com.drivemate.app.nav.Geo.bearing(a, b).toFloat()
            val observed = router.snapForMatch(midpoint, heading, 5.0)
            if (observed != null) {
                assertEquals("A real published slip must not be matched to a different OSM way",
                    graph.wayId[graph.way[edge]], graph.wayId[graph.way[observed]])
                confidentlyMatchedLinks++
            }
        }

        // Find a real close pair of distinct OSM ways in this published tile. This is a
        // geometry sample only: highway tags do not independently establish carriageway
        // layout, directionality, or legal lane choice. The production matcher should leave
        // a genuinely unresolved near-tie unknown.
        fun midpoint(e: Int): LatLon {
            val a = graph.from[e]; val b = graph.to[e]
            return LatLon((graph.lat[a] + graph.lat[b]) / 2.0, (graph.lon[a] + graph.lon[b]) / 2.0)
        }
        fun metres(a: LatLon, b: LatLon): Double {
            val dy = (a.lat - b.lat) * 111_000.0
            val dx = (a.lon - b.lon) * 111_000.0 * kotlin.math.cos(Math.toRadians(a.lat))
            return kotlin.math.sqrt(dx * dx + dy * dy)
        }
        fun axisDifference(a: Int, b: Int): Double {
            val delta = com.drivemate.app.nav.Geo.angleDiff(
                com.drivemate.app.nav.Geo.bearing(
                    LatLon(graph.lat[graph.from[a]], graph.lon[graph.from[a]]),
                    LatLon(graph.lat[graph.to[a]], graph.lon[graph.to[a]])),
                com.drivemate.app.nav.Geo.bearing(
                    LatLon(graph.lat[graph.from[b]], graph.lon[graph.from[b]]),
                    LatLon(graph.lat[graph.to[b]], graph.lon[graph.to[b]])))
            return minOf(delta, kotlin.math.abs(180.0 - delta))
        }
        fun distanceToEdge(p: LatLon, edge: Int): Double {
            val a = graph.from[edge]; val b = graph.to[edge]
            val k = kotlin.math.cos(Math.toRadians(p.lat))
            val ax = graph.lon[a] * k; val ay = graph.lat[a]
            val bx = graph.lon[b] * k; val by = graph.lat[b]
            val px = p.lon * k; val py = p.lat
            val dx = bx - ax; val dy = by - ay
            val t = if (dx == 0.0 && dy == 0.0) 0.0 else
                ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
            val c = t.coerceIn(0.0, 1.0)
            return kotlin.math.sqrt((ax + c * dx - px) * (ax + c * dx - px) +
                (ay + c * dy - py) * (ay + c * dy - py)) * 111_195.0
        }
        val candidates = (0 until graph.edgeCount).filter { e ->
            graph.access[e].toInt() == 0 && graph.metres[e] >= 12f &&
                graph.wayHighway[graph.way[e]] in setOf("motorway", "trunk", "primary", "secondary")
        }
        val bins = HashMap<Pair<Int, Int>, MutableList<Int>>()
        for (e in candidates) {
            val p = midpoint(e)
            val key = (p.lat * 1000).toInt() to (p.lon * 1000).toInt()
            bins.getOrPut(key) { mutableListOf() }.add(e)
        }
        var closePair: Pair<Int, Int>? = null
        search@ for (e in candidates) {
            val p = midpoint(e)
            val y = (p.lat * 1000).toInt(); val x = (p.lon * 1000).toInt()
            for (dy in -1..1) for (dx in -1..1) for (other in bins[(y + dy) to (x + dx)].orEmpty()) {
                if (graph.way[other] == graph.way[e] || graph.wayId[graph.way[other]] == graph.wayId[graph.way[e]]) continue
                val otherMid = midpoint(other)
                val centre = LatLon((p.lat + otherMid.lat) / 2.0, (p.lon + otherMid.lon) / 2.0)
                val d1 = distanceToEdge(centre, e); val d2 = distanceToEdge(centre, other)
                if (metres(p, otherMid) <= 30.0 && axisDifference(e, other) <= 20.0 &&
                    d1 <= 25.0 && d2 <= 25.0 && kotlin.math.abs(d1 - d2) <= 5.0 &&
                    router.snapForMatch(centre, null, 10.0) == null) {
                    closePair = e to other; break@search
                }
            }
        }
        assertNotNull("Published Stockport tile must provide a real close pair of distinct major-road ways", closePair)
        val pair = requireNotNull(closePair)
        val pairPoint = LatLon((midpoint(pair.first).lat + midpoint(pair.second).lat) / 2.0,
            (midpoint(pair.first).lon + midpoint(pair.second).lon) / 2.0)
        val pairDistance1 = distanceToEdge(pairPoint, pair.first)
        val pairDistance2 = distanceToEdge(pairPoint, pair.second)
        val pairMatch = router.snapForMatch(pairPoint, null, 10.0)
        assertNull("A near-tied real parallel/dual-carriageway geometry sample must remain observationally unknown", pairMatch)
        assertTrue(pairDistance1 <= 25.0 && pairDistance2 <= 25.0 &&
            kotlin.math.abs(pairDistance1 - pairDistance2) <= 5.0)

        // Replay a route made solely from consecutive edges in the authentic graph. A
        // distant outlier is injected between real on-route fixes; progress must recover
        // from the following source-backed fixes. This exercises graph planning and the
        // production NavigationEngine, while remaining a software simulation.
        val progressStart = pair.first
        fun nextSameWay(edge: Int): Int = (graph.outStart[graph.to[edge]] until graph.outStart[graph.to[edge] + 1])
            .asSequence().map { graph.outEdges[it] }
            .firstOrNull { graph.access[it].toInt() == 0 && graph.way[it] == graph.way[edge] &&
                graph.reverse[edge] != it && !graph.banned(edge, it) } ?: -1
        val firstContinuation = nextSameWay(progressStart)
        assertTrue("Published source way must continue through a real traversable edge", firstContinuation >= 0)
        val connectedFinish = nextSameWay(firstContinuation)
        assertTrue("Published source way must provide enough actual geometry for a progress replay", connectedFinish >= 0)
        val plan = router.plan(progressStart, connectedFinish)
        assertTrue("Real graph route must contain multiple traversed edges", plan.edges.size >= 2)
        val routePoints = ArrayList<LatLon>()
        routePoints += LatLon(graph.lat[graph.from[plan.edges.first()]], graph.lon[graph.from[plan.edges.first()]])
        for (edge in plan.edges) routePoints += LatLon(graph.lat[graph.to[edge]], graph.lon[graph.to[edge]])
        assertTrue("Real route geometry must provide progress samples", routePoints.size >= 3)
        val replayRoute = com.drivemate.app.data.Route(
            points = routePoints, distance = plan.metres, duration = plan.seconds,
            steps = listOf(com.drivemate.app.data.RouteStep("Continue", "arrive", "", "", plan.metres,
                routePoints.last())), summary = "published Stockport graph replay")
        val engine = com.drivemate.app.nav.NavigationEngine(replayRoute)
        val fixes = listOf(routePoints[0], routePoints[1], routePoints.last())
        val progress = fixes.map { engine.update(it, speed = 8.0, accuracy = 8.0) }
        assertTrue("Confirmed source-backed passage should advance route progress", progress.last().along > progress.first().along)
        val gpsFilter = com.drivemate.app.nav.GpsFilter().apply { smoothing = false }
        fun location(p: LatLon, timeNanos: Long): android.location.Location = android.location.Location("gps").apply {
            latitude = p.lat; longitude = p.lon; accuracy = 5f; elapsedRealtimeNanos = timeNanos
        }
        val baseTime = android.os.SystemClock.elapsedRealtimeNanos()
        val acceptedStart = requireNotNull(gpsFilter.accept(location(routePoints.first(), baseTime)))
        val rejectedOutlier = gpsFilter.accept(location(LatLon(54.2, -1.0), baseTime + 1_000_000_000L))
        val acceptedRecovery = gpsFilter.accept(location(routePoints.last(), baseTime + 8_000_000_000L))
        assertNull("Production GPS filter must reject the simulated impossible jump", rejectedOutlier)
        assertNotNull("Production GPS filter must accept a subsequent plausible source-backed fix", acceptedRecovery)
        val recoveryEngine = com.drivemate.app.nav.NavigationEngine(replayRoute)
        val filteredStart = recoveryEngine.update(LatLon(acceptedStart.latitude, acceptedStart.longitude), speed = 8.0, accuracy = 5.0)
        val filteredRecovery = requireNotNull(acceptedRecovery).let {
            recoveryEngine.update(LatLon(it.latitude, it.longitude), speed = 8.0, accuracy = 5.0)
        }
        assertTrue("Navigation progress must recover and advance only on the accepted real fix",
            filteredRecovery.along > filteredStart.along)

        // Warm and measure repeated production matching calls, including cache behavior.
        val perfEdges = (0 until graph.edgeCount).asSequence().filter { e ->
            graph.access[e].toInt() == 0 && graph.metres[e] >= 6f &&
                graph.wayHighway[graph.way[e]].endsWith("_link") && e !in slips
        }.distinctBy { graph.wayId[graph.way[it]] to graph.from[it] }.take(12).toList()
        assertTrue("Matcher performance sample must contain several distinct real link fixes", perfEdges.size >= 5)
        val perfFixes = perfEdges.map { e ->
            val a = graph.from[e]; val b = graph.to[e]
            midpoint(e) to com.drivemate.app.nav.Geo.bearing(
                LatLon(graph.lat[a], graph.lon[a]), LatLon(graph.lat[b], graph.lon[b])).toFloat()
        }
        val coldTimings = perfFixes.map { (p, heading) ->
            val t = android.os.SystemClock.elapsedRealtimeNanos()
            router.snapForMatch(p, heading, 5.0)
            android.os.SystemClock.elapsedRealtimeNanos() - t
        }.sorted()
        val cachedTimings = perfFixes.map { (p, heading) ->
            val t = android.os.SystemClock.elapsedRealtimeNanos()
            router.snapForMatch(p, heading, 5.0)
            android.os.SystemClock.elapsedRealtimeNanos() - t
        }.sorted()
        val slipEvidence = org.json.JSONArray().also { array -> slips.forEach { edge ->
            array.put(JSONObject().put("edge", edge).put("osm_way_id", graph.wayId[graph.way[edge]])
                .put("highway", graph.wayHighway[graph.way[edge]]).put("confident_match",
                    router.snapForMatch(midpoint(edge), com.drivemate.app.nav.Geo.bearing(
                        LatLon(graph.lat[graph.from[edge]], graph.lon[graph.from[edge]]),
                        LatLon(graph.lat[graph.to[edge]], graph.lon[graph.to[edge]])).toFloat(), 5.0) != null))
        } }
        val performanceEvidence = org.json.JSONArray().also { array -> perfEdges.forEach { edge ->
            array.put(JSONObject().put("edge", edge).put("osm_way_id", graph.wayId[graph.way[edge]])
                .put("highway", graph.wayHighway[graph.way[edge]]))
        } }
        val routeEvidence = org.json.JSONArray().also { array -> plan.edges.forEach { edge ->
            array.put(JSONObject().put("edge", edge).put("osm_way_id", graph.wayId[graph.way[edge]])
                .put("metres", graph.metres[edge].toDouble()))
        } }
        val elapsedMs = (android.os.SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
        val receipt = JSONObject().put("schema", 1)
            .put("scope", "m03_stockport_published_graph_junction_and_slip_samples")
            .put("published_release", index.optString("release"))
            .put("index_sha256", MessageDigest.getInstance("SHA-256")
                .digest(indexText.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) })
            .put("source_sha256", piece.getString("sha256"))
            .put("graph_edges", graph.edgeCount)
            .put("real_junction_node", (graph.nodeId?.get(junction)?.takeIf { it > 0 } ?: -1L))
            .put("real_junction_unknown", true)
            .put("sampled_slip_edges", slips.size)
            .put("slip_edge_samples", slipEvidence)
            .put("confident_slip_matches", confidentlyMatchedLinks)
            .put("close_distinct_major_way_pair_midpoint_separation_m", metres(midpoint(pair.first), midpoint(pair.second)))
            .put("close_pair_fix_distance_to_way_1_m", pairDistance1)
            .put("close_pair_fix_distance_to_way_2_m", pairDistance2)
            .put("close_pair_osm_way_ids", org.json.JSONArray().put(graph.wayId[graph.way[pair.first]])
                .put(graph.wayId[graph.way[pair.second]]))
            .put("close_pair_highway_tags", org.json.JSONArray().put(graph.wayHighway[graph.way[pair.first]])
                .put(graph.wayHighway[graph.way[pair.second]]))
            .put("close_pair_axis_difference_degrees", axisDifference(pair.first, pair.second))
            .put("ambiguous_parallel_or_dual_carriageway_match_unknown", pairMatch == null)
            .put("parallel_pair_road_law_or_carriageway_truth_verified", false)
            .put("real_graph_route_edges", plan.edges.size)
            .put("route_edge_samples", routeEvidence)
            .put("route_progress_start_m", filteredStart.along)
            .put("route_progress_after_filtered_recovery_m", filteredRecovery.along)
            .put("impossible_outlier_rejected_by_production_gps_filter", rejectedOutlier == null)
            .put("recovery_after_outlier", filteredRecovery.along > filteredStart.along)
            .put("uncached_distinct_fix_samples", coldTimings.size)
            .put("uncached_fix_edge_samples", performanceEvidence)
            .put("uncached_match_p50_ms", coldTimings[coldTimings.size / 2] / 1_000_000.0)
            .put("uncached_match_max_ms", coldTimings.last() / 1_000_000.0)
            .put("cached_repeat_samples", cachedTimings.size)
            .put("cached_match_p50_ms", cachedTimings[cachedTimings.size / 2] / 1_000_000.0)
            .put("cached_match_max_ms", cachedTimings.last() / 1_000_000.0)
            .put("performance_scope", "single published tile; cold distinct-link fixes and exact-key cache repeats")
            .put("sample_elapsed_ms", elapsedMs)
            .put("independent_legal_truth_verified", false)
            .put("physical_gps_replay_verified", false)
        File(File(requireNotNull(app.externalMediaDirs.firstOrNull()), "additional_test_output")
            .apply { mkdirs() }, "m03-published-stockport-gps-match.json").writeText(receipt.toString())
    }

}
