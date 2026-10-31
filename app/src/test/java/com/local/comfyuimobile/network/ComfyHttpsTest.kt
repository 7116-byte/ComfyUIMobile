package com.local.comfyuimobile.network

import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real local TLS sockets, with a short-lived test certificate generated at runtime. */
class ComfyHttpsTest {
    companion object {
        @ClassRule @JvmField val folder = TemporaryFolder()
        private val tls by lazy {
            val file = File(folder.root, "localhost.p12")
            val password = "local-test-only".toCharArray()
            val bin = File(System.getProperty("java.home"), "bin")
            val keytool = File(bin, if (File(bin, "keytool.exe").exists()) "keytool.exe" else "keytool")
            val process = ProcessBuilder(keytool.path, "-genkeypair", "-alias", "localhost", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", file.path,
                "-storepass", String(password), "-keypass", String(password), "-noprompt")
                .redirectErrorStream(true).redirectOutput(File(folder.root, "certificate-generation.txt")).start()
            check(process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); "Test certificate generation timed out" }
            check(process.exitValue() == 0) { "Test certificate generation failed" }
            val keys = KeyStore.getInstance("PKCS12").apply { file.inputStream().use { load(it, password) } }
            val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys, password) }
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("localhost", keys.getCertificate("localhost"))
            }
            val trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
            val trust = trusts.trustManagers.filterIsInstance<X509TrustManager>().single()
            val server = SSLContext.getInstance("TLS").apply { init(keyManagers.keyManagers, null, SecureRandom()) }
            val client = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
            Triple(server, client, trust)
        }
    }

    private val executor = Executors.newCachedThreadPool()
    private var server: SSLServerSocket? = null
    private val clients = mutableListOf<OkHttpClient>()
    private val requests = AtomicInteger()
    private val requestLine = AtomicReference("")

    @After fun close() {
        server?.close()
        executor.shutdownNow()
        clients.forEach { it.connectionPool.evictAll(); it.dispatcher.executorService.shutdownNow() }
    }

    private fun fixture(code: Int = 200, contentType: String = "application/json", body: String = """{"system":{"comfyui_version":"tls-fixture"},"devices":[]}""", location: String? = null, webSocket: Boolean = false): String {
        val service = tls.first.serverSocketFactory.createServerSocket(0, 50, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        server = service
        executor.execute {
            while (!service.isClosed) {
                val socket = runCatching { service.accept() as SSLSocket }.getOrNull() ?: break
                executor.execute {
                    socket.use {
                        runCatching {
                            it.soTimeout = 6_000
                            it.startHandshake()
                            val reader = it.inputStream.bufferedReader(Charsets.US_ASCII)
                            requestLine.set(reader.readLine().orEmpty())
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                            }
                            requests.incrementAndGet()
                            val output = it.outputStream
                            if (webSocket) {
                                val accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                                    .digest((headers.getValue("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                                output.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $accept\r\n\r\n".toByteArray(Charsets.US_ASCII))
                                output.flush()
                                // Wait for the test client to start closing, then reply with a normal close frame.
                                it.inputStream.read()
                                output.write(byteArrayOf(0x88.toByte(), 0x02, 0x03, 0xe8.toByte()))
                                output.flush()
                            } else {
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                val redirect = location?.let { value -> "Location: $value\r\n" }.orEmpty()
                                output.write("HTTP/1.1 $code Fixture\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n${redirect}\r\n".toByteArray(Charsets.US_ASCII))
                                output.write(bytes)
                                output.flush()
                            }
                        }.onFailure { failure ->
                            System.err.println("Local TLS fixture: ${failure.javaClass.name}: ${failure.message}")
                        }
                    }
                }
            }
        }
        return "https://127.0.0.1:${service.localPort}"
    }

    private fun trustedClient(): ComfyClient {
        val client = OkHttpClient.Builder().sslSocketFactory(tls.second.socketFactory, tls.third).build()
        clients += client
        return ComfyClient(folder.newFolder(), client)
    }

    @Test fun probesRealHttpsAndKeepsHttpsForOutputUrls() = runBlocking {
        val base = fixture()
        val client = trustedClient()
        val (stats, profile) = client.probe(base)
        assertEquals("tls-fixture", stats.comfyVersion)
        assertEquals("127.0.0.1", profile.name)
        assertEquals(base, profile.baseUrl)
        client.setServer(profile.baseUrl)
        assertEquals("$base/view?filename=image.png&subfolder=&type=output", client.mediaUrl("image.png", "", "output"))
        assertEquals(1, requests.get())
    }

    @Test fun productionTrustRejectsUntrustedCertificate() = runBlocking {
        val base = fixture()
        val failure = runCatching { ComfyClient(folder.newFolder()).probe(base) }.exceptionOrNull()
        assertTrue("Untrusted certificates must not be ignored: $failure", failure is SSLException)
        assertEquals(0, requests.get())
    }

    @Test fun httpsInstanceOpensRealSecureWebSocket() {
        val base = fixture(webSocket = true)
        val client = trustedClient()
        client.setServer(base)
        val ready = CountDownLatch(1)
        val opened = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        try {
            client.openWebSocket("mobile 1", {}, { failure.set(it); ready.countDown() }, { opened.set(true); ready.countDown() })
            assertTrue(ready.await(8, TimeUnit.SECONDS))
            assertTrue("WSS connection failed: ${failure.get()}", opened.get())
            assertEquals("GET /ws?clientId=mobile+1 HTTP/1.1", requestLine.get())
        } finally { client.closeWebSocket() }
    }

    @Test fun platformLoginHtmlGetsActionableErrorInsteadOfJsonParseFailure() = runBlocking {
        val base = fixture(contentType = "text/html", body = "<!doctype html><html>Sign in</html>")
        val failure = runCatching { trustedClient().probe(base) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("登录页"))
    }

    @Test fun blockedCloudEndpointExplainsUnsupportedAuthentication() = runBlocking {
        val base = fixture(code = 403, body = "access denied")
        val failure = runCatching { trustedClient().probe(base) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("HTTP 403"))
        assertTrue(failure?.message.orEmpty().contains("访问授权"))
    }

    @Test fun apiRedirectCannotDowngradeTlsOrVisitUnrelatedHosts() = runBlocking {
        val base = fixture(code = 302, location = "http://127.0.0.1:9/should-not-connect")
        val failure = runCatching { trustedClient().probe(base) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("最终的 HTTPS"))
        assertEquals(1, requests.get())
    }
}
