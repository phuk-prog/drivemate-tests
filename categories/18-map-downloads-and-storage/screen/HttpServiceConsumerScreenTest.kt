package com.drivemate.tests.cat18

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivemate.app.data.Http
import com.drivemate.app.data.HttpError
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/** Current Category 4 (historical folder 18): actual Android Http consumer on controlled TLS.
 * Metadata bodies only, no fabricated UK road data or external-service success claims.
 * Test-only key/certificate are public fixtures. Default TLS factory is restored in finally;
 * the hostname verifier, app network security policy and production sources stay unchanged.
 * Baseline actual Android execution is UNRUN until public emulator CI executes this cohort.
 */
@RunWith(AndroidJUnit4::class)
class HttpServiceConsumerScreenTest {
    private fun failure(block: () -> Unit): IOException {
        try { block() } catch (error: IOException) { return error }
        throw AssertionError("Expected an actual HTTP/transport failure")
    }

    private fun response(socket: SSLSocket, code: Int, body: String, extra: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.outputStream.write(("HTTP/1.1 $code Result\r\nContent-Type: application/json\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n$extra\r\n").toByteArray(Charsets.US_ASCII))
        socket.outputStream.write(bytes)
        socket.outputStream.flush()
    }

    internal class LocalTlsServer(context: SSLContext, val handle: (String, SSLSocket) -> Unit) : Closeable {
        private val listener = context.serverSocketFactory.createServerSocket(
            0, 16, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        private val connections = CopyOnWriteArrayList<SSLSocket>()
        private val workers = CopyOnWriteArrayList<Thread>()
        val paths = CopyOnWriteArrayList<String>()
        val url = "https://127.0.0.1:${listener.localPort}"
        private val acceptor = Thread({
            while (!listener.isClosed) {
                val socket = try { listener.accept() as SSLSocket } catch (_: IOException) { break }
                connections.add(socket)
                val worker = Thread({
                    try {
                        socket.use {
                            socket.soTimeout = 3000
                            val reader = socket.inputStream.bufferedReader(Charsets.US_ASCII)
                            val first = reader.readLine() ?: return@use
                            val path = first.split(' ').getOrNull(1) ?: return@use
                            var count = 0
                            while (true) {
                                val line = reader.readLine() ?: return@use
                                if (line.isEmpty()) break
                                check(++count < 100) { "Unexpected request header volume" }
                            }
                            paths.add(path)
                            handle(path, socket)
                        }
                    } catch (_: IOException) {
                        // Expected for rejected TLS, deliberately truncated/closed responses and timeout cleanup.
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    } finally { connections.remove(socket) }
                }, "controlled-tls-response").apply { isDaemon = true }
                workers.add(worker)
                worker.start()
            }
        }, "controlled-tls-listener").apply { isDaemon = true; start() }
        override fun close() {
            listener.close()
            connections.forEach { runCatching { it.close() } }
            acceptor.join(1000)
            workers.forEach { it.interrupt(); it.join(1000) }
        }
    }

