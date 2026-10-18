package com.local.comfyuimobile.bridge

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.InflaterInputStream
import java.util.zip.CRC32

data class ImageGenerationMetadata(
    val promptJson: String? = null,
    val workflowJson: String? = null,
    val parameters: String? = null,
)

object WorkflowImageReader {
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun readPngWorkflow(input: InputStream): String = readPng(input, workflowOnly = true).workflowJson
        ?: error("图片中没有可导入的 ComfyUI 工作流")

    fun readPngMetadata(input: InputStream): ImageGenerationMetadata = readPng(input, workflowOnly = false)

    private fun readPng(input: InputStream, workflowOnly: Boolean): ImageGenerationMetadata {
        val entries = mutableMapOf<String, String>()
        var textBytes = 0
        DataInputStream(input.buffered()).use { source ->
            val signature = ByteArray(pngSignature.size).also(source::readFully)
            require(signature.contentEquals(pngSignature)) { "所选文件不是有效的 PNG 图片" }
            while (true) {
                val length = try {
                    source.readInt()
                } catch (_: java.io.EOFException) {
                    break
                }
                require(length >= 0) { "PNG 数据块大小异常" }
                val typeBytes = ByteArray(4).also(source::readFully)
                val type = typeBytes.toString(StandardCharsets.US_ASCII)
                if (type !in setOf("tEXt", "zTXt", "iTXt")) {
                    var remaining = length
                    while (remaining > 0) {
                        val skipped = source.skipBytes(remaining)
                        if (skipped == 0) { source.readByte(); remaining-- } else remaining -= skipped
                    }
                    source.readInt()
                    if (type == "IEND") break
                    continue
                }
                require(length <= MAX_TEXT_BYTES && textBytes + length <= MAX_TOTAL_TEXT_BYTES) { "PNG 元数据过大" }
                textBytes += length
                val data = ByteArray(length).also(source::readFully)
                val expectedCrc = source.readInt().toLong() and 0xffffffffL
                require(CRC32().apply { update(typeBytes); update(data) }.value == expectedCrc) { "PNG 元数据校验失败" }
                val entry = when (type) {
                    "tEXt" -> readText(data)
                    "zTXt" -> readCompressedText(data)
                    "iTXt" -> readInternationalText(data)
                    else -> null
                }
                if (entry != null) {
                    entries[entry.first.lowercase()] = entry.second
                    if (workflowOnly && entry.first.equals("workflow", ignoreCase = true)) break
                    if (!workflowOnly && "workflow" in entries && "prompt" in entries) break
                }
            }
        }
        return ImageGenerationMetadata(entries["prompt"], entries["workflow"], entries["parameters"])
    }

    private fun readText(data: ByteArray): Pair<String, String>? {
        val separator = data.indexOf(0)
        if (separator <= 0) return null
        val key = data.copyOfRange(0, separator).toString(StandardCharsets.ISO_8859_1)
        if (key.lowercase() !in METADATA_KEYS) return null
        val value = data.copyOfRange(separator + 1, data.size).toString(StandardCharsets.UTF_8)
        return key to value
    }

    private fun readCompressedText(data: ByteArray): Pair<String, String>? {
        val separator = data.indexOf(0)
        if (separator <= 0 || separator + 2 > data.size) return null
        val key = data.copyOfRange(0, separator).toString(StandardCharsets.ISO_8859_1)
        if (key.lowercase() !in METADATA_KEYS) return null
        require(data[separator + 1].toInt() == 0) { "不支持的 PNG 元数据压缩格式" }
        val compressed = data.copyOfRange(separator + 2, data.size)
        return key to inflate(compressed).toString(StandardCharsets.UTF_8)
    }

    private fun readInternationalText(data: ByteArray): Pair<String, String>? {
        val keywordEnd = data.indexOf(0)
        if (keywordEnd <= 0 || keywordEnd + 3 > data.size) return null
        val key = data.copyOfRange(0, keywordEnd).toString(StandardCharsets.ISO_8859_1)
        if (key.lowercase() !in METADATA_KEYS) return null
        val compressed = data[keywordEnd + 1].toInt() == 1
        require(data[keywordEnd + 1].toInt() in 0..1 && data[keywordEnd + 2].toInt() == 0) { "不支持的 PNG 元数据压缩格式" }
        var cursor = keywordEnd + 3
        cursor = data.indexOf(0, cursor).takeIf { it >= 0 }?.plus(1) ?: return null
        cursor = data.indexOf(0, cursor).takeIf { it >= 0 }?.plus(1) ?: return null
        val text = data.copyOfRange(cursor, data.size)
        val decoded = if (compressed) inflate(text) else text
        return key to decoded.toString(StandardCharsets.UTF_8)
    }

    private fun inflate(data: ByteArray): ByteArray =
        InflaterInputStream(ByteArrayInputStream(data)).use { input ->
            val result = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(result.size() + count <= MAX_TEXT_BYTES) { "PNG 解压后的元数据过大" }
                result.write(buffer, 0, count)
            }
            result.toByteArray()
        }

    private fun ByteArray.indexOf(value: Int, start: Int = 0): Int {
        for (index in start until size) if (this[index].toInt() and 0xFF == value) return index
        return -1
    }

    private val METADATA_KEYS = setOf("prompt", "workflow", "parameters")
    private const val MAX_TEXT_BYTES = 16 * 1024 * 1024
    private const val MAX_TOTAL_TEXT_BYTES = 32 * 1024 * 1024
}
