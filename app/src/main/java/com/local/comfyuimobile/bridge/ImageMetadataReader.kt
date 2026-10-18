package com.local.comfyuimobile.bridge

import androidx.exifinterface.media.ExifInterface
import java.io.File

object ImageMetadataReader {
    fun read(file: File): ImageGenerationMetadata {
        val png = file.inputStream().use { input ->
            val signature = ByteArray(8)
            input.read(signature) == 8 && signature.contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a),
            )
        }
        if (png) return file.inputStream().use(WorkflowImageReader::readPngMetadata)
        val exif = ExifInterface(file)
        val values = listOf(
            ExifInterface.TAG_MODEL, ExifInterface.TAG_MAKE,
            ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
        ).mapNotNull(exif::getAttribute)
        var prompt: String? = null
        var workflow: String? = null
        var parameters: String? = null
        values.forEach { value ->
            val text = value.trim('\u0000', ' ', '\n', '\r')
            require(text.length <= 16 * 1024 * 1024) { "图片生成参数过大" }
            when {
                text.startsWith("prompt:") -> prompt = text.substringAfter(':')
                text.startsWith("workflow:") -> workflow = text.substringAfter(':')
                text.startsWith("parameters:") -> parameters = text.substringAfter(':')
            }
        }
        return ImageGenerationMetadata(prompt, workflow, parameters)
    }
}
