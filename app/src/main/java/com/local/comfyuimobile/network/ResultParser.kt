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
            val prompt = job.optJSONArray("prompt")
            val taskNumber = prompt?.optLong(0) ?: 0L
            val extraData = prompt?.optJSONObject(3)
            val mobile = extraData?.optJSONObject("comfy_mobile")
            val createdAt = extraData?.optLong("create_time")?.takeIf { it > 0 }
                ?: executionStart(job)
            val workflowPath = mobile?.optString("workflow_path").orEmpty()
            val workflowName = mobile?.optString("workflow_name").orEmpty()
            val outputs = job.optJSONObject("outputs") ?: return@forEach
            outputs.keys().forEach { nodeId ->
                collect(baseUrl, jobId, nodeId, outputs.opt(nodeId), createdAt, taskNumber, workflowPath, workflowName, result)
            }
        }
        return result
            .distinctBy { "${it.jobId}/${it.nodeId}/${it.type}/${it.subfolder}/${it.filename}" }
            .sortedWith(compareByDescending<ResultMedia> { it.createdAt }.thenByDescending { it.taskNumber })
    }

    private fun collect(
        baseUrl: String,
        jobId: String,
        nodeId: String,
        value: Any?,
        createdAt: Long,
        taskNumber: Long,
        workflowPath: String,
        workflowName: String,
        out: MutableList<ResultMedia>,
    ) {
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
                        out += ResultMedia(
                            jobId = jobId,
                            nodeId = nodeId,
                            filename = filename,
                            subfolder = subfolder,
                            type = type,
                            kind = kind,
                            url = url,
                            createdAt = createdAt,
                            taskNumber = taskNumber,
                            workflowPath = workflowPath,
                            workflowName = workflowName,
                        )
                    }
                }
                value.keys().forEach { collect(baseUrl, jobId, nodeId, value.opt(it), createdAt, taskNumber, workflowPath, workflowName, out) }
            }
            is JSONArray -> repeat(value.length()) {
                collect(baseUrl, jobId, nodeId, value.opt(it), createdAt, taskNumber, workflowPath, workflowName, out)
            }
        }
    }

    private fun executionStart(job: JSONObject): Long {
        val messages = job.optJSONObject("status")?.optJSONArray("messages") ?: return 0L
        repeat(messages.length()) { index ->
            val message = messages.optJSONArray(index) ?: return@repeat
            if (message.optString(0) == "execution_start") return message.optJSONObject(1)?.optLong("timestamp") ?: 0L
        }
        return 0L
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