    private fun contexts(): Pair<SSLContext, SSLContext> {
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(
            TEST_CERT.byteInputStream(Charsets.US_ASCII))
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(
            Base64.getMimeDecoder().decode(TEST_KEY)))
        val password = "controlled-public-test-key".toCharArray()
        val serverStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setKeyEntry("controlled", privateKey, password, arrayOf(certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(serverStore, password)
        }
        val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null); setCertificateEntry("controlled", certificate)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(trustStore)
        }
        val server = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, null) }
        val client = SSLContext.getInstance("TLS").apply { init(null, trustManagers.trustManagers, null) }
        return server to client
    }

    internal fun withServer(handler: (String, SSLSocket) -> Unit, test: (LocalTlsServer) -> Unit) {
        synchronized(TLS_LOCK) {
            val original = HttpsURLConnection.getDefaultSSLSocketFactory()
            val (serverContext, clientContext) = contexts()
            try {
                LocalTlsServer(serverContext, handler).use { server ->
                    HttpsURLConnection.setDefaultSSLSocketFactory(clientContext.socketFactory)
                    test(server)
                }
            } finally { HttpsURLConnection.setDefaultSSLSocketFactory(original) }
        }
    }

    @Test fun boundedJsonAndAuthenticationStatusesComeFromRealAndroidRequests() {
        withServer({ path, socket ->
            val code = path.removePrefix("/").toIntOrNull() ?: 200
            response(socket, code, "{\"ok\":true}")
        }) { server ->
            assertTrue(JSONObject(Http.getBounded("${server.url}/200", 64)).getBoolean("ok"))
            for (status in listOf(401, 403, 404)) {
                val error = failure { Http.getBounded("${server.url}/$status", 64) }
                assertTrue("A server response must retain HttpError status", error is HttpError)
                assertEquals(status, (error as HttpError).code)
                assertTrue(Http.describe(error).contains(status.toString()))
            }
            assertEquals(listOf("/200", "/401", "/403", "/404"), server.paths.toList())
        }
    }

    @Test fun boundedBodyOverflowRejectsThenNextRequestRecovers() {
        withServer({ path, socket -> response(socket, 200, if (path == "/large") "x".repeat(129) else "{\"ok\":true}") }) { server ->
            val error = failure { Http.getBounded("${server.url}/large", 128) }
            assertFalse(error is HttpError)
            assertEquals("Published data exceeds size limit", error.message)
            assertTrue(JSONObject(Http.getBounded("${server.url}/ok", 64)).getBoolean("ok"))
        }
    }

    @Test fun transientServerErrorNeedsExplicitRetryAndThenSucceeds() {
        val requests = AtomicInteger()
        withServer({ _, socket ->
            response(socket, if (requests.incrementAndGet() == 1) 503 else 200, "{\"ok\":true}")
        }) { server ->
            val error = failure { Http.get("${server.url}/retry") }
            assertTrue(error is HttpError)
            assertEquals(503, (error as HttpError).code)
            assertEquals("Consumer must not silently replay a failed request", 1, requests.get())
            assertTrue(JSONObject(Http.get("${server.url}/retry")).getBoolean("ok"))
            assertEquals(2, requests.get())
        }
    }

    @Test fun responseTimeoutIsTransportFailureAndExplicitRetryRecovers() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            withServer({ path, socket ->
                if (path == "/slow") { arrived.countDown(); release.await(5, TimeUnit.SECONDS) }
                else response(socket, 200, "{\"ok\":true}")
            }) { server ->
                val error = failure { Http.getQuick("${server.url}/slow", 3000, 1000) }
                assertTrue("Server actually received the request", arrived.await(1, TimeUnit.SECONDS))
                assertTrue("Timeout must remain distinct from an HTTP error: ${error.javaClass.simpleName}", error is SocketTimeoutException)
                assertFalse(error is HttpError)
                release.countDown()
                assertTrue(JSONObject(Http.getQuick("${server.url}/ok", 1000, 1000)).getBoolean("ok"))
            }
        } finally { release.countDown() }
    }

    @Test fun truncatedResponseIsRejectedAndFollowingRequestRecovers() {
        withServer({ path, socket ->
            if (path == "/truncated") {
                socket.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: 200\r\nConnection: close\r\n\r\n{\"ok\":".toByteArray(Charsets.US_ASCII))
                socket.outputStream.flush()
            } else response(socket, 200, "{\"ok\":true}")
        }) { server ->
            val error = failure { Http.getBounded("${server.url}/truncated", 512, 1000) }
            assertFalse("An incomplete body is not a server status", error is HttpError)
            assertTrue(JSONObject(Http.getBounded("${server.url}/ok", 64)).getBoolean("ok"))
        }
    }

    @Test fun sameOriginHttpsRedirectReachesFinalJsonWithoutChangingHostPolicy() {
        withServer({ path, socket ->
            if (path == "/redirect") response(socket, 302, "", "Location: /final\r\n")
            else response(socket, 200, "{\"ok\":true}")
        }) { server ->
            assertTrue(JSONObject(Http.getBounded("${server.url}/redirect", 64)).getBoolean("ok"))
            assertEquals(listOf("/redirect", "/final"), server.paths.toList())
        }
    }

    @Test fun untrustedCertificateFailsTlsBeforeAnyHttpStatusThenTrustedTestContextRecovers() {
        synchronized(TLS_LOCK) {
            val original = HttpsURLConnection.getDefaultSSLSocketFactory()
            val (serverContext, clientContext) = contexts()
            try {
                LocalTlsServer(serverContext) { _, socket -> response(socket, 200, "{\"ok\":true}") }.use { server ->
                    val error = failure { Http.getQuick("${server.url}/untrusted", 1000, 1000) }
                    assertTrue("Untrusted certificate must remain a TLS failure: ${error.javaClass.simpleName}", error is SSLException)
                    assertFalse(error is HttpError)
                    assertTrue("No HTTP request is sent before trusted handshake", server.paths.isEmpty())
                    HttpsURLConnection.setDefaultSSLSocketFactory(clientContext.socketFactory)
                    assertTrue(JSONObject(Http.getBounded("${server.url}/trusted", 64)).getBoolean("ok"))
                    assertEquals(listOf("/trusted"), server.paths.toList())
                }
            } finally { HttpsURLConnection.setDefaultSSLSocketFactory(original) }
        }
    }

    companion object {
        private val TLS_LOCK = Any()
        // Public test-only localhost certificate, valid 2026-10-28 through 2046-10-23.
        private val TEST_CERT = """-----BEGIN CERTIFICATE-----
MIIBtDCCAVugAwIBAgIUR0P34bJ+XXpMi8PnRdaRobcS8UAwCgYIKoZIzj0EAwIw
KTEnMCUGA1UEAwweRHJpdmVNYXRlIGNvbnRyb2xsZWQgdGVzdCBvbmx5MB4XDTI2
MTAyODE5MjMyOFoXDTQ2MTAyMzE5MjMyOFowKTEnMCUGA1UEAwweRHJpdmVNYXRl
IGNvbnRyb2xsZWQgdGVzdCBvbmx5MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
Cl+lBnFznI6p8Wx5+RTaC7UXzqsPpRBe0qk8SQzyM17u6thy+CdPK4SDnTAg7Nyp
R9yMAOZoIM0Pe3/VZcamlKNhMF8wHQYDVR0OBBYEFH4KCeWDYbDHV5P8hfCpdEQK
08FwMB8GA1UdIwQYMBaAFH4KCeWDYbDHV5P8hfCpdEQK08FwMA8GA1UdEQQIMAaH
BH8AAAEwDAYDVR0TAQH/BAIwADAKBggqhkjOPQQDAgNHADBEAiBT8cnJHBoz0e9w
rdK7BarIj8fZEMbsdmeFm5kXrCcmOwIgY6j4v7VlMsZCJBzlay2PJlNm/EDXqGLO
vDCgUFB0sZs=
-----END CERTIFICATE-----""".trimIndent()
        // PKCS8 EC key for this test certificate only; never used by a real service.
        private val TEST_KEY = """MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgHCDmANF3A8L4tWcW
dbVq3hOWXOq1crCFakcASQMUmiChRANCAAQKX6UGcXOcjqnxbHn5FNoLtRfOqw+l
EF7SqTxJDPIzXu7q2HL4J08rhIOdMCDs3KlH3IwA5mggzQ97f9VlxqaU""".trimIndent()
    }
}
