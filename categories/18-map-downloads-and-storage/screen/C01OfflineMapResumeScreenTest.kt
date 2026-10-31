package com.drivemate.tests.cat18

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivemate.app.data.MapData
import com.drivemate.app.data.MapLayout
import com.drivemate.app.data.MapPack
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.SSLSocket
import kotlin.random.Random

/**
 * C01.2 — offline map download, interruption/resume, restart and last-good retention, on the
 * Android emulator through the app's real production download path (MapPack.update) over TLS.
 *
 * The server is a controlled local HTTPS server that misbehaves on purpose. Resume logic does not
 * depend on archive size, so a small valid PMTiles-v3-headed archive stands in for the 1.8 GB
 * national file (which does not fit the 4 GB CI emulator disk alongside the app). No UK road data
 * or published-map success is claimed here.
 */
@RunWith(AndroidJUnit4::class)
class C01OfflineMapResumeScreenTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val tls = HttpServiceConsumerScreenTest()

    private fun archive(seed: Int, size: Int = 3 * 1024 * 1024): ByteArray =
        "PMTiles".toByteArray(Charsets.US_ASCII) + byteArrayOf(3) + Random(seed).nextBytes(size - 8)

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun download(url: String, bytes: ByteArray, tag: String) =
        MapData.Download(url, MapData.Asset(sha(bytes), bytes.size.toLong(), tag), MapLayout.OWN)

    private fun mapDir() = File(ctx.filesDir, "map")
    private fun parts() = mapDir().listFiles()?.filter { it.name.endsWith(".pmtiles.part") }.orEmpty()

    private fun reset() {
        ctx.getSharedPreferences("map_pack", 0).edit().clear().commit()
        mapDir().deleteRecursively()
        MapPack.load(ctx)
    }

    @Before fun setUp() = reset()
    @After fun tearDown() = reset()

    /** Serves [body()] with HEAD, full GET, Range/If-Range; [cutAfter] truncates a response. */
    private class Archive(var body: ByteArray, var etag: String) {
        val requests = CopyOnWriteArrayList<HttpServiceConsumerScreenTest.Request>()
        var cutAfter: Int? = null
        var ignoreRange = false
        var wrongOffset = false
        var corruptBody = false

        fun handle(request: HttpServiceConsumerScreenTest.Request, socket: SSLSocket) {
            requests.add(request)
            val out = socket.outputStream
            val bytes = if (corruptBody) body.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() } else body
            if (request.method == "HEAD") {
                out.write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nETag: $etag\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.US_ASCII)); out.flush(); return
            }
            val range = request.headers["range"]?.removePrefix("bytes=")?.removeSuffix("-")?.toLongOrNull()
            val ifRange = request.headers["if-range"]
            val resume = range != null && !ignoreRange && (ifRange == null || ifRange == etag)
            val start = if (resume) range!!.toInt() else 0
            val head = if (resume) {
                val claimed = if (wrongOffset) start + 1 else start
                "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes $claimed-${bytes.size - 1}/${bytes.size}\r\n" +
                    "Content-Length: ${bytes.size - start}\r\n"
            } else "HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\n"
            out.write((head + "ETag: $etag\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
            val end = cutAfter?.let { minOf(bytes.size, start + it) } ?: bytes.size
            out.write(bytes, start, end - start)
            out.flush()
            if (end < bytes.size) socket.close() // connection dropped mid-download
        }
    }

    private fun gets(a: Archive) = a.requests.filter { it.method == "GET" }

    @Test fun interruptedDownloadResumesFromItsPartAndSurvivesRestart() {
        val bytes = archive(1)
        val a = Archive(bytes, "\"v1\"")
        tls.withRequestServer(a::handle) { server ->
            val d = download("${server.url}/drivemate.pmtiles", bytes, "map-data-ukmap-r1-a1")
            a.cutAfter = 1024 * 1024
            assertTrue(runCatching { MapPack.update(ctx, {}, d) }.isFailure)
            assertNull("an interrupted download is never installed", MapPack.installed())
            val kept = parts().single().length()
            assertTrue("the partial bytes are kept for resume", kept in 1 until bytes.size)

            a.cutAfter = null
            MapPack.update(ctx, {}, d)
            val resumed = gets(a).last()
            assertEquals("bytes=$kept-", resumed.headers["range"])
            assertEquals("\"v1\"", resumed.headers["if-range"])
            val installed = requireNotNull(MapPack.installed())
            assertEquals(sha(bytes), sha(installed.readBytes()))
            assertTrue(parts().isEmpty())

            // App restart: state is re-read from storage; the verified map is still selected and
            // an unchanged manifest is satisfied locally with no further download.
            MapPack.load(ctx)
            assertEquals(installed, MapPack.installed())
            val before = a.requests.size
            MapPack.update(ctx, {}, d)
            assertEquals(before, a.requests.size)
        }
    }

    @Test fun changedFileOrIgnoredRangeRestartsFromZeroAndStillVerifies() {
        val v1 = archive(2)
        val a = Archive(v1, "\"v1\"")
        tls.withRequestServer(a::handle) { server ->
            a.cutAfter = 512 * 1024
            assertTrue(runCatching { MapPack.update(ctx, {}, download("${server.url}/m.pmtiles", v1, "map-data-ukmap-r2-a1")) }.isFailure)

            // Published file rebuilt: a new manifest identity discards the old part entirely.
            val v2 = archive(3)
            a.body = v2; a.etag = "\"v2\""; a.cutAfter = 512 * 1024
            val d2 = download("${server.url}/m.pmtiles", v2, "map-data-ukmap-r3-a1")
            assertTrue(runCatching { MapPack.update(ctx, {}, d2) }.isFailure)
            assertNull("old-version part is never resumed", gets(a).last().headers["range"])

            // Server ignores Range (200): the app rewrites from byte zero, never appends.
            a.cutAfter = null; a.ignoreRange = true
            MapPack.update(ctx, {}, d2)
            assertTrue(gets(a).last().headers.containsKey("range"))
            assertEquals(sha(v2), sha(requireNotNull(MapPack.installed()).readBytes()))
        }
    }

    @Test fun wrongResumeOffsetIsRejectedThenRecoversFromZero() {
        val bytes = archive(4)
        val a = Archive(bytes, "\"v1\"")
        tls.withRequestServer(a::handle) { server ->
            val d = download("${server.url}/m.pmtiles", bytes, "map-data-ukmap-r4-a1")
            a.cutAfter = 700 * 1024
            assertTrue(runCatching { MapPack.update(ctx, {}, d) }.isFailure)
            a.cutAfter = null; a.wrongOffset = true
            assertTrue(runCatching { MapPack.update(ctx, {}, d) }.isFailure)
            assertTrue("a part with a mismatched resume is discarded", parts().isEmpty())
            assertNull(MapPack.installed())
            a.wrongOffset = false
            MapPack.update(ctx, {}, d)
            assertNull(gets(a).last().headers["range"])
            assertEquals(sha(bytes), sha(requireNotNull(MapPack.installed()).readBytes()))
        }
    }

    @Test fun corruptNewDownloadKeepsTheLastGoodMap() {
        val good = archive(5)
        val a = Archive(good, "\"v1\"")
        tls.withRequestServer(a::handle) { server ->
            MapPack.update(ctx, {}, download("${server.url}/m.pmtiles", good, "map-data-ukmap-r5-a1"))
            val lastGood = requireNotNull(MapPack.installed())

            val next = archive(6)
            a.body = next; a.etag = "\"v2\""; a.corruptBody = true
            val error = runCatching { MapPack.update(ctx, {}, download("${server.url}/m.pmtiles", next, "map-data-ukmap-r6-a1")) }
            assertTrue(error.exceptionOrNull() is IOException)
            assertEquals("last good map stays selected", lastGood, MapPack.installed())
            assertEquals(sha(good), sha(lastGood.readBytes()))
            assertTrue("a complete but corrupt candidate is not kept for reuse", parts().isEmpty())
            assertFalse(mapDir().listFiles()!!.any { it.name.contains(sha(next)) })
        }
    }
}
