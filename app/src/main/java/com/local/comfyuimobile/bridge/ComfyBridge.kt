package com.local.comfyuimobile.bridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.local.comfyuimobile.data.AppLogger
import com.local.comfyuimobile.data.PromptGraphPolicy
import com.local.comfyuimobile.data.WorkflowPolicy
import com.local.comfyuimobile.model.GeneratedPrompt
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterSection
import com.local.comfyuimobile.model.WorkflowConnectionMarker
import com.local.comfyuimobile.model.WorkflowManifest
import com.local.comfyuimobile.model.WorkflowNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ComfyBridge(private val activity: Activity) {
    data class LinkRepairReport(
        val paintedBeforeRefresh: Int,
        val paintedAfterRefresh: Int,
        val paintedAfterRepair: Int,
        val linkPixelVisibleBefore: Int,
        val linkPixelVisibleAfter: Int,
        val linkPixelVisibleRepaired: Int,
        val repairMode: String,
    ) {
        val failed: Boolean
            get() = paintedAfterRepair >= 0 && paintedAfterRepair == 0
    }

    private data class PendingImageImport(val uri: Uri, val mimeType: String)
    private class PageTransitionException(message: String) : IllegalStateException(message)
    private class JavascriptContextUnavailableException(message: String) : IllegalStateException(message)

    var webView: WebView by mutableStateOf(WebView(activity))
        private set
    var onWebViewRecreated: ((WebView) -> Unit)? = null
    @Volatile private var allowedOrigin: String = ""
    @Volatile private var pageLoadError: String? = null
    @Volatile private var lastBridgePhase: String = "尚未执行前端脚本"
    @Volatile private var rendererEpoch: Int = 0
    @Volatile private var pageEpoch: Int = 0
    @Volatile private var finishedPageEpoch: Int = -1
    @Volatile var lastLinkRepairReport: LinkRepairReport? = null
        private set
    private val pendingImageImports = ConcurrentHashMap<String, PendingImageImport>()
    private val pendingEvaluations = ConcurrentHashMap<String, CancellableContinuation<String>>()

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        configureWebView(webView)
        logWebViewRuntime("初始化")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        target.settings.javaScriptEnabled = true
        target.settings.domStorageEnabled = true
        target.settings.databaseEnabled = true
        target.settings.mediaPlaybackRequiresUserGesture = false
        target.settings.allowFileAccess = false
        target.settings.allowContentAccess = false
        target.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageEpoch += 1
                finishedPageEpoch = -1
                pageLoadError = null
                AppLogger.info("ComfyUI 网页开始加载：轮次=$pageEpoch，地址=${url.orEmpty()}")
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                finishedPageEpoch = pageEpoch
                AppLogger.info(
                    "ComfyUI 网页完成加载：轮次=$pageEpoch，进度=${view.progress}%，" +
                        "已挂载=${view.isAttachedToWindow}，地址=${url.orEmpty()}",
                )
                super.onPageFinished(view, url)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val token = request.url.pathSegments
                    .takeIf { it.size == 2 && it[0] == IMAGE_IMPORT_PATH }
                    ?.get(1)
                    ?: return super.shouldInterceptRequest(view, request)
                val pending = pendingImageImports[token] ?: return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    404,
                    "导入内容已失效",
                    emptyMap(),
                    "导入内容已失效".byteInputStream(),
                )
                val stream = activity.contentResolver.openInputStream(pending.uri) ?: return WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    404,
                    "无法读取图片",
                    emptyMap(),
                    "无法读取图片".byteInputStream(),
                )
                return WebResourceResponse(pending.mimeType, null, stream)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url.toString()
                if (target.startsWith(allowedOrigin)) return false
                runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    pageLoadError = "网页加载失败（${error.errorCode}）：${error.description}"
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) {
                    pageLoadError = "网页返回错误 ${errorResponse.statusCode}：${errorResponse.reasonPhrase}"
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val message = "WebView 渲染进程退出：崩溃=${detail.didCrash()}，" +
                    "退出时优先级=${detail.rendererPriorityAtExit()}，最后阶段=$lastBridgePhase"
                rendererEpoch += 1
                pageLoadError = "ComfyUI 网页渲染进程崩溃，已自动重建前端桥接"
                AppLogger.error(message)
                val rendererError = rendererGoneException()
                pendingEvaluations.entries.toList().forEach { (id, continuation) ->
                    if (pendingEvaluations.remove(id, continuation) && continuation.isActive) {
                        continuation.resumeWithException(rendererError)
                    }
                }

                if (webView === view) {
                    val origin = allowedOrigin
                    view.destroy()
                    val replacement = WebView(activity)
                    configureWebView(replacement)
                    webView = replacement
                    onWebViewRecreated?.invoke(replacement)
                    logWebViewRuntime("崩溃后重建")
                    if (origin.isNotBlank()) replacement.loadUrl("$origin/")
                }
                // 返回 true 表示已处理；返回 false 会让 Android 连带杀死整个 App。
                return true
            }
        }
    }

    suspend fun loadServer(baseUrl: String, timeoutMillis: Long = 45_000L) {
        val origin = baseUrl.trimEnd('/')
        withContext(Dispatchers.Main.immediate) {
            allowedOrigin = origin
            pageLoadError = null
            webView.onResume()
            webView.resumeTimers()
            if (webView.url.orEmpty().startsWith(origin)) webView.reload() else webView.loadUrl("$origin/")
        }

        val deadline = System.currentTimeMillis() + timeoutMillis
        var currentUrl = "about:blank"
        var progress = 0
        var attached = false
        while (System.currentTimeMillis() < deadline) {
            pageLoadError?.let { throw IllegalStateException(it) }
            val pageState = withContext(Dispatchers.Main.immediate) {
                Triple(webView.url.orEmpty(), webView.progress, webView.isAttachedToWindow)
            }
            currentUrl = pageState.first.ifBlank { "about:blank" }
            progress = pageState.second
            attached = pageState.third
            if (
                isPageReadyForScripts(
                    currentUrl = currentUrl,
                    allowedOrigin = origin,
                    progress = progress,
                    pageEpoch = pageEpoch,
                    finishedPageEpoch = finishedPageEpoch,
                    attached = attached,
                )
            ) return
            delay(100)
        }
        throw IllegalStateException("网页加载超时：当前地址 $currentUrl，进度 $progress%，网页已挂载=$attached")
    }

    suspend fun awaitReady(timeoutMillis: Long = 90_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError = "ComfyUI 前端尚未初始化"
        while (System.currentTimeMillis() < deadline) {
            val remaining = deadline - System.currentTimeMillis()
            val response = runCatching {
                evaluate(READY_SCRIPT, timeoutMillis = remaining.coerceIn(1_000L, 5_000L))
            }.getOrElse {
                lastError = it.message.takeUnless(String?::isNullOrBlank) ?: lastError
                ""
            }
            val json = runCatching { JSONObject(response) }.getOrNull()
            if (json?.optBoolean("ok") == true) return
            lastError = json?.optString("error").takeUnless { it.isNullOrBlank() } ?: lastError
            delay(500)
        }
        throw IllegalStateException("前端桥接超时：$lastError")
    }

    suspend fun refreshVisibleViewport() = withContext(Dispatchers.Main.immediate) {
        webView.onResume()
        webView.requestLayout()
        webView.invalidate()
        webView.evaluateJavascript(
            "window.dispatchEvent(new Event('resize'));" +
                "window.__comfyMobileApp?.canvas?.resize?.();" +
                "window.__comfyMobileApp?.canvas?.draw?.(true, true);",
            null,
        )
    }

    suspend fun awaitVisibleViewport(timeoutMillis: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var width = 0
        var height = 0
        while (System.currentTimeMillis() < deadline) {
            val viewport = withContext(Dispatchers.Main.immediate) {
                Triple(webView.width, webView.height, webView.isShown)
            }
            width = viewport.first
            height = viewport.second
            if (width > 0 && height > 0 && viewport.third) return
            delay(50)
        }
        throw IllegalStateException("高级编辑画布尚未显示：${width}×${height}")
    }

    suspend fun loadWorkflow(
        rawJson: String,
        workflowPath: String? = null,
        nativeWorkflowOpen: Boolean = false,
    ): WorkflowManifest {
        val encoded = Base64.getEncoder().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val encodedPath = frontendWorkflowStorePath(workflowPath)
            ?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }
        val script = workflowManifestScript(encoded, encodedPath, nativeWorkflowOpen)
        var lastError: Throwable? = null
        var response: String? = null
        repeat(3) { attempt ->
            if (response != null) return@repeat
            response = try {
                awaitReady()
                evaluate(script)
            } catch (error: PageTransitionException) {
                lastError = error
                AppLogger.info("工作流加载时 ComfyUI 页面发生切换，正在重试 ${attempt + 1}/3")
                delay(500)
                null
            } catch (error: JavascriptContextUnavailableException) {
                lastError = error
                if (attempt < 2) {
                    AppLogger.info("ComfyUI 页面已加载但脚本无响应，正在自动重载前端 ${attempt + 1}/2")
                    runCatching { reloadFrontend() }
                        .onFailure { reloadError ->
                            lastError = reloadError
                            AppLogger.error("ComfyUI 前端自动重载失败", reloadError)
                        }
                }
                null
            }
        }
        val resolved = response ?: throw (
            lastError ?: IllegalStateException("ComfyUI 页面尚未稳定，无法加载工作流")
        )
        return parseWorkflowManifest(rawJson, resolved)
    }

    private suspend fun reloadFrontend() {
        val origin = allowedOrigin.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("尚未连接 ComfyUI 服务器")
        loadServer(origin, timeoutMillis = 45_000L)
        awaitReady(timeoutMillis = 60_000L)
    }

    suspend fun snapshotCurrentWorkflow(): Pair<String, WorkflowManifest> {
        awaitReady()
        val rawJson = exportCurrentWorkflow()
        val encoded = Base64.getEncoder().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val response = evaluate(
            workflowManifestScript(
                encodedWorkflow = encoded,
                encodedWorkflowPath = null,
                nativeWorkflowOpen = false,
                inspectCurrentGraph = true,
            ),
        )
        return rawJson to parseWorkflowManifest(rawJson, response)
    }

    private fun parseWorkflowManifest(rawJson: String, response: String): WorkflowManifest {
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "工作流解析失败"))
        root.optJSONObject("diagnostics")
            ?.takeIf { it.optBoolean("nativeWorkflowOpen") }
            ?.let { diagnostics ->
                val canvasMode = diagnostics.opt("canvasLinkMode")
                    .takeUnless { it == null || it == JSONObject.NULL }
                    ?.toString()
                    ?: "未知"
                AppLogger.info(
                    "高级编辑原生打开诊断：源工作流=${diagnostics.optInt("sourceLinkCount")} 条，" +
                        "画布已载入=${diagnostics.optInt("loadedLinkCount")} 条，" +
                        "当前已绘制=${diagnostics.optInt("renderedPathCount")} 条，" +
                        "画布模式=$canvasMode，Vue 节点=${diagnostics.optBoolean("vueNodesMode")}，" +
                        "重绘前=${diagnostics.optInt("paintedBeforeRefresh", -1)} 条，" +
                        "重绘后=${diagnostics.optInt("paintedAfterRefresh", -1)} 条，" +
                        "修复后=${diagnostics.optInt("paintedAfterRepair", -1)} 条，" +
                        "修复方式=${diagnostics.optString("linkRepairMode", "none")}，" +
                        "像素可见=${diagnostics.optInt("linkPixelVisibleBefore", -1)}/" +
                        "${diagnostics.optInt("linkPixelVisibleAfter", -1)}/" +
                        "${diagnostics.optInt("linkPixelVisibleRepaired", -1)}",
                )
                lastLinkRepairReport = LinkRepairReport(
                    paintedBeforeRefresh = diagnostics.optInt("paintedBeforeRefresh", -1),
                    paintedAfterRefresh = diagnostics.optInt("paintedAfterRefresh", -1),
                    paintedAfterRepair = diagnostics.optInt("paintedAfterRepair", -1),
                    linkPixelVisibleBefore = diagnostics.optInt("linkPixelVisibleBefore", -1),
                    linkPixelVisibleAfter = diagnostics.optInt("linkPixelVisibleAfter", -1),
                    linkPixelVisibleRepaired = diagnostics.optInt("linkPixelVisibleRepaired", -1),
                    repairMode = diagnostics.optString("linkRepairMode", "none"),
                )
            }
        val layout = parseLayout(rawJson)
        val fieldsJson = root.optJSONArray("fields") ?: JSONArray()
        val fields = buildList {
            repeat(fieldsJson.length()) { index ->
                val item = fieldsJson.getJSONObject(index)
                val key = item.getString("key")
                val optionsJson = item.optJSONArray("values") ?: JSONArray()
                val options = List(optionsJson.length()) { optionsJson.optString(it) }
                val value = item.opt("value")
                val nodeType = item.optString("nodeType")
                val name = item.optString("name")
                val widgetType = item.optString("widgetType")
                val minimum = item.optNullableDouble("min")
                val maximum = item.optNullableDouble("max")
                val step = item.optNullableDouble("step")
                val kind = ParameterClassifier.kind(
                    nodeType = nodeType,
                    name = name,
                    widgetType = widgetType,
                    value = value,
                    options = options,
                    dataType = item.optString("dataType"),
                    minimum = minimum,
                    maximum = maximum,
                    step = step,
                    precision = item.optInt("precision", -1).takeIf { it >= 0 },
                )
                val stored = layout.optJSONObject(key)
                val label = stored?.optString("label").takeUnless { it.isNullOrBlank() }
                    ?: ParameterClassifier.label(item.optString("nodeTitle"), name, item.optString("label", name))
                val section = when (stored?.optString("section")) {
                    "primary" -> ParameterSection.PRIMARY
                    "more" -> ParameterSection.MORE
                    else -> if (item.optBoolean("refreshesWorkflow")) {
                        ParameterSection.PRIMARY
                    } else {
                        ParameterClassifier.section(nodeType, name, kind)
                    }
                }
                val valueJson = when (value) {
                    null, JSONObject.NULL -> "null"
                    is String -> JSONObject.quote(value)
                    else -> value.toString()
                }
                add(
                    ParameterField(
                        key = key,
                        nodeId = item.optString("nodeId"),
                        nodeTitle = item.optString("nodeTitle"),
                        nodeType = nodeType,
                        name = name,
                        label = label,
                        widgetType = widgetType,
                        kind = kind,
                        valueJson = valueJson,
                        displayValue = when (value) {
                            null, JSONObject.NULL -> ""
                            else -> value.toString()
                        },
                        options = options,
                        minimum = minimum,
                        maximum = maximum,
                        step = step,
                        linked = item.optBoolean("linked"),
                        visible = stored?.optBoolean("visible", true) ?: true,
                        section = section,
                        order = stored?.optInt("order", index) ?: index,
                        warning = if (kind.name == "UNSUPPORTED") "此控件需在高级编辑中修改" else null,
                        widgetIndex = item.optInt("widgetIndex", -1),
                        refreshesWorkflow = item.optBoolean("refreshesWorkflow"),
                        nodeOrder = item.optInt("nodeOrder"),
                    ),
                )
            }
        }.sortedWith(compareBy<ParameterField> { it.nodeOrder }.thenBy { it.order })
        val nodesJson = root.optJSONArray("nodes") ?: JSONArray()
        val nodes = buildList {
            repeat(nodesJson.length()) { index ->
                val item = nodesJson.getJSONObject(index)
                add(
                    WorkflowNode(
                        id = item.getString("id"),
                        title = item.optString("title").ifBlank { item.optString("type", "未命名节点") },
                        type = item.optString("type"),
                        order = item.optInt("order", index),
                        isController = item.optBoolean("isController"),
                        isOutput = item.optBoolean("isOutput"),
                        inputMarkers = parseConnectionMarkers(item.optJSONArray("inputMarkers")),
                        outputMarkers = parseConnectionMarkers(item.optJSONArray("outputMarkers")),
                    ),
                )
            }
        }.sortedBy { it.order }
        return WorkflowManifest(fields, nodes)
    }

    private fun parseConnectionMarkers(array: JSONArray?): List<WorkflowConnectionMarker> {
        if (array == null) return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            WorkflowConnectionMarker(
                label = item.optString("label"),
                type = item.optString("type"),
                color = item.optString("color"),
                portName = item.optString("portName"),
            )
        }
    }

    suspend fun extractWorkflowFromImage(uri: Uri, mimeType: String?, filename: String): String {
        awaitReady()
        val declaredMime = mimeType.orEmpty().substringBefore(';').trim().lowercase()
        val extensionMime = when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }
        val normalizedMime = declaredMime.takeIf { it in SUPPORTED_WORKFLOW_IMAGE_TYPES } ?: extensionMime
        require(normalizedMime in SUPPORTED_WORKFLOW_IMAGE_TYPES) {
            "仅支持包含 ComfyUI 工作流的 PNG、WebP 或 AVIF 图片"
        }
        val token = UUID.randomUUID().toString()
        pendingImageImports[token] = PendingImageImport(uri, normalizedMime)
        return try {
            val encodedName = Base64.getEncoder().encodeToString(filename.toByteArray(Charsets.UTF_8))
            val response = evaluate(imageWorkflowScript(token, encodedName, normalizedMime))
            val root = JSONObject(response)
            if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "图片工作流解析失败"))
            root.getJSONObject("workflow").toString()
        } finally {
            pendingImageImports.remove(token)
        }
    }

    suspend fun buildPrompt(fields: List<ParameterField>): GeneratedPrompt {
        awaitReady()
        lastBridgePhase = "准备参数，共 ${fields.size} 项"
        AppLogger.info("前端桥接：$lastBridgePhase")
        val changedFields = fields.filter { it.valueJson != it.originalValueJson }
        AppLogger.info("前端桥接：Android 已比较参数，仅应用实际修改，共 ${changedFields.size} 项")

        for (field in changedFields) {
            lastBridgePhase = "应用参数：${field.nodeTitle}/${field.label}（${field.key}）"
            AppLogger.info("前端桥接阶段：$lastBridgePhase")
            val singleUpdate = JSONArray().put(
                JSONObject()
                    .put("key", field.key)
                    .put("widgetIndex", field.widgetIndex)
                    .put("value", parseJsonValue(field.valueJson)),
            )
            val singleEncoded = Base64.getEncoder().encodeToString(
                singleUpdate.toString().toByteArray(Charsets.UTF_8),
            )
            val applyResponse = withContext(Dispatchers.Main.immediate) {
                evaluateImmediate(promptApplyScript(singleEncoded))
            }
            val applyRoot = JSONObject(applyResponse)
            if (!applyRoot.optBoolean("ok")) {
                throw IllegalStateException(applyRoot.optString("error", "应用手机参数失败"))
            }
        }

        lastBridgePhase = "执行排队前回调"
        AppLogger.info("前端桥接阶段：$lastBridgePhase")
        val callbackResponse = withContext(Dispatchers.Main.immediate) { evaluateImmediate(PROMPT_BEFORE_QUEUE_SCRIPT) }
        val callbackRoot = JSONObject(callbackResponse)
        if (!callbackRoot.optBoolean("ok")) {
            throw IllegalStateException(callbackRoot.optString("error", "执行排队前回调失败"))
        }

        lastBridgePhase = "调用官方 graphToPrompt"
        AppLogger.info("前端桥接阶段：$lastBridgePhase")
        val response = evaluate(PROMPT_CONVERT_SCRIPT)
        lastBridgePhase = "Prompt 已返回 Android，响应=${response.length} 字符"
        AppLogger.info("前端桥接：$lastBridgePhase")
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "生成 Prompt 失败"))
        val prompt = root.getJSONObject("prompt")
        val relevantNodeIds = buildSet {
            val values = root.optJSONArray("relevantNodeIds") ?: JSONArray()
            repeat(values.length()) { index -> add(values.optString(index)) }
        }
        val filterResult = PromptGraphPolicy.retainExecutableAncestors(prompt, relevantNodeIds)
        AppLogger.info(
            "Prompt 执行链整理：官方节点=${filterResult.originalCount}，" +
                "根节点命中=${filterResult.matchedRootCount}，保留节点=${filterResult.retainedCount}，" +
                "完整保留=${filterResult.keptFullPrompt}",
        )
        if (prompt.length() == 0) throw IllegalStateException("当前工作流没有可执行的输出链")
        val workflow = root.getJSONObject("workflow")
        WorkflowPolicy.writeMobileLayout(workflow, fields)
        return GeneratedPrompt(prompt.toString(), workflow.toString())
    }

    suspend fun syncWorkflow(fields: List<ParameterField>): String {
        awaitReady()
        val updates = JSONArray().apply {
            fields.forEach { field ->
                put(
                    JSONObject()
                        .put("key", field.key)
                        .put("widgetIndex", field.widgetIndex)
                        .put("value", parseJsonValue(field.valueJson)),
                )
            }
        }
        val encoded = Base64.getEncoder().encodeToString(updates.toString().toByteArray(Charsets.UTF_8))
        val response = evaluate(syncWorkflowScript(encoded))
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "工作流参数同步失败"))
        val workflow = root.getJSONObject("workflow")
        WorkflowPolicy.writeMobileLayout(workflow, fields)
        return workflow.toString()
    }

    suspend fun exportCurrentWorkflow(): String {
        awaitReady()
        val response = evaluate(EXPORT_SCRIPT)
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "高级编辑同步失败"))
        return root.getJSONObject("workflow").toString()
    }

    suspend fun invokeWidgetButton(nodeId: String, actionToken: String): String {
        awaitReady()
        val payload = JSONObject().put("nodeId", nodeId).put("actionToken", actionToken)
        val encoded = Base64.getEncoder().encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
        val response = evaluate(widgetButtonScript(encoded))
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "网页按钮操作失败"))
        return root.getJSONObject("workflow").toString()
    }

    fun destroy() {
        onWebViewRecreated = null
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun evaluate(script: String, timeoutMillis: Long = 120_000L): String = withContext(Dispatchers.Main.immediate) {
        webView.onResume()
        webView.resumeTimers()
        val expectedRendererEpoch = rendererEpoch
        val expectedPageEpoch = pageEpoch
        val token = UUID.randomUUID().toString()
        val quotedToken = JSONObject.quote(token)
        val kickoff = """
            (() => {
              window.__comfyMobileResults = window.__comfyMobileResults || Object.create(null);
              window.__comfyMobileRunning = window.__comfyMobileRunning || Object.create(null);
              window.__comfyMobileCurrentPhase = '';
              if (window.__comfyMobileResults[$quotedToken]) return 'started';
              if (window.__comfyMobileRunning[$quotedToken]) return 'started';
              window.__comfyMobileRunning[$quotedToken] = true;
              setTimeout(() => {
                (async () => {
                  try {
                    const value = await ($script);
                    window.__comfyMobileResults[$quotedToken] = {
                      value: typeof value === 'string' ? value : JSON.stringify(value ?? null)
                    };
                  } catch (error) {
                    window.__comfyMobileResults[$quotedToken] = {
                      error: String(error?.stack || error)
                    };
                  } finally {
                    delete window.__comfyMobileRunning[$quotedToken];
                  }
                })();
              }, 250);
              return 'started';
            })()
        """.trimIndent()
        val kickoffDeadline = System.currentTimeMillis() + timeoutMillis.coerceAtMost(10_000L)
        var started = ""
        var currentUrl = webView.url.orEmpty().ifBlank { "about:blank" }
        var currentProgress = webView.progress
        var currentAttached = webView.isAttachedToWindow
        var currentFinished = finishedPageEpoch == pageEpoch
        while (System.currentTimeMillis() < kickoffDeadline && started != "started") {
            if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
            if (pageEpoch != expectedPageEpoch) {
                throw PageTransitionException("ComfyUI 页面在脚本启动前发生了切换")
            }
            currentUrl = webView.url.orEmpty().ifBlank { "about:blank" }
            currentProgress = webView.progress
            currentAttached = webView.isAttachedToWindow
            currentFinished = finishedPageEpoch == expectedPageEpoch
            if (
                isPageReadyForScripts(
                    currentUrl = currentUrl,
                    allowedOrigin = allowedOrigin,
                    progress = currentProgress,
                    pageEpoch = expectedPageEpoch,
                    finishedPageEpoch = finishedPageEpoch,
                    attached = currentAttached,
                )
            ) {
                started = try {
                    evaluateImmediate(kickoff)
                } catch (throwable: Throwable) {
                    if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
                    if (pageEpoch != expectedPageEpoch) {
                        throw PageTransitionException("ComfyUI 页面在脚本启动时发生了切换")
                    }
                    throw throwable
                }
            }
            if (started != "started") delay(100)
        }
        if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
        if (pageEpoch != expectedPageEpoch) {
            throw PageTransitionException("ComfyUI 页面在脚本启动后发生了切换")
        }
        if (started != "started") {
            val kickoffState = runCatching {
                evaluateImmediate(
                    """
                    (() => {
                      if (window.__comfyMobileResults?.[$quotedToken]) return 'completed';
                      if (window.__comfyMobileRunning?.[$quotedToken]) return 'running';
                      return 'missing';
                    })()
                    """.trimIndent(),
                )
            }.getOrDefault("")
            if (kickoffState == "running" || kickoffState == "completed") {
                started = "started"
            } else if (kickoffState == "missing") {
                throw IllegalStateException(
                    "前端桥接脚本没有启动，脚本存在语法错误或当前 WebView 不支持其中的语法" +
                        "（长度=${script.length}，校验=${script.hashCode().toUInt().toString(16)}）",
                )
            }
        }
        if (started != "started") {
            throw JavascriptContextUnavailableException(
                "ComfyUI 页面脚本没有响应：地址 $currentUrl，加载进度 $currentProgress%，" +
                    "页面完成=$currentFinished，网页已挂载=$currentAttached",
            )
        }

        val poll = """
            (() => {
              const store = window.__comfyMobileResults;
              const result = store?.[$quotedToken];
              const phase = String(window.__comfyMobileCurrentPhase || '');
              if (!result && !phase) return '';
              if (!result) return JSON.stringify({phase});
              delete store[$quotedToken];
              return JSON.stringify({phase, result});
            })()
        """.trimIndent()
        val cleanup = "delete window.__comfyMobileResults?.[$quotedToken]; delete window.__comfyMobileRunning?.[$quotedToken]"
        val deadline = System.currentTimeMillis() + timeoutMillis
        var reportedPhase = ""
        try {
            while (System.currentTimeMillis() < deadline) {
                if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
                if (pageEpoch != expectedPageEpoch) {
                    throw PageTransitionException("ComfyUI 页面在前端操作期间发生了切换")
                }
                val raw = try {
                    evaluateImmediate(poll)
                } catch (throwable: Throwable) {
                    if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
                    if (pageEpoch != expectedPageEpoch) {
                        throw PageTransitionException("ComfyUI 页面在读取前端结果时发生了切换")
                    }
                    throw throwable
                }
                if (raw.isNotEmpty()) {
                    val envelope = JSONObject(raw)
                    val phase = envelope.optString("phase")
                    if (phase.isNotBlank() && phase != reportedPhase) {
                        reportedPhase = phase
                        lastBridgePhase = phase
                        AppLogger.info("前端桥接阶段：$phase")
                    }
                    val result = envelope.optJSONObject("result")
                    if (result == null) {
                        delay(50)
                        continue
                    }
                    val error = result.optString("error")
                    if (error.isNotBlank()) throw IllegalStateException(error)
                    return@withContext result.optString("value")
                }
                delay(50)
            }
            throw IllegalStateException("前端脚本执行超时")
        } finally {
            runCatching { webView.evaluateJavascript(cleanup, null) }
        }
    }

    private suspend fun evaluateImmediate(script: String): String =
        withTimeout(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val evaluationId = UUID.randomUUID().toString()
                val target = webView
                pendingEvaluations[evaluationId] = continuation
                continuation.invokeOnCancellation { pendingEvaluations.remove(evaluationId, continuation) }
                runCatching {
                    target.evaluateJavascript(script) { encodedResult ->
                        if (!pendingEvaluations.remove(evaluationId, continuation) || !continuation.isActive) {
                            return@evaluateJavascript
                        }
                        runCatching { decodeJavascriptResult(encodedResult) }
                            .onSuccess(continuation::resume)
                            .onFailure(continuation::resumeWithException)
                    }
                }.onFailure { throwable ->
                    if (pendingEvaluations.remove(evaluationId, continuation) && continuation.isActive) {
                        continuation.resumeWithException(throwable)
                    }
                }
            }
        }

    private fun rendererGoneException() = IllegalStateException(
        "ComfyUI 网页渲染进程崩溃，前端桥接已自动重建。请等待连接恢复后重新生成。最后阶段：$lastBridgePhase",
    )

    private fun logWebViewRuntime(action: String) {
        val packageInfo = WebView.getCurrentWebViewPackage()
        AppLogger.info(
            "WebView 运行时（$action）：包=${packageInfo?.packageName.orEmpty()}，" +
                "版本=${packageInfo?.versionName.orEmpty()}",
        )
    }

    private fun decodeJavascriptResult(value: String): String {
        if (value == "null" || value.isBlank()) return "{}"
        return JSONArray("[$value]").getString(0)
    }

    private fun parseLayout(rawJson: String): JSONObject = runCatching {
        JSONObject(rawJson).optJSONObject("extra")
            ?.optJSONObject("comfyMobile")
            ?.optJSONObject("fields") ?: JSONObject()
    }.getOrDefault(JSONObject())

    private fun parseJsonValue(raw: String): Any = runCatching {
        JSONObject("{\"v\":$raw}").get("v")
    }.getOrElse { raw }

    private fun JSONObject.optNullableDouble(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

    private fun imageWorkflowScript(token: String, encodedFilename: String, mimeType: String) = """
        (async () => {
          try {
            const api = window.comfyAPI?.pnginfo;
            if (!api) return JSON.stringify({ok:false, error:'ComfyUI 图片工作流解析器尚未就绪'});
            const response = await fetch('/$IMAGE_IMPORT_PATH/$token', {cache:'no-store'});
            if (!response.ok) return JSON.stringify({ok:false, error:'无法读取所选图片'});
            const filename = new TextDecoder().decode(Uint8Array.from(atob('$encodedFilename'), c => c.charCodeAt(0)));
            const file = new File([await response.blob()], filename, {type:'$mimeType'});
            let metadata;
            if ('$mimeType' === 'image/png') metadata = await api.getPngMetadata(file);
            else if ('$mimeType' === 'image/webp') metadata = await api.getWebpMetadata(file);
            else if ('$mimeType' === 'image/avif') metadata = await api.getAvifMetadata(file);
            const raw = metadata?.workflow ?? metadata?.Workflow;
            if (!raw) return JSON.stringify({ok:false, error:'图片中没有可导入的 ComfyUI 工作流'});
            const workflow = typeof raw === 'string' ? JSON.parse(raw) : raw;
            if (!Array.isArray(workflow?.nodes)) {
              return JSON.stringify({ok:false, error:'图片中的数据不是 ComfyUI 画布工作流'});
            }
            return JSON.stringify({ok:true, workflow});
          } catch (error) {
            return JSON.stringify({ok:false, error:'图片工作流解析错误：' + String(error?.message || error)});
          }
        })()
    """.trimIndent()

    private fun workflowManifestScript(
        encodedWorkflow: String,
        encodedWorkflowPath: String?,
        nativeWorkflowOpen: Boolean,
        inspectCurrentGraph: Boolean = false,
    ) = """
        (async () => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
            const text = new TextDecoder().decode(Uint8Array.from(atob('$encodedWorkflow'), c => c.charCodeAt(0)));
            const workflow = JSON.parse(text);
            let sourceWorkflow = workflow;
            const cloneValue = (value) => {
              if (value == null || typeof value !== 'object') return value;
              try { return structuredClone(value); }
              catch (_) { return JSON.parse(JSON.stringify(value)); }
            };
            const serverWorkflowPath = ${encodedWorkflowPath?.let { "new TextDecoder().decode(Uint8Array.from(atob('$it'), c => c.charCodeAt(0)))" } ?: "''"};
            if (!$inspectCurrentGraph) {
              if (serverWorkflowPath) {
              // Follow the same path as ComfyUI's workflow sidebar: resolve the
              // persisted ComfyWorkflow first and pass that object to loadGraphData.
              const workflowStore = app.extensionManager?.workflow;
              if (!workflowStore?.getWorkflowByPath || !workflowStore?.syncWorkflows) {
                return JSON.stringify({ok:false, error:'当前 ComfyUI 前端未提供官方工作流打开接口'});
              }
              await workflowStore.syncWorkflows();
              const persistedWorkflow = workflowStore.getWorkflowByPath(serverWorkflowPath);
              if (!persistedWorkflow) {
                return JSON.stringify({ok:false, error:'服务器工作流列表中找不到：' + serverWorkflowPath});
              }
              const alreadyActive = typeof workflowStore.isActive === 'function'
                ? workflowStore.isActive(persistedWorkflow)
                : workflowStore.activeWorkflow?.path === serverWorkflowPath;
              const loadFromRemote = !persistedWorkflow.isLoaded;
              if ($nativeWorkflowOpen) {
                // Match ComfyUI 1.45.21's workflow sidebar/openWorkflow service:
                // keep the restored active tab untouched; otherwise load the
                // ComfyWorkflow's own activeState with the native argument order.
                if (!alreadyActive) {
                  if (loadFromRemote) await persistedWorkflow.load();
                  if (!persistedWorkflow.activeState) {
                    return JSON.stringify({ok:false, error:'服务器工作流内容尚未加载：' + serverWorkflowPath});
                  }
                  sourceWorkflow = cloneValue(persistedWorkflow.activeState);
                  await app.loadGraphData(
                    sourceWorkflow,
                    true,
                    true,
                    persistedWorkflow,
                    {
                      checkForRerouteMigration:false,
                      deferWarnings:true,
                      skipAssetScans:!loadFromRemote
                    }
                  );
                }
              } else {
                if (loadFromRemote) await persistedWorkflow.load();
                if (!persistedWorkflow.activeState) {
                  return JSON.stringify({ok:false, error:'服务器工作流内容尚未加载：' + serverWorkflowPath});
                }
                // The parameter bridge keeps the App's current working copy.
                await app.loadGraphData(
                  workflow,
                  true,
                  true,
                  persistedWorkflow,
                  {
                    checkForRerouteMigration:false,
                    deferWarnings:true,
                    skipAssetScans:!loadFromRemote
                  }
                );
              }
              await new Promise(resolve => setTimeout(resolve, 250));
              const activeWorkflowPath = workflowStore.activeWorkflow?.path || '';
              if (activeWorkflowPath !== serverWorkflowPath) {
                return JSON.stringify({
                  ok:false,
                  error:'ComfyUI 打开的工作流标签不匹配：期望 ' + serverWorkflowPath +
                    '，实际 ' + (activeWorkflowPath || '未命名工作流')
                });
              }

              if (!$nativeWorkflowOpen) {
                // Re-run callbacks only for the native parameter form's working
                // copy. Advanced editing leaves ComfyUI's own graph untouched.
                const openedGraph = app.rootGraph || app.graph;
                const openedNodes = new Map((openedGraph?._nodes || []).map(node => [String(node.id), node]));
                for (const sourceNode of workflow.nodes || []) {
                  const targetNode = openedNodes.get(String(sourceNode.id));
                  if (!targetNode || !Array.isArray(sourceNode.widgets_values)) continue;
                  sourceNode.widgets_values.forEach((sourceValue, index) => {
                    const widget = targetNode.widgets?.[index];
                    if (!widget) return;
                    const nextValue = cloneValue(sourceValue);
                    const groupToggle = widget.value && typeof widget.value === 'object' &&
                      typeof widget.value.toggled === 'boolean';
                    const nextToggle = typeof nextValue === 'boolean'
                      ? nextValue
                      : (typeof nextValue?.toggled === 'boolean' ? nextValue.toggled : null);
                    if (groupToggle && nextToggle != null) {
                      if (widget.value.toggled !== nextToggle) {
                        if (typeof widget.toggle === 'function') widget.toggle(nextToggle);
                        else if (typeof widget.doModeChange === 'function') widget.doModeChange(nextToggle);
                        else widget.value.toggled = nextToggle;
                      }
                    } else {
                      widget.value = nextValue;
                      try { widget.callback?.(nextValue, app.canvas, targetNode); } catch (_) {}
                    }
                  });
                }
                if (workflow.extra?.comfyMobile) {
                  openedGraph.extra = openedGraph.extra || {};
                  openedGraph.extra.comfyMobile = cloneValue(workflow.extra.comfyMobile);
                }
              }
              } else {
                await app.loadGraphData(workflow, true, false, null);
              }
            }
            const rootGraph = app.rootGraph || app.graph;
            if ($nativeWorkflowOpen) sourceWorkflow = rootGraph?.serialize?.() || sourceWorkflow;
            const loadedIds = new Set((rootGraph?._nodes || []).map(node => String(node.id)));
            const missing = (sourceWorkflow.nodes || []).filter(node => !loadedIds.has(String(node.id))).map(node => node.type);
            if (missing.length) return JSON.stringify({ok:false, error:'缺失节点：' + [...new Set(missing)].join(', ')});
            const sourceLinkCount = Array.isArray(sourceWorkflow.links)
              ? sourceWorkflow.links.length
              : Object.keys(sourceWorkflow.links || {}).length;
            const rootLinks = rootGraph?.links ?? rootGraph?._links;
            const loadedLinkCount = rootLinks instanceof Map
              ? rootLinks.size
              : Object.keys(rootLinks || {}).length;
            if (sourceLinkCount > 0 && loadedLinkCount === 0) {
              return JSON.stringify({ok:false, error:'工作流原有 ' + sourceLinkCount + ' 条连线，但高级编辑画布没有载入连线'});
            }
            if ($nativeWorkflowOpen && app.canvas) {
              // Android WebView can leave the Vue-nodes slot-layout sync pending
              // (pendingSlotSync stays true), so drawConnections() exits before
              // painting any links; a stale WebView-local Comfy.LinkRenderMode
              // of -1 hides them the same way. The graph itself still holds
              // every link. Force one redraw after loading, detect the vanish
              // (and whether the front canvas actually composites the link
              // pixels), repair by mechanism, and report the exact state.
              const canvas = app.canvas;
              const liteGraph = window.LiteGraph;
              const graphLinksRef = rootGraph?.links ?? rootGraph?._links;
              const graphLinkCount = graphLinksRef instanceof Map
                ? graphLinksRef.size
                : Object.keys(graphLinksRef || {}).length;
              const countPainted = () => Number(canvas?.renderedPaths?.size || 0);
              const sampleLinkPixel = () => {
                try {
                  const frontCtx = canvas.ctx;
                  const ds = canvas.ds;
                  const dpr = window.devicePixelRatio || 1;
                  if (!frontCtx || typeof frontCtx.getImageData !== 'function') return -1;
                  const toDevice = (p) => [
                    Math.round((p[0] * ds.scale + ds.offset[0]) * dpr),
                    Math.round((p[1] * ds.scale + ds.offset[1]) * dpr)
                  ];
                  const brightAt = (x, y) => {
                    if (x < 0 || y < 0 || x >= canvas.canvas.width || y >= canvas.canvas.height) return 0;
                    const data = frontCtx.getImageData(x, y, 1, 1).data;
                    if (data[3] === 0) return 0;
                    return (data[0] >= 60 || data[1] >= 60 || data[2] >= 60) ? 1 : 0;
                  };
                  for (const path of [...(canvas.renderedPaths || [])]) {
                    const startNode = rootGraph?.getNodeById?.(path.origin_id);
                    const endNode = rootGraph?.getNodeById?.(path.target_id);
                    const startPos = startNode?.getOutputPos?.(path.origin_slot);
                    const endPos = endNode?.getInputPos?.(path.target_slot);
                    if (!startPos || !endPos) continue;
                    for (let fraction = 0.1; fraction < 1.0; fraction += 0.1) {
                      const [dx, dy] = toDevice([
                        startPos[0] + (endPos[0] - startPos[0]) * fraction,
                        startPos[1] + (endPos[1] - startPos[1]) * fraction
                      ]);
                      for (let oy = -2; oy <= 2; oy += 2) {
                        for (let ox = -2; ox <= 2; ox += 2) {
                          if (brightAt(dx + ox, dy + oy)) return 1;
                        }
                      }
                    }
                  }
                  return 0;
                } catch (_) {
                  return -1;
                }
              };
              let paintedBefore = -1;
              let paintedAfter = -1;
              let paintedRepaired = -1;
              let linkPixelVisibleBefore = -1;
              let linkPixelVisibleAfter = -1;
              let linkPixelVisibleRepaired = -1;
              let repairMode = 'none';
              if (graphLinkCount > 0) {
                await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
                await new Promise(resolve => setTimeout(resolve, 120));
                paintedBefore = countPainted();
                linkPixelVisibleBefore = paintedBefore > 0 ? sampleLinkPixel() : -1;
                canvas.setDirty?.(true, true);
                canvas.draw?.(true, true);
                await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
                paintedAfter = countPainted();
                linkPixelVisibleAfter = paintedAfter > 0 ? sampleLinkPixel() : -1;
                if (paintedAfter === 0) {
                  const settings = app.ui?.settings;
                  const settingMode = Number(settings?.getSettingValue?.('Comfy.LinkRenderMode'));
                  if (Number(canvas.links_render_mode) === -1) {
                    canvas.links_render_mode = Number.isFinite(settingMode) && settingMode >= 0 ? settingMode : 2;
                    if (Number(settings?.getSettingValue?.('Comfy.LinkRenderMode')) === -1) {
                      if (typeof settings?.setSettingValueAsync === 'function') {
                        await settings.setSettingValueAsync('Comfy.LinkRenderMode', 2);
                      } else if (typeof settings?.setSettingValue === 'function') {
                        settings.setSettingValue('Comfy.LinkRenderMode', 2);
                      }
                    }
                    canvas.setDirty?.(true, true);
                    canvas.draw?.(true, true);
                    repairMode = 'linkMode';
                  } else if (liteGraph?.vueNodesMode === true) {
                    const previousVueMode = liteGraph.vueNodesMode;
                    const vueDomPresent = document.querySelectorAll('[data-node-id]').length > 0;
                    liteGraph.vueNodesMode = false;
                    canvas.setDirty?.(true, true);
                    canvas.draw?.(true, true);
                    if (vueDomPresent) liteGraph.vueNodesMode = previousVueMode;
                    repairMode = 'vueFallback';
                  } else {
                    canvas.setDirty?.(true, true);
                    canvas.draw?.(true, true);
                    repairMode = 'redraw';
                  }
                  await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
                  paintedRepaired = countPainted();
                  linkPixelVisibleRepaired = paintedRepaired > 0 ? sampleLinkPixel() : -1;
                }
              }
              // Persistent guard while editing: any later redraw (including the
              // Activity's post-load refresh) can hit the same WebView state.
              // A frame that painted links before but paints zero now gets
              // repaired in place on every frame until the state is healthy.
              if (graphLinkCount > 0 && !canvas.__comfyMobileLinkGuardInstalled) {
                const originalDrawConnections = canvas.drawConnections;
                canvas.__comfyMobileLinkGuardInstalled = true;
                canvas.drawConnections = function(context) {
                  originalDrawConnections.call(this, context);
                  const ref = this.graph?.links ?? this.graph?._links;
                  const count = ref instanceof Map ? ref.size : Object.keys(ref || {}).length;
                  if (count === 0) return;
                  const rendered = Number(this.renderedPaths?.size || 0);
                  const last = Number(this.__comfyMobileLastRendered || 0);
                  if (rendered !== 0) {
                    this.__comfyMobileLastRendered = rendered;
                    return;
                  }
                  if (last === 0) {
                    this.__comfyMobileLastRendered = 0;
                    return;
                  }
                  if (Number(this.links_render_mode) === -1) {
                    this.links_render_mode = 2;
                    originalDrawConnections.call(this, context);
                  } else if (window.LiteGraph?.vueNodesMode === true) {
                    const previousVueMode = window.LiteGraph.vueNodesMode;
                    const vueDomPresent = document.querySelectorAll('[data-node-id]').length > 0;
                    window.LiteGraph.vueNodesMode = false;
                    try {
                      originalDrawConnections.call(this, context);
                    } finally {
                      if (vueDomPresent) window.LiteGraph.vueNodesMode = previousVueMode;
                    }
                  }
                  this.__comfyMobileLastRendered = Number(this.renderedPaths?.size || 0);
                };
                if (repairMode === 'none') repairMode = 'guard';
              }
              window.__comfyMobileLinkRepair = {
                paintedBefore,
                paintedAfter,
                paintedRepaired,
                linkPixelVisibleBefore,
                linkPixelVisibleAfter,
                linkPixelVisibleRepaired,
                repairMode,
                graphLinkCount
              };
            }
            const allNodes = rootGraph?._nodes || [];
            const allNodeById = new Map(allNodes.map(node => [String(node.id), node]));
            const activeNodes = allNodes.filter(node => ![2, 4].includes(Number(node.mode ?? 0)));
            const nodeById = new Map(activeNodes.map(node => [String(node.id), node]));
            const linkValue = (link, property, fallbackIndex) => link?.[property] ?? link?.[fallbackIndex];
            const getLink = (id) => rootGraph?.links?.get?.(id) ?? rootGraph?.links?.[id];
            const typeValues = (value) => (Array.isArray(value) ? value : [value])
              .map(item => String(item || '').toUpperCase())
              .filter(Boolean);
            const typesMatch = (inputType, outputType) => {
              const inputs = typeValues(inputType);
              const outputs = typeValues(outputType);
              if (!inputs.length || !outputs.length) return true;
              return inputs.includes('*') || outputs.includes('*') || inputs.some(type => outputs.includes(type));
            };
            const bypassInputsForOutput = (node, outputSlot) => {
              const linkedInputs = (node.inputs || [])
                .map((input, index) => ({input, index}))
                .filter(item => item.input?.link != null);
              if (!linkedInputs.length) return [];
              const outputType = node.outputs?.[outputSlot]?.type;
              const compatible = linkedInputs.filter(item => typesMatch(item.input?.type, outputType));
              const sameSlot = compatible.find(item => item.index === outputSlot);
              if (sameSlot) return [sameSlot];
              if (compatible.length === 1) return compatible;
              if (compatible.length > 1) return [compatible[Math.min(outputSlot, compatible.length - 1)]];
              return linkedInputs.length === 1 ? linkedInputs : [];
            };
            const resolveActiveOrigins = (link, visiting = new Set()) => {
              if (link == null) return [];
              const originId = String(linkValue(link, 'origin_id', 1));
              const originSlot = Number(linkValue(link, 'origin_slot', 2) ?? 0);
              const origin = allNodeById.get(originId);
              if (!origin) return [];
              const mode = Number(origin.mode ?? 0);
              if (mode === 2) return [];
              if (mode !== 4) {
                return [{
                  originId,
                  originSlot,
                  type:String(linkValue(link, 'type', 5) || origin.outputs?.[originSlot]?.type || '')
                }];
              }
              const visitKey = originId + ':' + originSlot;
              if (visiting.has(visitKey)) return [];
              const next = new Set(visiting);
              next.add(visitKey);
              return bypassInputsForOutput(origin, originSlot).flatMap(item =>
                resolveActiveOrigins(getLink(item.input.link), next)
              );
            };
            const resolveInputOrigins = (node, input, inputIndex) => {
              if (typeof node.resolveInput === 'function') {
                try {
                  const resolved = node.resolveInput(inputIndex);
                  if (resolved?.widgetInfo) return [];
                  if (resolved != null) {
                    const originId = String(resolved.origin_id ?? resolved.originId ?? resolved[1]);
                    if (allNodeById.has(originId)) {
                      return [{
                        originId,
                        originSlot:Number(resolved.origin_slot ?? resolved.originSlot ?? resolved[2] ?? 0),
                        type:String(resolved.type ?? resolved[5] ?? input?.type ?? '')
                      }];
                    }
                  }
                } catch (_) {
                  // Older/custom LiteGraph nodes may not implement the official resolver completely.
                }
              }
              const link = input.link == null ? null : getLink(input.link);
              return resolveActiveOrigins(link);
            };
            const parentIds = (node) => [...new Set((node.inputs || []).flatMap((input, inputIndex) => {
              return resolveInputOrigins(node, input, inputIndex).map(origin => origin.originId);
            }).filter(id => id && nodeById.has(id)))];
            const isOutputNode = (node) => {
              const data = node.constructor?.nodeData || node.nodeData || {};
              return data.output_node === true || data.outputNode === true || /(?:Save|Preview|Output)/i.test(String(node.comfyClass || node.type || ''));
            };
            const outputNodes = activeNodes.filter(node => isOutputNode(node) && (node.inputs || []).some(input => input.link != null));
            if (!outputNodes.length) return JSON.stringify({ok:false, error:'当前工作流没有已连线的输出节点'});
            const executionIds = new Set();
            const includeAncestors = (node) => {
              const id = String(node.id);
              if (executionIds.has(id)) return;
              executionIds.add(id);
              for (const parentId of parentIds(node)) includeAncestors(nodeById.get(parentId));
            };
            outputNodes.forEach(includeAncestors);
            const isController = (node) => /Fast Groups (?:Bypasser|Muter)/i.test(String(node.comfyClass || node.type || ''));
            const controllers = activeNodes.filter(isController);
            const depthCache = new Map();
            const depthOf = (node, visiting = new Set()) => {
              const id = String(node.id);
              if (depthCache.has(id)) return depthCache.get(id);
              if (visiting.has(id)) return 0;
              const next = new Set(visiting); next.add(id);
              const parents = parentIds(node).filter(parent => executionIds.has(parent));
              const depth = parents.length ? 1 + Math.max(...parents.map(parent => depthOf(nodeById.get(parent), next))) : 0;
              depthCache.set(id, depth);
              return depth;
            };
            const displayNodes = [...activeNodes.filter(node => executionIds.has(String(node.id))), ...controllers.filter(node => !executionIds.has(String(node.id)))];
            displayNodes.sort((a, b) => {
              const controllerOrder = Number(isController(b)) - Number(isController(a));
              if (controllerOrder) return controllerOrder;
              const depthOrder = depthOf(a) - depthOf(b);
              if (depthOrder) return depthOrder;
              const xOrder = Number(a.pos?.[0] || 0) - Number(b.pos?.[0] || 0);
              if (xOrder) return xOrder;
              return Number(a.order || 0) - Number(b.order || 0);
            });
            const displayOrderById = new Map(displayNodes.map((node, index) => [String(node.id), index]));
            const inputMarkersByNode = new Map();
            const outputMarkersByNode = new Map();
            const relevantLinks = activeNodes.flatMap(node => (node.inputs || []).flatMap((input, targetSlot) => {
              return resolveInputOrigins(node, input, targetSlot).map(origin => ({
                origin_id:origin.originId,
                origin_slot:origin.originSlot,
                target_id:String(node.id),
                target_slot:targetSlot,
                type:String(input.type || origin.type || '')
              }));
            })).filter(link => executionIds.has(link.origin_id) && executionIds.has(link.target_id));
            const linkGroups = new Map();
            for (const link of relevantLinks) {
              const originId = String(linkValue(link, 'origin_id', 1));
              const originSlot = Number(linkValue(link, 'origin_slot', 2) ?? 0);
              const key = originId + ':' + originSlot;
              if (!linkGroups.has(key)) linkGroups.set(key, []);
              linkGroups.get(key).push(link);
            }
            const sortedLinkGroups = [...linkGroups.entries()].sort(([, a], [, b]) => {
              const aOrigin = String(linkValue(a[0], 'origin_id', 1));
              const bOrigin = String(linkValue(b[0], 'origin_id', 1));
              const nodeOrder = (displayOrderById.get(aOrigin) ?? 999999) - (displayOrderById.get(bOrigin) ?? 999999);
              if (nodeOrder) return nodeOrder;
              return Number(linkValue(a[0], 'origin_slot', 2) ?? 0) - Number(linkValue(b[0], 'origin_slot', 2) ?? 0);
            });
            const fallbackColors = {
              MODEL:'#B39DDB', CLIP:'#FFD54F', VAE:'#EF9A9A', CONDITIONING:'#FFB74D',
              LATENT:'#CE93D8', IMAGE:'#64B5F6', MASK:'#81C784', INT:'#90CAF9',
              FLOAT:'#80CBC4', STRING:'#A5D6A7', BOOLEAN:'#FFCC80'
            };
            const connectionColor = (type) => {
              const maps = [app.canvas?.default_connection_color_byType, globalThis.LGraphCanvas?.link_type_colors];
              for (const map of maps) {
                const value = map?.[type];
                if (typeof value === 'string' && value) return value;
              }
              return fallbackColors[String(type || '').toUpperCase()] || '#9E9E9E';
            };
            const branchSuffix = (index) => {
              let value = index;
              let suffix = '';
              do {
                suffix = String.fromCharCode(97 + (value % 26)) + suffix;
                value = Math.floor(value / 26) - 1;
              } while (value >= 0);
              return suffix;
            };
            const addMarker = (map, nodeId, marker) => {
              if (!map.has(nodeId)) map.set(nodeId, []);
              map.get(nodeId).push(marker);
            };
            const nextNumberByColor = new Map();
            sortedLinkGroups.forEach(([, links]) => {
              links.sort((a, b) => {
                const aTarget = String(linkValue(a, 'target_id', 3));
                const bTarget = String(linkValue(b, 'target_id', 3));
                const nodeOrder = (displayOrderById.get(aTarget) ?? 999999) - (displayOrderById.get(bTarget) ?? 999999);
                if (nodeOrder) return nodeOrder;
                return Number(linkValue(a, 'target_slot', 4) ?? 0) - Number(linkValue(b, 'target_slot', 4) ?? 0);
              });
              const first = links[0];
              const originId = String(linkValue(first, 'origin_id', 1));
              const originSlot = Number(linkValue(first, 'origin_slot', 2) ?? 0);
              const type = String(linkValue(first, 'type', 5) || nodeById.get(originId)?.outputs?.[originSlot]?.type || '');
              const color = connectionColor(type);
              const colorKey = color.toLowerCase();
              const number = String((nextNumberByColor.get(colorKey) || 0) + 1);
              nextNumberByColor.set(colorKey, Number(number));
              addMarker(outputMarkersByNode, originId, {
                label:number,
                type,
                color,
                portName:String(nodeById.get(originId)?.outputs?.[originSlot]?.name || type)
              });
              links.forEach((link, branchIndex) => {
                const targetId = String(linkValue(link, 'target_id', 3));
                const targetSlot = Number(linkValue(link, 'target_slot', 4) ?? 0);
                const inputType = String(linkValue(link, 'type', 5) || nodeById.get(targetId)?.inputs?.[targetSlot]?.type || type);
                addMarker(inputMarkersByNode, targetId, {
                  label:links.length > 1 ? number + branchSuffix(branchIndex) : number,
                  type:inputType,
                  color,
                  portName:String(nodeById.get(targetId)?.inputs?.[targetSlot]?.name || inputType)
                });
              });
            });
            window.__comfyMobileRelevantNodeIds = new Set(executionIds);
            const fields = [];
            const nodes = [];
            for (const [nodeOrder, node] of displayNodes.entries()) {
                const nodeKey = String(node.id);
                const controller = isController(node);
                const nodeData = node.constructor?.nodeData || node.nodeData || {};
                const inputDefinitions = {
                  ...(nodeData.input?.required || {}),
                  ...(nodeData.input?.optional || {}),
                  ...(nodeData.input?.hidden || {}),
                };
                nodes.push({
                  id: nodeKey,
                  title: node.title || node.type || '',
                  type: node.comfyClass || node.type || '',
                  order: nodeOrder,
                  isController: controller,
                  isOutput: isOutputNode(node),
                  inputMarkers: inputMarkersByNode.get(nodeKey) || [],
                  outputMarkers: outputMarkersByNode.get(nodeKey) || [],
                });
                const widgets = node.widgets || [];
                const nameCounts = new Map();
                for (const widget of widgets) nameCounts.set(widget?.name, (nameCounts.get(widget?.name) || 0) + 1);
                for (const [widgetIndex, widget] of widgets.entries()) {
                  if (!widget?.name || widget.type === 'button' || widget.type === 'hidden' || widget.type === 'converted-widget' || widget.hidden === true) continue;
                  const input = (node.inputs || []).find(i => i.widget?.name === widget.name || i.name === widget.name);
                  const inputSpec = inputDefinitions[widget.name];
                  const declaredType = Array.isArray(inputSpec) ? inputSpec[0] : inputSpec?.type;
                  const inputOptions = Array.isArray(inputSpec) ? inputSpec[1] : inputSpec;
                  const numericOption = (name) => {
                    const candidate = widget.options?.[name] ?? inputOptions?.[name];
                    return Number.isFinite(candidate) ? candidate : null;
                  };
                  const values = Array.isArray(widget.options?.values) ? widget.options.values.map(String) : [];
                  let value = widget.value;
                  const groupToggle = value && typeof value === 'object' && typeof value.toggled === 'boolean' &&
                    (typeof widget.toggle === 'function' || typeof widget.doModeChange === 'function');
                  if (groupToggle) value = value.toggled;
                  else if (value && typeof value === 'object') {
                    try { value = JSON.parse(JSON.stringify(value)); } catch (_) { continue; }
                  }
                  if (typeof value === 'undefined' || typeof value === 'function') value = null;
                  const widgetKey = nameCounts.get(widget.name) > 1 ? widget.name + '#' + widgetIndex : widget.name;
                  const rawLabel = widget.label || input?.label || widget.name;
                  fields.push({
                    key: nodeKey + '/' + widgetKey,
                    nodeId: nodeKey,
                    nodeTitle: node.title || node.type || '',
                    nodeType: node.comfyClass || node.type || '',
                    nodeOrder,
                    name: widget.name,
                    label: groupToggle && rawLabel.startsWith('Enable ') ? '启用：' + rawLabel.slice(7) : rawLabel,
                    widgetType: groupToggle ? 'toggle' : String(widget.type || typeof value),
                    dataType: typeof declaredType === 'string' ? declaredType : '',
                    widgetIndex,
                    refreshesWorkflow: groupToggle,
                    value,
                    values,
                    min: numericOption('min'),
                    max: numericOption('max'),
                    step: numericOption('step'),
                    precision: numericOption('precision'),
                    linked: input?.link != null,
                  });
                }
            }
            return JSON.stringify({
              ok:true,
              fields,
              nodes,
              diagnostics:{
                nativeWorkflowOpen:$nativeWorkflowOpen,
                sourceLinkCount,
                loadedLinkCount,
                renderedPathCount:Number(app.canvas?.renderedPaths?.size || 0),
                canvasLinkMode:Number(app.canvas?.links_render_mode),
                vueNodesMode:window.LiteGraph?.vueNodesMode === true,
                paintedBeforeRefresh:Number(window.__comfyMobileLinkRepair?.paintedBefore ?? -1),
                paintedAfterRefresh:Number(window.__comfyMobileLinkRepair?.paintedAfter ?? -1),
                paintedAfterRepair:Number(window.__comfyMobileLinkRepair?.paintedRepaired ?? -1),
                linkPixelVisibleBefore:Number(window.__comfyMobileLinkRepair?.linkPixelVisibleBefore ?? -1),
                linkPixelVisibleAfter:Number(window.__comfyMobileLinkRepair?.linkPixelVisibleAfter ?? -1),
                linkPixelVisibleRepaired:Number(window.__comfyMobileLinkRepair?.linkPixelVisibleRepaired ?? -1),
                linkRepairMode:String(window.__comfyMobileLinkRepair?.repairMode || 'none')
              }
            });
          } catch (error) {
            return JSON.stringify({ok:false, error:'工作流解析错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private fun promptApplyScript(encodedUpdates: String) = """
        (() => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
            const graph = app.rootGraph || app.graph;
            const text = new TextDecoder().decode(Uint8Array.from(atob('$encodedUpdates'), c => c.charCodeAt(0)));
            const updates = JSON.parse(text);
            const nodeMap = new Map((graph?._nodes || []).map(node => [String(node.id), node]));
            for (const update of updates) {
              const cut = update.key.lastIndexOf('/');
              const node = nodeMap.get(update.key.slice(0, cut));
              const widget = update.widgetIndex >= 0
                ? node?.widgets?.[update.widgetIndex]
                : node?.widgets?.find(w => w.name === update.key.slice(cut + 1));
              if (!widget) continue;
              const groupToggle = widget.value && typeof widget.value === 'object' && typeof widget.value.toggled === 'boolean';
              if (groupToggle && typeof update.value === 'boolean') {
                if (widget.value.toggled !== update.value) {
                  if (typeof widget.toggle === 'function') widget.toggle(update.value);
                  else if (typeof widget.doModeChange === 'function') widget.doModeChange(update.value);
                  else widget.value.toggled = update.value;
                }
              } else {
                widget.value = update.value;
                try { widget.callback?.(update.value, app.canvas, node); } catch (_) {}
              }
            }
            return JSON.stringify({ok:true});
          } catch (error) {
            return JSON.stringify({ok:false, error:'应用手机参数错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private val PROMPT_BEFORE_QUEUE_SCRIPT = """
        (() => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            const graph = app?.rootGraph || app?.graph;
            if (!graph) return JSON.stringify({ok:false, error:'ComfyUI 工作流画布尚未就绪'});
            const nodeMap = new Map((graph._nodes || []).map(node => [String(node.id), node]));
            const relevantIds = window.__comfyMobileRelevantNodeIds || new Set();
            for (const [nodeId, node] of nodeMap.entries()) {
              if (!relevantIds.has(String(nodeId))) continue;
              for (const widget of (node.widgets || [])) {
                if (typeof widget.beforeQueued !== 'function') continue;
                try {
                  widget.beforeQueued({isPartialExecution:false});
                } catch (error) {
                  throw new Error('节点 ' + String(node.title || node.type || node.id) +
                    '（' + String(node.id) + '）控件 ' + String(widget.name || widget.label || '?') +
                    ' 的排队前回调失败：' + String(error?.stack || error));
                }
              }
            }
            return JSON.stringify({ok:true});
          } catch (error) {
            return JSON.stringify({ok:false, error:'排队前回调错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private val PROMPT_CONVERT_SCRIPT = """
        (async () => {
          try {
            const setPhase = (stage, node, widget) => {
              const details = [stage];
              if (node) details.push('节点=' + String(node.title || node.type || node.id), 'ID=' + String(node.id));
              if (widget) details.push('控件=' + String(widget.name || widget.label || '?'));
              window.__comfyMobileCurrentPhase = details.join('，');
            };
            setPhase('查找 ComfyUI 前端对象');
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
            const graph = app.rootGraph || app.graph;
            const nodeMap = new Map((graph?._nodes || []).map(node => [String(node.id), node]));
            const restores = [];
            const wrap = (object, key, stage, node, widget) => {
              const original = object?.[key];
              if (typeof original !== 'function') return;
              try {
                object[key] = function(...args) {
                  setPhase(stage, node, widget);
                  return original.apply(this, args);
                };
                restores.push(() => { object[key] = original; });
              } catch (_) {}
            };
            wrap(graph, 'computeExecutionOrder', '计算节点执行顺序');
            wrap(graph, 'serialize', '序列化画布工作流');
            for (const node of nodeMap.values()) {
              for (const widget of (node.widgets || [])) {
                wrap(widget, 'serializeValue', '序列化节点控件', node, widget);
              }
            }
            let result;
            try {
              result = await app.graphToPrompt();
            } finally {
              for (const restore of restores.reverse()) {
                try { restore(); } catch (_) {}
              }
            }
            const relevantIds = window.__comfyMobileRelevantNodeIds || new Set();
            if (!Object.keys(result.output || {}).length) {
              return JSON.stringify({ok:false, error:'当前工作流没有可执行的输出链'});
            }
            setPhase('序列化 Prompt 和工作流');
            return JSON.stringify({
              ok:true,
              prompt:result.output,
              workflow:result.workflow,
              relevantNodeIds:[...relevantIds].map(String)
            });
          } catch (error) {
            return JSON.stringify({ok:false, error:'生成参数转换错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private fun syncWorkflowScript(encodedUpdates: String) = """
        (async () => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            const graph = app?.rootGraph || app?.graph;
            if (!graph) return JSON.stringify({ok:false, error:'ComfyUI 工作流画布尚未就绪'});
            const text = new TextDecoder().decode(Uint8Array.from(atob('$encodedUpdates'), c => c.charCodeAt(0)));
            const updates = JSON.parse(text);
            const nodeMap = new Map((graph._nodes || []).map(node => [String(node.id), node]));
            for (const update of updates) {
              const cut = update.key.lastIndexOf('/');
              const node = nodeMap.get(update.key.slice(0, cut));
              const widget = update.widgetIndex >= 0
                ? node?.widgets?.[update.widgetIndex]
                : node?.widgets?.find(w => w.name === update.key.slice(cut + 1));
              if (!widget) continue;
              const groupToggle = widget.value && typeof widget.value === 'object' && typeof widget.value.toggled === 'boolean';
              if (groupToggle && typeof update.value === 'boolean') {
                if (widget.value.toggled !== update.value) {
                  if (typeof widget.toggle === 'function') widget.toggle(update.value);
                  else if (typeof widget.doModeChange === 'function') widget.doModeChange(update.value);
                  else widget.value.toggled = update.value;
                }
              } else {
                widget.value = update.value;
                try { widget.callback?.(update.value, app.canvas, node); } catch (_) {}
              }
            }
            return JSON.stringify({ok:true, workflow:graph.serialize()});
          } catch (error) {
            return JSON.stringify({ok:false, error:'工作流参数同步错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    companion object {
        private const val IMAGE_IMPORT_PATH = "__comfy_mobile_import"
        private val SUPPORTED_WORKFLOW_IMAGE_TYPES = setOf("image/png", "image/webp", "image/avif")

        internal fun normalizeServerWorkflowPath(value: String?): String? = value
            ?.trim()
            ?.replace('\\', '/')
            ?.trimStart('/')
            ?.replace(Regex("^workflows/", RegexOption.IGNORE_CASE), "")
            ?.takeIf(String::isNotBlank)

        internal fun frontendWorkflowStorePath(value: String?): String? = normalizeServerWorkflowPath(value)
            ?.let { path -> if (path.endsWith(".json", ignoreCase = true)) path else "$path.json" }
            ?.let { path -> "workflows/$path" }

        internal fun isPageReadyForScripts(
            currentUrl: String,
            allowedOrigin: String,
            progress: Int,
            pageEpoch: Int,
            finishedPageEpoch: Int,
            attached: Boolean,
        ): Boolean =
            allowedOrigin.isNotBlank() && currentUrl != "about:blank" &&
                currentUrl.startsWith(allowedOrigin) && progress >= 100 &&
                pageEpoch == finishedPageEpoch && attached

        private val READY_SCRIPT = """
            (async () => {
              try {
                const app = window.comfyAPI?.app?.app;
                if (!(app?.rootGraph || app?.graph)) return JSON.stringify({ok:false,error:'工作流画布尚未就绪'});
                const workspace = app.extensionManager;
                if (!app.vueAppReady || !workspace) {
                  return JSON.stringify({ok:false,error:'ComfyUI 网页应用尚未就绪'});
                }
                if (workspace.spinner === true) {
                  delete window.__comfyMobileReadySince;
                  delete window.__comfyMobileReadyKey;
                  return JSON.stringify({ok:false,error:'ComfyUI 正在恢复工作流标签'});
                }
                const settings = app?.ui?.settings;
                const locale = settings?.getSettingValue?.('Comfy.Locale');
                if (settings && locale !== 'zh') {
                  delete window.__comfyMobileReadySince;
                  delete window.__comfyMobileReadyKey;
                  if (typeof settings?.setSettingValueAsync === 'function') await settings.setSettingValueAsync('Comfy.Locale', 'zh');
                  else settings?.setSettingValue?.('Comfy.Locale', 'zh');
                  return JSON.stringify({ok:false,error:'正在切换 ComfyUI 网页语言'});
                }
                // A fresh WebView profile enables Nodes 2.0 by default in
                // Frontend 1.45.21, while the user's working browser uses the
                // classic canvas. Some custom nodes never finish the DOM slot
                // sync in Android WebView; ComfyUI then intentionally skips all
                // links while LayoutStore.pendingSlotSync remains true. Use the
                // same classic renderer as the browser before opening a graph.
                const modernNodesEnabled = settings?.getSettingValue?.('Comfy.VueNodes.Enabled');
                if (settings && modernNodesEnabled !== false) {
                  delete window.__comfyMobileReadySince;
                  delete window.__comfyMobileReadyKey;
                  if (typeof settings?.setSettingValueAsync === 'function') {
                    await settings.setSettingValueAsync('Comfy.VueNodes.Enabled', false);
                  } else {
                    settings?.setSettingValue?.('Comfy.VueNodes.Enabled', false);
                  }
                  return JSON.stringify({ok:false,error:'正在切换为与浏览器一致的经典节点画布'});
                }
                const modernNodeElements = document.querySelectorAll('[data-node-id]').length;
                if (window.LiteGraph?.vueNodesMode === true || modernNodeElements > 0) {
                  delete window.__comfyMobileReadySince;
                  delete window.__comfyMobileReadyKey;
                  return JSON.stringify({ok:false,error:'正在等待经典节点画布接管'});
                }
                const workflowStore = workspace.workflow;
                if (!workflowStore?.getWorkflowByPath || !workflowStore?.syncWorkflows) {
                  return JSON.stringify({ok:false,error:'ComfyUI 工作流仓库尚未就绪'});
                }
                const readyKey = String(workflowStore.activeWorkflow?.path || '') + ':' +
                  String(workflowStore.workflows?.length || 0);
                if (window.__comfyMobileReadyKey !== readyKey) {
                  window.__comfyMobileReadyKey = readyKey;
                  window.__comfyMobileReadySince = Date.now();
                  return JSON.stringify({ok:false,error:'正在等待 ComfyUI 工作流状态稳定'});
                }
                if (Date.now() - Number(window.__comfyMobileReadySince || 0) < 750) {
                  return JSON.stringify({ok:false,error:'正在等待 ComfyUI 工作流状态稳定'});
                }
                window.__comfyMobileApp = app;
                return JSON.stringify({ok:true});
              } catch (error) {
                return JSON.stringify({ok:false,error:'前端初始化错误：' + String(error)});
              }
            })()
        """.trimIndent()

        private val EXPORT_SCRIPT = """
            (() => {
              try {
                const app = window.__comfyMobileApp;
                const graph = app?.rootGraph || app?.graph;
                if (!graph) return JSON.stringify({ok:false,error:'工作流画布尚未就绪'});
                return JSON.stringify({ok:true, workflow:graph.serialize()});
              } catch (error) {
                return JSON.stringify({ok:false,error:'高级编辑同步错误：' + String(error?.stack || error)});
              }
            })()
        """.trimIndent()

        private fun widgetButtonScript(encodedPayload: String) = """
            (async () => {
              try {
                const payload = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob('$encodedPayload'), c => c.charCodeAt(0))));
                const app = window.__comfyMobileApp;
                const graph = app?.rootGraph || app?.graph;
                if (!graph) return JSON.stringify({ok:false,error:'工作流画布尚未就绪'});
                const node = (graph._nodes || []).find(item => String(item.id) === String(payload.nodeId));
                if (!node) return JSON.stringify({ok:false,error:'找不到种子部件 ' + payload.nodeId});
                const widget = (node.widgets || []).find(item => item?.type === 'button' && String(item.name || '').includes(payload.actionToken));
                if (!widget) return JSON.stringify({ok:false,error:'网页中没有此种子操作'});
                if (widget.disabled) return JSON.stringify({ok:false,error:'当前还没有可使用的上次排队种子'});
                const action = widget.callback || widget.onClick;
                if (typeof action !== 'function') return JSON.stringify({ok:false,error:'种子按钮没有可执行回调'});
                const result = action.call(widget);
                if (result && typeof result.then === 'function') await result;
                await new Promise(resolve => setTimeout(resolve, 50));
                return JSON.stringify({ok:true, workflow:graph.serialize()});
              } catch (error) {
                return JSON.stringify({ok:false,error:'种子操作错误：' + String(error?.stack || error)});
              }
            })()
        """.trimIndent()
    }
}
