package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.DeviceStats
import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.SystemStats
import com.local.comfyuimobile.model.WorkflowEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import okio.source

data class QueueResponse(val promptId: String, val number: Int, val nodeErrors: JSONObject?)
data class UploadResponse(val name: String, val subfolder: String, val type: String)
class PromptSubmissionException(
    message: String,
    val nodeProblems: Map<String, List<String>>,
) : IllegalStateException(message)

class ComfyClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var baseUrl: String = ""
    @Volatile private var socket: WebSocket? = null

    fun setServer(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun serverUrl(): String = baseUrl

    suspend fun probe(url: String = baseUrl): Pair<SystemStats, ServerProfile> = withContext(Dispatchers.IO) {
        val normalized = LanAddress.normalize(url)
        val root = executeJson(Request.Builder().url("$normalized/system_stats").get().build())
        val stats = parseSystemStats(root)
        val host = normalized.removePrefix("http://").substringBefore(':')
        stats to ServerProfile(
            id = UUID.nameUUIDFromBytes(normalized.toByteArray()).toString(),
            name = host,
            baseUrl = normalized,
            lastSeen = System.currentTimeMillis(),
            comfyVersion = stats.comfyVersion,
        )
    }

    suspend fun systemStats(): SystemStats = withContext(Dispatchers.IO) {
        parseSystemStats(getJson("/system_stats"))
    }

    suspend fun features(): JSONObject = withContext(Dispatchers.IO) { getJson("/features") }

    suspend fun objectInfo(): JSONObject = withContext(Dispatchers.IO) { getJson("/object_info") }

    suspend fun listWorkflows(): List<WorkflowEntry> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/v2/userdata?path=${encode("workflows")}" 
        val array = executeArray(Request.Builder().url(url).get().build())
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                val type = item.optString("type")
                val path = item.optString("path")
                if (type == "directory" || path.endsWith(".json", ignoreCase = true)) {
                    add(
                        WorkflowEntry(
                            name = item.optString("name"),
                            path = path,
                            isDirectory = type == "directory",
                            size = item.optLong("size"),
                            modified = item.optDouble("modified"),
                        ),
                    )
                }
            }
        }.sortedWith(compareBy<WorkflowEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.path })
    }

    suspend fun readWorkflow(path: String): String = withContext(Dispatchers.IO) {
        executeText(Request.Builder().url(userdataUrl(path)).get().build())
    }

    suspend fun writeWorkflow(path: String, json: String, overwrite: Boolean): WorkflowEntry = withContext(Dispatchers.IO) {
        val url = userdataUrl(path) + "?overwrite=$overwrite&full_info=true"
        val request = Request.Builder().url(url).post(json.toRequestBody(jsonMedia)).build()
        val response = executeJson(request)
        WorkflowEntry(
            name = path.substringAfterLast('/'),
            path = response.optString("path", path),
            isDirectory = false,
            size = response.optLong("size", json.toByteArray().size.toLong()),
            modified = response.optDouble("modified", System.currentTimeMillis() / 1000.0),
        )
    }

    suspend fun moveWorkflow(source: String, destination: String): WorkflowEntry = withContext(Dispatchers.IO) {
        val url = userdataUrl(source) + "/move/" + UserdataPath.encode(destination) + "?overwrite=false&full_info=true"
        val response = executeJson(Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build())
        WorkflowEntry(
            name = destination.substringAfterLast('/'),
            path = response.optString("path", destination),
            isDirectory = false,
            size = response.optLong("size"),
            modified = response.optDouble("modified"),
        )
    }

    suspend fun deleteWorkflow(path: String) = withContext(Dispatchers.IO) {
        executeText(Request.Builder().url(userdataUrl(path)).delete().build(), allowedCodes = setOf(200, 204))
    }

    suspend fun queue(): List<JobSummary> = withContext(Dispatchers.IO) {
        val root = getJson("/queue")
        parseQueueArray(root.optJSONArray("queue_running"), JobState.RUNNING) +
            parseQueueArray(root.optJSONArray("queue_pending"), JobState.PENDING)
    }

    suspend fun history(maxItems: Int? = null): JSONObject = withContext(Dispatchers.IO) {
        getJson(if (maxItems == null) "/history" else "/history?max_items=$maxItems")
    }

    suspend fun history(promptId: String): JSONObject = withContext(Dispatchers.IO) {
        getJson("/history/${encode(promptId)}")
    }

    suspend fun historyJobs(maxItems: Int? = null): List<JobSummary> = withContext(Dispatchers.IO) {
        val history = history(maxItems)
        buildList {
            history.keys().forEach { id ->
                val item = history.optJSONObject(id) ?: return@forEach
                val status = item.optJSONObject("status")
                val statusString = status?.optString("status_str").orEmpty()
                val completed = status?.optBoolean("completed") == true
                val state = when {
                    statusString.equals("error", true) -> JobState.ERROR
                    completed -> JobState.SUCCESS
                    else -> JobState.UNKNOWN
                }
                add(JobSummary(id = id, state = state, message = statusString))
            }
        }
    }

    suspend fun queuePrompt(
        promptJson: String,
        workflowJson: String,
        clientId: String,
        workflowPath: String,
        workflowName: String,
    ): QueueResponse = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("client_id", clientId)
            .put("prompt", JSONObject(promptJson))
            .put(
                "extra_data",
                JSONObject()
                    .put("extra_pnginfo", JSONObject().put("workflow", JSONObject(workflowJson)))
                    .put(
                        "comfy_mobile",
                        JSONObject()
                            .put("workflow_path", workflowPath)
                            .put("workflow_name", workflowName),
                    ),
            )
        val request = Request.Builder().url("$baseUrl/prompt").post(body.toString().toRequestBody(jsonMedia)).build()
        client.newCall(request).execute().use { httpResponse ->
            val responseBody = httpResponse.body?.string().orEmpty()
            val response = runCatching { JSONObject(responseBody) }.getOrElse { JSONObject() }
            if (!httpResponse.isSuccessful) {
                val error = response.optJSONObject("error")
                val message = when (error?.optString("type")) {
                    "prompt_outputs_failed_validation" -> "部分部件参数校验失败，请查看标红的部件"
                    "prompt_no_outputs" -> "当前工作流没有可执行的输出节点"
                    "invalid_prompt" -> "生成参数格式无效"
                    else -> error?.optString("message").takeUnless { it.isNullOrBlank() }
                        ?.let { "服务器校验失败：$it" }
                        ?: "服务器拒绝了生成参数（HTTP ${httpResponse.code}）"
                }
                throw PromptSubmissionException(message, parseNodeProblems(response.optJSONObject("node_errors")))
            }
            QueueResponse(
                promptId = response.getString("prompt_id"),
                number = response.optInt("number"),
                nodeErrors = response.optJSONObject("node_errors"),
            )
        }
    }

    suspend fun upload(
        filename: String,
        mimeType: String?,
        contentLength: Long,
        inputStream: () -> InputStream,
        subfolder: String,
    ): UploadResponse = withContext(Dispatchers.IO) {
        val fileBody = object : RequestBody() {
            override fun contentType() = mimeType?.toMediaTypeOrNull()
            override fun contentLength(): Long = contentLength
            override fun writeTo(sink: okio.BufferedSink) {
                inputStream().use { source -> sink.writeAll(source.source()) }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", filename, fileBody)
            .addFormDataPart("type", "input")
            .addFormDataPart("subfolder", subfolder)
            .addFormDataPart("overwrite", "false")
            .build()
        val response = executeJson(Request.Builder().url("$baseUrl/upload/image").post(body).build())
        UploadResponse(response.getString("name"), response.optString("subfolder"), response.optString("type", "input"))
    }

    suspend fun cancel(job: JobSummary) = withContext(Dispatchers.IO) {
        if (job.state == JobState.PENDING) {
            val body = JSONObject().put("delete", JSONArray().put(job.id))
            executeText(Request.Builder().url("$baseUrl/queue").post(body.toString().toRequestBody(jsonMedia)).build())
        } else {
            val specific = Request.Builder().url("$baseUrl/api/jobs/${encode(job.id)}").delete().build()
            runCatching { executeText(specific) }.getOrElse {
                executeText(Request.Builder().url("$baseUrl/interrupt").post(ByteArray(0).toRequestBody()).build())
            }
        }
    }

    suspend fun clearPending() = withContext(Dispatchers.IO) {
        val body = JSONObject().put("clear", true)
        executeText(Request.Builder().url("$baseUrl/queue").post(body.toString().toRequestBody(jsonMedia)).build())
    }

    fun openWebSocket(clientId: String, onMessage: (JSONObject) -> Unit, onFailure: (Throwable) -> Unit, onOpen: () -> Unit) {
        closeWebSocket()
        val request = Request.Builder().url(baseUrl.replaceFirst("http://", "ws://") + "/ws?clientId=${encode(clientId)}").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { onMessage(JSONObject(text)) }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onFailure(t)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onFailure(IllegalStateException("WebSocket 已关闭：$code $reason"))
            }
        })
    }

    fun closeWebSocket() {
        socket?.close(1000, "switch server")
        socket = null
    }

    fun mediaUrl(filename: String, subfolder: String, type: String): String =
        "$baseUrl/view?filename=${encode(filename)}&subfolder=${encode(subfolder)}&type=${encode(type)}"

    suspend fun downloadTo(url: String, output: OutputStream) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败：HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("下载内容为空")
            output.use { target -> body.byteStream().use { source -> source.copyTo(target) } }
        }
    }

    private fun getJson(path: String): JSONObject = executeJson(Request.Builder().url(baseUrl + path).get().build())

    private fun executeJson(request: Request): JSONObject = JSONObject(executeText(request))
    private fun executeArray(request: Request): JSONArray = JSONArray(executeText(request))

    private fun executeText(request: Request, allowedCodes: Set<Int> = setOf(200)): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.code !in allowedCodes) {
                val message = runCatching {
                    val error = JSONObject(body).optJSONObject("error")
                    error?.optString("message").takeUnless { it.isNullOrBlank() } ?: body
                }.getOrDefault(body)
                throw IllegalStateException("HTTP ${response.code}: ${message.take(600)}")
            }
            return body
        }
    }

    private fun parseSystemStats(root: JSONObject): SystemStats {
        val system = root.optJSONObject("system") ?: JSONObject()
        val devicesJson = root.optJSONArray("devices") ?: JSONArray()
        val devices = buildList {
            repeat(devicesJson.length()) { index ->
                val item = devicesJson.getJSONObject(index)
                add(DeviceStats(item.optString("name"), item.optLong("vram_total"), item.optLong("vram_free")))
            }
        }
        return SystemStats(
            comfyVersion = system.optString("comfyui_version"),
            frontendVersion = system.optString("required_frontend_version"),
            devices = devices,
        )
    }

    private fun parseQueueArray(array: JSONArray?, state: JobState): List<JobSummary> {
        if (array == null) return emptyList()
        return buildList {
            repeat(array.length()) { index ->
                val item = array.optJSONArray(index) ?: return@repeat
                val id = item.optString(1)
                if (id.isNotBlank()) add(JobSummary(id = id, state = state))
            }
        }
    }

    private fun parseNodeProblems(nodeErrors: JSONObject?): Map<String, List<String>> {
        if (nodeErrors == null) return emptyMap()
        return buildMap {
            nodeErrors.keys().forEach { nodeId ->
                val errors = nodeErrors.optJSONObject(nodeId)?.optJSONArray("errors") ?: return@forEach
                val messages = buildList {
                    repeat(errors.length()) { index ->
                        val item = errors.optJSONObject(index) ?: return@repeat
                        val input = item.optJSONObject("extra_info")?.optString("input_name").orEmpty()
                        val message = when (item.optString("type")) {
                            "required_input_missing" -> "缺少必填输入"
                            "value_not_in_list" -> "所选值不在可用列表中"
                            "value_smaller_than_min" -> "数值低于允许的最小值"
                            "value_bigger_than_max" -> "数值超过允许的最大值"
                            "invalid_input_type" -> "输入类型不正确"
                            "prompt_no_outputs" -> "没有可执行的输出节点"
                            else -> item.optString("message").ifBlank { item.optString("details", "参数校验失败") }
                        }
                        add(if (input.isBlank()) message else "$input：$message")
                    }
                }
                if (messages.isNotEmpty()) put(nodeId, messages.distinct())
            }
        }
    }

    private fun userdataUrl(path: String): String = "$baseUrl/userdata/${UserdataPath.encode(path)}"
    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
