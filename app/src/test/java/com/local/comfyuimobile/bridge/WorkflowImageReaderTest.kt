package com.local.comfyuimobile.bridge

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

class WorkflowImageReaderTest {
    @Test fun readsWorkflowFromComfyUiTextChunk() {
        val workflow = """{"nodes":[{"id":1,"type":"KSampler"}]}"""
        val metadata = "workflow\u0000$workflow".toByteArray(StandardCharsets.UTF_8)
        val png = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writeChunk("tEXt", metadata)
            writeChunk("IEND", byteArrayOf())
        }.toByteArray()

        assertEquals(workflow, WorkflowImageReader.readPngWorkflow(ByteArrayInputStream(png)))
    }

    @Test fun readsUtf8WorkflowFromInternationalTextChunk() {
        val workflow = """{"nodes":[{"id":1,"title":"中文节点"}]}"""
        val metadata = ByteArrayOutputStream().apply {
            write("workflow".toByteArray(StandardCharsets.ISO_8859_1))
            write(0)
            write(0) // 不压缩
            write(0)
            write(0) // 空语言标记
            write(0) // 空翻译关键字
            write(workflow.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        val png = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            writeChunk("iTXt", metadata)
            writeChunk("IEND", byteArrayOf())
        }.toByteArray()

        assertEquals(workflow, WorkflowImageReader.readPngWorkflow(ByteArrayInputStream(png)))
    }

    private fun ByteArrayOutputStream.writeChunk(type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }
        DataOutputStream(this).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
