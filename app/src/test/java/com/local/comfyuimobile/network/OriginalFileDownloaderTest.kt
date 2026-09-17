package com.local.comfyuimobile.network

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OriginalFileDownloaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = ByteArray(256 * 1024 + 3) { ((it * 31 + it / 256) % 256).toByte() }
    private val client = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()
    private fun downloader(root: File, partSize: Long = 128 * 1024) =
        OriginalFileDownloader(client, root, parallelThreshold = 1024, targetPartSize = partSize)

    @Test fun twoThreeAndFourRangesAreConcurrentAndByteExact() = runBlocking {
        for ((count, partSize) in listOf(2 to 256_000L, 3 to 100_000L, 4 to 64_000L)) {
            val barrier = CountDownLatch(count)
            Server { request ->
                normal(request).also {
                    if (request.isPart) {
                        barrier.countDown()
                        check(barrier.await(5, TimeUnit.SECONDS)) { "Range requests were not concurrent" }
                    }
                }
            }.use { server ->
                val root = temporary.newFolder()
                val output = ClosingOutput()
                downloader(root, partSize).downloadTo(server.url, output)
                assertArrayEquals(bytes, output.toByteArray())
                assertTrue(output.closed)
                val parts = server.requests.filter { it.isPart }
                assertEquals(count, parts.size)
                assertTrue(parts.all { it.headers["if-range"] == "\"original-v1\"" })
                assertTrue(server.requests.all { it.headers["accept-encoding"] == "identity" })
                assertEquals(0, server.requests.count { it.range == null })
                assertClean(root)
            }
        }
    }

    @Test fun serverIgnoringRangeReusesTheFullResponseWithoutSecondDownload() = runBlocking {
        Server { Reply(200, body = bytes) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            downloader(root).downloadTo(server.url, output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(1, server.requests.size)
            assertClean(root)
        }
    }

    @Test fun smallFilesUseOneFullDownloadAndNoParallelParts() = runBlocking {
        Server(::normal).use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            OriginalFileDownloader(client, root, parallelThreshold = bytes.size.toLong() + 1).downloadTo(server.url, output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(listOf("bytes=0-0", null), server.requests.map { it.range })
            assertClean(root)
        }
    }

    @Test fun missingWeakOrMalformedEtagUsesFullDownload() = runBlocking {
        for (etag in listOf(null, "W/\"original-v1\"", "unquoted")) {
            Server { request -> normal(request).let { it.copy(headers = it.headers - "ETag" + listOfNotNull(etag?.let { tag -> "ETag" to tag }).toMap()) } }
                .use { server ->
                    val root = temporary.newFolder()
                    val output = ClosingOutput()
                    downloader(root).downloadTo(server.url, output)
                    assertArrayEquals(bytes, output.toByteArray())
                    assertEquals(0, server.requests.count { it.isPart })
                    assertEquals(1, server.requests.count { it.range == null })
                    assertClean(root)
                }
        }
    }

    @Test fun rejectedRangeProbeFallsBack() = runBlocking {
        for (code in listOf(400, 405, 416, 501)) {
            Server { if (it.range != null) Reply(code) else normal(it) }.use { server ->
                val root = temporary.newFolder()
                val output = ClosingOutput()
                downloader(root).downloadTo(server.url, output)
                assertArrayEquals(bytes, output.toByteArray())
                assertEquals(2, server.requests.size)
                assertClean(root)
            }
        }
    }

    @Test fun malformedProbeFallsBackWithoutSegments() = runBlocking {
        for (range in listOf("bytes 1-1/${bytes.size}", "bytes 0-0/*", "bytes 0-0/0", "bytes 0-0/99999999999999999999", "bad")) {
            verifyFallback { request, reply -> if (request.range == "bytes=0-0") reply.copy(headers = reply.headers + ("Content-Range" to range)) else reply }
        }
    }

    @Test fun incorrectRangeStartEndAndTotalCannotBeMerged() = runBlocking {
        for (range in listOf("bytes 1-10/${bytes.size}", "bytes 0-0/${bytes.size}", "bytes 0-999/999999", "bytes 5-3/${bytes.size}")) {
            verifyFallback { request, reply -> if (request.isPart) reply.copy(headers = reply.headers + ("Content-Range" to range)) else reply }
        }
    }

    @Test fun missingContentRangeCannotBeMerged() = runBlocking {
        verifyFallback { request, reply -> if (request.isPart) reply.copy(headers = reply.headers - "Content-Range") else reply }
    }

    @Test fun changingOrMissingVersionCannotBeMerged() = runBlocking {
        for (version in listOf(null, "\"new-v2\"", "W/\"original-v1\"")) {
            verifyFallback { request, reply ->
                if (request.isPart) reply.copy(headers = reply.headers - "ETag" + listOfNotNull(version?.let { "ETag" to it }).toMap()) else reply
            }
        }
    }

    @Test fun ignoredIfRangeAndRejectedPartsFallBackToFreshFullFile() = runBlocking {
        for (code in listOf(200, 412, 416, 500)) {
            verifyFallback { request, reply -> if (request.isPart) Reply(code, body = bytes) else reply }
        }
    }

    @Test fun fallbackUsesOneCoherentNewVersionNotMixtureOfOldAndNew() = runBlocking {
        val replacement = bytes.map { (it.toInt() xor 0x55).toByte() }.toByteArray()
        Server { request ->
            when {
                request.isPart -> normal(request).copy(headers = normal(request).headers + ("ETag" to "\"v2\""))
                request.range == null -> Reply(200, mapOf("ETag" to "\"v2\""), replacement)
                else -> normal(request)
            }
        }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            downloader(root).downloadTo(server.url, output)
            assertArrayEquals(replacement, output.toByteArray())
            assertClean(root)
        }
    }

    @Test fun truncatedAndOverlongPartsNeverReachDestination() = runBlocking {
        for (change in listOf(-1, 1)) verifyFallback { request, reply ->
            if (request.isPart) reply.copy(body = reply.body.copyOf(reply.body.size + change), chunked = true) else reply
        }
    }

    @Test fun wrongDeclaredPartLengthFallsBack() = runBlocking {
        verifyFallback { request, reply -> if (request.isPart) reply.copy(headers = reply.headers + ("Content-Length" to "1")) else reply }
    }

    @Test fun compressedSegmentsAreNotMistakenForOriginalBytes() = runBlocking {
        verifyFallback { request, reply -> if (request.isPart) reply.copy(headers = reply.headers + ("Content-Encoding" to "gzip")) else reply }
    }

    @Test fun probeRedirectPinsTheFinalResourceForAllParts() = runBlocking {
        Server(::normal).use { target ->
            Server { Reply(302, mapOf("Location" to target.url)) }.use { source ->
                val root = temporary.newFolder()
                val output = ClosingOutput()
                downloader(root).downloadTo(source.url, output)
                assertArrayEquals(bytes, output.toByteArray())
                assertEquals(1, source.requests.size)
                assertTrue(target.requests.any { it.isPart })
                assertEquals(0, target.requests.count { it.range == null })
                assertClean(root)
            }
        }
    }

    @Test fun redirectedPartCannotBeCombinedWithOriginalResource() = runBlocking {
        Server(::normal).use { other ->
            verifyFallback { request, reply -> if (request.isPart) Reply(302, mapOf("Location" to other.url)) else reply }
        }
    }

    @Test fun chunkedRangesWithNoContentLengthStillRequireExactByteCounts() = runBlocking {
        Server { normal(it).copy(chunked = true) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            downloader(root).downloadTo(server.url, output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(0, server.requests.count { it.range == null })
            assertClean(root)
        }
    }

    @Test fun unknownLengthFullDownloadUsesHttpFraming() = runBlocking {
        Server { Reply(200, body = bytes, chunked = true) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            downloader(root).downloadTo(server.url, output)
            assertArrayEquals(bytes, output.toByteArray())
            assertClean(root)
        }
    }

    @Test fun truncatedFullDownloadFailsWithoutPublishingAnyBytes() = runBlocking {
        Server { Reply(200, mapOf("Content-Length" to (bytes.size + 10).toString()), bytes) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            assertTrue(runCatching { downloader(root).downloadTo(server.url, output) }.exceptionOrNull() is IOException)
            assertEquals(0, output.size())
            assertTrue(output.closed)
            assertClean(root)
        }
    }

    @Test fun fallbackFailureIsNotReportedAsSuccess() = runBlocking {
        Server { if (it.range == "bytes=0-0") normal(it) else Reply(503) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            assertTrue(runCatching { downloader(root).downloadTo(server.url, output) }.exceptionOrNull() is IOException)
            assertEquals(0, output.size())
            assertTrue(output.closed)
            assertClean(root)
        }
    }

    @Test fun cancellationClosesBlockedSocketsBeforeHeadersDuringProbePartsAndFullBody() = runBlocking {
        for (mode in listOf("headers", "probe", "parts", "full")) {
            val entered = CountDownLatch(1)
            val disconnected = CountDownLatch(1)
            Server { request ->
                val stall = when (mode) {
                    "headers", "probe", "full" -> true
                    else -> request.isPart
                }
                if (!stall) normal(request) else {
                    val reply = if (mode == "full") Reply(200, mapOf("Content-Length" to bytes.size.toString())) else normal(request)
                    reply.copy(stall = true, beforeHeaders = mode == "headers", entered = entered, disconnected = disconnected)
                }
            }.use { server ->
                val root = temporary.newFolder()
                val output = ClosingOutput()
                val job = launch(Dispatchers.IO) { downloader(root).downloadTo(server.url, output) }
                assertTrue("no request for $mode", entered.await(5, TimeUnit.SECONDS))
                withTimeout(3_000) { job.cancelAndJoin() }
                assertTrue("socket not cancelled for $mode", disconnected.await(3, TimeUnit.SECONDS))
                assertTrue(job.isCancelled)
                assertTrue(output.closed)
                assertEquals(0, output.size())
                assertEquals(0, server.requests.count { it.range == null })
                assertClean(root)
            }
        }
    }

    @Test fun oneBadPartCancelsStalledSiblingsBeforeFallback() = runBlocking {
        val entered = CountDownLatch(2)
        val disconnected = CountDownLatch(1)
        Server { request ->
            if (!request.isPart) normal(request) else {
                entered.countDown()
                check(entered.await(5, TimeUnit.SECONDS))
                if (request.range!!.startsWith("bytes=0-")) Reply(416)
                else normal(request).copy(stall = true, disconnected = disconnected)
            }
        }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            withTimeout(5_000) { downloader(root, partSize = 256_000).downloadTo(server.url, output) }
            assertTrue(disconnected.await(3, TimeUnit.SECONDS))
            assertArrayEquals(bytes, output.toByteArray())
            assertClean(root)
        }
    }

    @Test fun atomicFileDownloadPreservesExistingFileOnFailureAndReplacesItOnSuccess() = runBlocking {
        val root = temporary.newFolder()
        val target = File(temporary.newFolder(), "original.png").apply { writeText("previous") }
        Server { Reply(503) }.use { server ->
            assertTrue(runCatching { downloader(root).downloadToFile(server.url, target) }.isFailure)
            assertEquals("previous", target.readText())
            assertClean(root)
        }
        Server(::normal).use { server ->
            downloader(root).downloadToFile(server.url, target)
            assertArrayEquals(bytes, target.readBytes())
            assertClean(root)
        }
    }

    @Test fun outputWriteFailureClosesStreamAndCleansTemporaryFiles() = runBlocking {
        Server(::normal).use { server ->
            val root = temporary.newFolder()
            val output = object : ClosingOutput() {
                override fun write(buffer: ByteArray, offset: Int, length: Int) { throw IOException("disk full") }
            }
            assertTrue(runCatching { downloader(root).downloadTo(server.url, output) }.isFailure)
            assertTrue(output.closed)
            assertClean(root)
        }
    }

    @Test fun cancellationWhilePublishingOutputStopsCopyAndCleansStaging() = runBlocking {
        Server(::normal).use { server ->
            val root = temporary.newFolder()
            lateinit var job: Job
            val output = object : ClosingOutput() {
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    super.write(buffer, offset, length)
                    job.cancel()
                }
            }
            job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { downloader(root).downloadTo(server.url, output) }
            job.start()
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
            assertTrue(output.closed)
            assertTrue(output.size() < bytes.size)
            assertClean(root)
        }
    }

    @Test fun cancelledFileDownloadNeverPublishesPartialFile() = runBlocking {
        val entered = CountDownLatch(1)
        Server { request -> normal(request).copy(stall = true, entered = entered) }.use { server ->
            val root = temporary.newFolder()
            val target = File(temporary.newFolder(), "original.png")
            val job = launch(Dispatchers.IO) { downloader(root).downloadToFile(server.url, target) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withTimeout(3_000) { job.cancelAndJoin() }
            assertFalse(target.exists())
            assertClean(root)
        }
    }

    @Test fun simultaneousManualAndAutomaticDownloadsUseIsolatedTemporaryFiles() = runBlocking {
        Server(::normal).use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            val target = File(temporary.newFolder(), "cached.png")
            val manual = async { downloader(root).downloadTo(server.url, output) }
            val automatic = async { downloader(root).downloadToFile(server.url, target) }
            awaitAll(manual, automatic)
            assertArrayEquals(bytes, output.toByteArray())
            assertArrayEquals(bytes, target.readBytes())
            assertClean(root)
        }
    }

    @Test fun nextTransferCleansOrphansButDoesNotDeleteUnrelatedFiles() = runBlocking {
        val root = temporary.newFolder()
        File(root, "transfer-old-process").apply { mkdir(); resolve("part-0").writeText("partial") }
        File(root, "unrelated").writeText("keep")
        Server(::normal).use { server -> downloader(root).downloadTo(server.url, ClosingOutput()) }
        assertEquals(listOf("unrelated"), root.listFiles()!!.map { it.name })
    }

    @Test fun publicComfyClientEntryPointsUseValidatedDownloads() = runBlocking {
        Server { Reply(200, body = bytes) }.use { server ->
            val root = temporary.newFolder()
            val api = ComfyClient(root)
            val output = ClosingOutput()
            api.downloadTo(server.url, output)
            val target = File(temporary.newFolder(), "cached.png")
            api.downloadToFile(server.url, target)
            assertArrayEquals(bytes, output.toByteArray())
            assertArrayEquals(bytes, target.readBytes())
            assertClean(root)
        }
    }

    @Test fun publicClientUsesParallelDownloadAboveTheRealDefaultThreshold() = runBlocking {
        val large = ByteArray(8 * 1024 * 1024 + 1) { (it * 17).toByte() }
        Server { request ->
            val range = request.range
            if (range == null) Reply(200, body = large) else {
                val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
                Reply(206, mapOf("Content-Range" to "bytes $start-$end/${large.size}", "ETag" to "\"large\""), large.copyOfRange(start, end + 1))
            }
        }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            ComfyClient(root).downloadTo(server.url, output)
            assertArrayEquals(large, output.toByteArray())
            assertEquals(2, server.requests.count { it.isPart })
            assertEquals(0, server.requests.count { it.range == null })
            assertClean(root)
        }
    }

    @Test fun localPartCorruptionIsDetectedBySha256BeforePublishing() = runBlocking {
        val releaseLast = CountDownLatch(1)
        Server { request ->
            if (request.isPart && !request.range!!.startsWith("bytes=0-")) {
                check(releaseLast.await(5, TimeUnit.SECONDS))
            }
            normal(request)
        }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            val download = async(Dispatchers.IO) { downloader(root, partSize = 256_000).downloadTo(server.url, output) }
            try {
                val part = withTimeout(5_000) {
                    var found: File? = null
                    while (found == null) {
                        found = root.walkTopDown().firstOrNull { it.name == "part-0" && it.length() == bytes.size.toLong() / 2 }
                        if (found == null) delay(10)
                    }
                    found
                }
                RandomAccessFile(part, "rw").use { it.write(99) }
            } finally {
                releaseLast.countDown()
            }
            download.await()
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(1, server.requests.count { it.range == null })
            assertClean(root)
        }
    }

    private suspend fun verifyFallback(change: (Incoming, Reply) -> Reply) {
        Server { change(it, normal(it)) }.use { server ->
            val root = temporary.newFolder()
            val output = ClosingOutput()
            downloader(root).downloadTo(server.url, output)
            assertArrayEquals(bytes, output.toByteArray())
            assertEquals(1, server.requests.count { it.range == null })
            assertClean(root)
        }
    }

    private fun normal(request: Incoming): Reply {
        val range = request.range ?: return Reply(200, body = bytes)
        val (start, end) = range.removePrefix("bytes=").split('-').map { it.toInt() }
        return Reply(206, mapOf("Content-Range" to "bytes $start-$end/${bytes.size}", "ETag" to "\"original-v1\""), bytes.copyOfRange(start, end + 1))
    }

    private fun assertClean(root: File) = assertEquals("leaked download files", emptyList<String>(), root.listFiles()!!.map { it.name })

    private open class ClosingOutput : ByteArrayOutputStream() {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    private data class Incoming(val headers: Map<String, String>) {
        val range get() = headers["range"]
        val isPart get() = range != null && range != "bytes=0-0"
    }

    private data class Reply(
        val code: Int,
        val headers: Map<String, String> = emptyMap(),
        val body: ByteArray = byteArrayOf(),
        val chunked: Boolean = false,
        val stall: Boolean = false,
        val beforeHeaders: Boolean = false,
        val entered: CountDownLatch? = null,
        val disconnected: CountDownLatch? = null,
    )

    /** Real sockets exercise OkHttp framing, cancellation and simultaneous connections. */
    private class Server(private val respond: (Incoming) -> Reply) : AutoCloseable {
        private val listener = ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${listener.localPort}/view?filename=original.png"
        val requests = CopyOnWriteArrayList<Incoming>()
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val workers = CopyOnWriteArrayList<Thread>()
        private val failures = CopyOnWriteArrayList<Throwable>()
        private val acceptor = thread(isDaemon = true) {
            while (!listener.isClosed) {
                val socket = runCatching { listener.accept() }.getOrNull() ?: break
                sockets += socket
                workers += thread(isDaemon = true) {
                    socket.use {
                        var reply: Reply? = null
                        try {
                            socket.soTimeout = 10_000
                            val reader = socket.getInputStream().bufferedReader()
                            reader.readLine() ?: return@thread
                            val headers = mutableMapOf<String, String>()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                            }
                            val incoming = Incoming(headers)
                            requests += incoming
                            reply = respond(incoming)
                            val output = socket.getOutputStream()
                            if (!reply.beforeHeaders) {
                                val framing = if (reply.chunked) mapOf("Transfer-Encoding" to "chunked")
                                    else mapOf("Content-Length" to reply.body.size.toString())
                                val allHeaders = framing + reply.headers + ("Connection" to "close")
                                output.write(("HTTP/1.1 ${reply.code} Test\r\n" + allHeaders.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray())
                                output.flush()
                            }
                            reply.entered?.countDown()
                            if (reply.stall) {
                                while (socket.getInputStream().read() != -1) { /* wait for client cancellation */ }
                            } else if (reply.chunked) {
                                if (reply.body.isNotEmpty()) {
                                    output.write("${reply.body.size.toString(16)}\r\n".toByteArray())
                                    output.write(reply.body)
                                    output.write("\r\n".toByteArray())
                                }
                                output.write("0\r\n\r\n".toByteArray())
                            } else output.write(reply.body)
                        } catch (_: IOException) {
                            // Expected: the downloader closes rejected/cancelled responses early.
                        } catch (error: Throwable) {
                            failures += error
                        } finally {
                            reply?.disconnected?.countDown()
                        }
                    }
                }
            }
        }

        override fun close() {
            listener.close()
            sockets.forEach { runCatching { it.close() } }
            acceptor.join(1_000)
            workers.forEach { it.join(1_000) }
            if (failures.isNotEmpty()) throw AssertionError("HTTP test server failed", failures.first())
        }
    }
}
