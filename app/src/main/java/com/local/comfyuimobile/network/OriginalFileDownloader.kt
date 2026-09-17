package com.local.comfyuimobile.network

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Downloads bytes, never decoded images. Only a fully validated file reaches the destination. */
internal class OriginalFileDownloader(
    private val client: OkHttpClient,
    private val temporaryRoot: File,
    private val parallelThreshold: Long = 8L * 1024 * 1024,
    private val targetPartSize: Long = 16L * 1024 * 1024,
) {
    init {
        require(parallelThreshold >= 2 && targetPartSize > 0)
    }

    suspend fun downloadTo(url: String, output: OutputStream) {
        // Also close a caller-owned stream when cancelled before the IO dispatcher starts.
        output.use { target ->
            staged(url) { file ->
                file.inputStream().use { copy(it, target, file.length(), currentCoroutineContext()) }
            }
        }
    }

    suspend fun downloadToFile(url: String, destination: File) = staged(url) { file ->
        currentCoroutineContext().ensureActive()
        val parent = destination.absoluteFile.parentFile!!
        if (!parent.isDirectory && !parent.mkdirs()) throw IOException("无法创建下载目录")
        // All app callers use internal storage (cacheDir/filesDir): same filesystem, atomic publish.
        // If an unsupported filesystem is supplied, fail rather than exposing a partial result.
        Files.move(file.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Unit
    }

    private suspend fun staged(url: String, consume: suspend (File) -> Unit) = withContext(Dispatchers.IO) {
        val directory = createWorkDirectory()
        try {
            val complete = File(directory, "complete")
            var alreadyComplete = false
            val metadata = try {
                request(requestBuilder(url).header("Range", "bytes=0-0").build()) { response, context ->
                    when (response.code) {
                        200 -> {
                            // A server ignoring Range has already sent the whole file: reuse it.
                            saveFull(response, complete, context)
                            alreadyComplete = true
                            null
                        }
                        206 -> {
                            identity(response)
                            val range = parseRange(response.header("Content-Range"))
                            if (range.start != 0L || range.end != 0L) throw RangeUnavailable("探测范围错误")
                            checkLength(response, 1)
                            val body = response.body ?: throw RangeUnavailable("探测内容为空")
                            copy(body.byteStream(), object : OutputStream() {
                                override fun write(value: Int) = Unit
                                override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
                            }, 1, context)
                            val tag = response.header("ETag")
                            // A weak ETag or second-resolution modification date cannot safely identify
                            // concurrent byte ranges. Such servers still work via a single full GET.
                            if (tag == null || !STRONG_ETAG.matches(tag)) null
                            else Metadata(range.total, tag, response.request.url.toString())
                        }
                        400, 405, 416, 501 -> null
                        else -> throw IOException("下载失败：HTTP ${response.code}")
                    }
                }
            } catch (_: RangeUnavailable) {
                null
            }
            if (!alreadyComplete) {
                var assembled = false
                if (metadata != null && metadata.total >= parallelThreshold) {
                    try {
                        val parts = downloadParts(metadata, directory)
                        merge(parts, complete, metadata.total)
                        assembled = true
                    } catch (error: IOException) {
                        // coroutineScope has stopped/joined every sibling before fallback/cleanup.
                        currentCoroutineContext().ensureActive()
                        directory.listFiles()?.forEach { it.delete() }
                    }
                }
                if (!assembled) request(requestBuilder(url).build()) { response, context ->
                    saveFull(response, complete, context)
                }
            }
            currentCoroutineContext().ensureActive()
            consume(complete)
        } finally {
            directory.deleteRecursively()
        }
    }

    private suspend fun downloadParts(metadata: Metadata, directory: File): List<Part> = coroutineScope {
        val count = (1 + (metadata.total - 1) / targetPartSize).coerceIn(2, 4).toInt()
        val size = metadata.total / count
        (0 until count).map { index ->
            async {
                val start = index * size
                val end = if (index == count - 1) metadata.total - 1 else start + size - 1
                val file = File(directory, "part-$index")
                request(
                    requestBuilder(metadata.url)
                        .header("Range", "bytes=$start-$end")
                        .header("If-Range", metadata.etag)
                        .build(),
                ) { response, context ->
                    if (response.code != 206) throw RangeUnavailable("服务器未返回分段：HTTP ${response.code}")
                    identity(response)
                    val range = parseRange(response.header("Content-Range"))
                    if (range != ByteRange(start, end, metadata.total)) throw RangeUnavailable("分段范围或总长度发生变化")
                    if (response.header("ETag") != metadata.etag || response.request.url.toString() != metadata.url) {
                        throw RangeUnavailable("下载文件版本发生变化")
                    }
                    checkLength(response, end - start + 1)
                    val body = response.body ?: throw RangeUnavailable("分段内容为空")
                    val hash = file.outputStream().buffered().use { output ->
                        copy(body.byteStream(), output, end - start + 1, context)
                    }
                    Part(file, range, hash)
                }
            }
        }.awaitAll()
    }

    private suspend fun merge(parts: List<Part>, destination: File, total: Long) {
        val context = currentCoroutineContext()
        var offset = 0L
        destination.outputStream().buffered().use { output ->
            for (part in parts) {
                if (part.range.start != offset || part.range.total != total) throw IOException("分段不连续")
                val hash = part.file.inputStream().use {
                    copy(it, output, part.range.end - part.range.start + 1, context)
                }
                if (!MessageDigest.isEqual(hash, part.sha256)) throw IOException("分段文件校验失败")
                offset = part.range.end + 1
                if (!part.file.delete()) throw IOException("无法清理下载分段")
            }
        }
        if (offset != total || destination.length() != total) throw IOException("合并文件长度不完整")
    }

    private fun saveFull(response: Response, destination: File, context: CoroutineContext) {
        if (response.code != 200 || response.header("Content-Range") != null) {
            throw IOException("完整下载失败：HTTP ${response.code}")
        }
        identity(response)
        val body = response.body ?: throw IOException("下载内容为空")
        destination.outputStream().buffered().use { output ->
            copy(body.byteStream(), output, body.contentLength().takeIf { it >= 0 }, context)
        }
    }

    /** Socket cancellation is attached for headers AND body, including blocking read stalls. */
    private suspend fun <T> request(request: Request, block: (Response, CoroutineContext) -> T): T = coroutineScope {
        val call = client.newCall(request)
        val cancelSocket = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            currentCoroutineContext().ensureActive()
            call.execute().use { response -> block(response, currentCoroutineContext()) }
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancelSocket.cancel()
        }
    }

    private fun copy(source: InputStream, target: OutputStream, expected: Long?, context: CoroutineContext): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        while (true) {
            context.ensureActive()
            val read = source.read(buffer)
            if (read == -1) break
            context.ensureActive()
            if (expected != null && read.toLong() > expected - received) throw IOException("下载内容超过声明长度")
            target.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            received += read
        }
        if (expected != null && received != expected) throw IOException("下载不完整：$received / $expected 字节")
        return digest.digest()
    }

    private fun requestBuilder(url: String) = Request.Builder().url(url)
        .header("Accept-Encoding", "identity")
        .header("Cache-Control", "no-cache, no-transform")
        .get()

    private fun identity(response: Response) {
        val encoding = response.header("Content-Encoding")
        if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
            throw RangeUnavailable("服务器返回了非原始字节编码：$encoding")
        }
    }

    private fun checkLength(response: Response, expected: Long) {
        val length = response.body?.contentLength() ?: -1
        if (length >= 0 && length != expected) throw RangeUnavailable("分段 Content-Length 不匹配")
    }

    private fun parseRange(value: String?): ByteRange {
        val fields = value?.let { CONTENT_RANGE.matchEntire(it)?.groupValues } ?: throw RangeUnavailable("缺少有效 Content-Range")
        val start = fields[1].toLongOrNull() ?: throw RangeUnavailable("无效范围起点")
        val end = fields[2].toLongOrNull() ?: throw RangeUnavailable("无效范围终点")
        val total = fields[3].toLongOrNull() ?: throw RangeUnavailable("未知文件大小")
        if (start > end || end >= total) throw RangeUnavailable("无效分段范围")
        return ByteRange(start, end, total)
    }

    private fun createWorkDirectory(): File = synchronized(initializedRoots) {
        val root = temporaryRoot.canonicalFile
        if (!root.isDirectory && !root.mkdirs()) throw IOException("无法创建下载临时目录")
        // The app and its service run in the same process. First use after a process death
        // removes orphan transfers; later clients never delete another active download.
        if (initializedRoots.add(root.path)) {
            root.listFiles()?.filter { it.isDirectory && it.name.startsWith("transfer-") }?.forEach { it.deleteRecursively() }
        }
        File(root, "transfer-${UUID.randomUUID()}").also {
            if (!it.mkdir()) throw IOException("无法创建下载临时文件")
        }
    }

    private data class ByteRange(val start: Long, val end: Long, val total: Long)
    private data class Metadata(val total: Long, val etag: String, val url: String)
    private data class Part(val file: File, val range: ByteRange, val sha256: ByteArray)
    private class RangeUnavailable(message: String) : IOException(message)

    companion object {
        private val CONTENT_RANGE = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)", RegexOption.IGNORE_CASE)
        private val STRONG_ETAG = Regex("\"[!#-~\\u0080-\\u00ff]*\"")
        private val initializedRoots = mutableSetOf<String>()
    }
}
