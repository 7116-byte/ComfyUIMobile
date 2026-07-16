package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object ResultParser {
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "avif")
    private val videoExtensions = setOf("mp4", "webm", "mov", "mkv", "m4v")

    fun parse(baseUrl: String, history: JSONObject): List<ResultMedia> {
        val result = mutableListOf<ResultMedia>()
        history.keys().forEach { jobId ->
            val job = history.optJSONObject(jobId) ?: return@forEach
            val outputs = job.optJSONObject("outputs") ?: return@forEach
            outputs.keys().forEach { nodeId -> collect(baseUrl, jobId, nodeId, outputs.opt(nodeId), result) }
        }
        return result.distinctBy { "${it.jobId}/${it.type}/${it.subfolder}/${it.filename}" }
    }

    private fun collect(baseUrl: String, jobId: String, nodeId: String, value: Any?, out: MutableList<ResultMedia>) {
        when (value) {
            is JSONObject -> {
                val filename = value.optString("filename")
                if (filename.isNotBlank()) {
                    val ext = filename.substringAfterLast('.', "").lowercase()
                    val kind = when (ext) {
                        in imageExtensions -> MediaKind.IMAGE
                        in videoExtensions -> MediaKind.VIDEO
                        else -> null
                    }
                    if (kind != null) {
                        val subfolder = value.optString("subfolder")
                        val type = value.optString("type", "output")
                        val url = "$baseUrl/view?filename=${encode(filename)}&subfolder=${encode(subfolder)}&type=${encode(type)}"
                        out += ResultMedia(jobId, nodeId, filename, subfolder, type, kind, url)
                    }
                }
                value.keys().forEach { collect(baseUrl, jobId, nodeId, value.opt(it), out) }
            }
            is JSONArray -> repeat(value.length()) { collect(baseUrl, jobId, nodeId, value.opt(it), out) }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
