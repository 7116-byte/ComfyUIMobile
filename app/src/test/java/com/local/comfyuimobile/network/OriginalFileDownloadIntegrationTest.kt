package com.local.comfyuimobile.network

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Opt-in, read-only test against an existing /view output. Never queues generation. */
class OriginalFileDownloadIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun liveOriginalIsIdenticalWithTwoAndFourRanges() = runBlocking {
        val url = System.getenv("COMFY_DOWNLOAD_TEST_URL")
        assumeTrue("Set COMFY_DOWNLOAD_TEST_URL to an existing original output", !url.isNullOrBlank())
        val requests = CopyOnWriteArrayList<String?>()
        val client = OkHttpClient.Builder().addNetworkInterceptor { chain ->
            requests += chain.request().header("Range")
            chain.proceed(chain.request())
        }.build()
        val expected = temporary.newFile("ordinary.bin")
        val ordinaryMs = measureTimeMillis {
            client.newCall(Request.Builder().url(url!!).header("Accept-Encoding", "identity").build()).execute().use { response ->
                assertEquals(200, response.code)
                expected.outputStream().use { output -> response.body!!.byteStream().use { it.copyTo(output) } }
            }
        }
        val expectedHash = hash(expected)
        for (count in listOf(2, 4)) {
            requests.clear()
            val root = temporary.newFolder()
            val destination = temporary.newFile("parallel-$count.bin")
            val downloader = OriginalFileDownloader(client, root, parallelThreshold = 2, targetPartSize = (expected.length() + count - 1) / count)
            val elapsed = measureTimeMillis { downloader.downloadToFile(url!!, destination) }
            assertEquals(expected.length(), destination.length())
            assertArrayEquals(expectedHash, hash(destination))
            assertEquals(count, requests.count { it != null && it != "bytes=0-0" })
            assertFalse("Unexpected full-download fallback", requests.contains(null))
            assertEquals(0, root.listFiles()!!.size)
            println("Read-only local original: bytes=${expected.length()}, parts=$count, ordinaryMs=$ordinaryMs, segmentedIncludingMergeMs=$elapsed, SHA256=identical")
        }
    }

    private fun hash(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest()
    }
}
