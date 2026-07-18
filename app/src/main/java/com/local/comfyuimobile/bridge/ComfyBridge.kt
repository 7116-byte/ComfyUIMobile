package com.local.comfyuimobile.bridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
    private data class PendingImageImport(val uri: Uri, val mimeType: String)

    var webView: WebView by mutableStateOf(WebView(activity))
        private set
    @Volatile private var allowedOrigin: String = ""
    @Volatile private var pageLoadError: String? = null
    @Volatile private var lastBridgePhase: String = "尚未执行前端脚本"
    @Volatile private var rendererEpoch: Int = 0
    private val pendingImageImports = ConcurrentHashMap<String, PendingImageImport>()
    private val pendingEvaluations = ConcurrentHashMap<String, CancellableContinuation<String>>()

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        configureWebView(webView)
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
            if (webView.url.orEmpty().startsWith(origin)) webView.reload() else webView.loadUrl("$origin/")
        }

        val deadline = System.currentTimeMillis() + timeoutMillis
        var currentUrl = "about:blank"
        var progress = 0
        while (System.currentTimeMillis() < deadline) {
            pageLoadError?.let { throw IllegalStateException(it) }
            val pageState = withContext(Dispatchers.Main.immediate) {
                webView.url.orEmpty() to webView.progress
            }
            currentUrl = pageState.first.ifBlank { "about:blank" }
            progress = pageState.second
            if (currentUrl.startsWith(origin) && currentUrl != "about:blank" && progress >= 100) return
            delay(100)
        }
        throw IllegalStateException("网页加载超时：当前地址 $currentUrl，进度 $progress%")
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

    suspend fun loadWorkflow(rawJson: String): WorkflowManifest {
        awaitReady()
        val encoded = Base64.getEncoder().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val response = evaluate(workflowManifestScript(encoded))
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "工作流解析失败"))
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
                val kind = ParameterClassifier.kind(nodeType, name, widgetType, value, options)
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
                        minimum = item.optNullableDouble("min"),
                        maximum = item.optNullableDouble("max"),
                        step = item.optNullableDouble("step"),
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
        AppLogger.info("前端桥接：参数序列化完成，Base64=${encoded.length} 字符")
        val response = evaluate(promptScript(encoded))
        lastBridgePhase = "Prompt 已返回 Android，响应=${response.length} 字符"
        AppLogger.info("前端桥接：$lastBridgePhase")
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "生成 Prompt 失败"))
        val prompt = root.getJSONObject("prompt")
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
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun evaluate(script: String, timeoutMillis: Long = 120_000L): String = withContext(Dispatchers.Main.immediate) {
        val expectedRendererEpoch = rendererEpoch
        val token = UUID.randomUUID().toString()
        val quotedToken = JSONObject.quote(token)
        val kickoff = """
            (() => {
              window.__comfyMobileResults = window.__comfyMobileResults || Object.create(null);
              window.__comfyMobileCurrentPhase = '';
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
                  }
                })();
              }, 0);
              return 'started';
            })()
        """.trimIndent()
        val started = try {
            evaluateImmediate(kickoff)
        } catch (throwable: Throwable) {
            if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
            throw throwable
        }
        if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
        if (started != "started") throw IllegalStateException("ComfyUI 页面正在加载")

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
        val cleanup = "delete window.__comfyMobileResults?.[$quotedToken]"
        val deadline = System.currentTimeMillis() + timeoutMillis
        var reportedPhase = ""
        try {
            while (System.currentTimeMillis() < deadline) {
                if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
                val raw = try {
                    evaluateImmediate(poll)
                } catch (throwable: Throwable) {
                    if (rendererEpoch != expectedRendererEpoch) throw rendererGoneException()
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

    private fun workflowManifestScript(encodedWorkflow: String) = """
        (async () => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
            const text = new TextDecoder().decode(Uint8Array.from(atob('$encodedWorkflow'), c => c.charCodeAt(0)));
            const workflow = JSON.parse(text);
            await app.loadGraphData(workflow, true, false);
            const rootGraph = app.rootGraph || app.graph;
            const loadedIds = new Set((rootGraph?._nodes || []).map(node => String(node.id)));
            const missing = (workflow.nodes || []).filter(node => !loadedIds.has(String(node.id))).map(node => node.type);
            if (missing.length) return JSON.stringify({ok:false, error:'缺失节点：' + [...new Set(missing)].join(', ')});
            const activeNodes = (rootGraph?._nodes || []).filter(node => ![2, 4].includes(Number(node.mode ?? 0)));
            const nodeById = new Map(activeNodes.map(node => [String(node.id), node]));
            const getLink = (id) => rootGraph?.links?.get?.(id) ?? rootGraph?.links?.[id];
            const parentIds = (node) => (node.inputs || []).map(input => {
              const link = input.link == null ? null : getLink(input.link);
              return link == null ? null : String(link.origin_id ?? link.originId ?? link[1]);
            }).filter(id => id && nodeById.has(id));
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
            const rawLinks = rootGraph?.links;
            const allLinks = rawLinks instanceof Map ? [...rawLinks.values()] : Object.values(rawLinks || {});
            const linkValue = (link, property, fallbackIndex) => link?.[property] ?? link?.[fallbackIndex];
            const relevantLinks = allLinks.filter(link => {
              const originId = String(linkValue(link, 'origin_id', 1));
              const targetId = String(linkValue(link, 'target_id', 3));
              return executionIds.has(originId) && executionIds.has(targetId);
            });
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
                    widgetIndex,
                    refreshesWorkflow: groupToggle,
                    value,
                    values,
                    min: Number.isFinite(widget.options?.min) ? widget.options.min : null,
                    max: Number.isFinite(widget.options?.max) ? widget.options.max : null,
                    step: Number.isFinite(widget.options?.step) ? widget.options.step : null,
                    linked: input?.link != null,
                  });
                }
            }
            return JSON.stringify({ok:true, fields, nodes});
          } catch (error) {
            return JSON.stringify({ok:false, error:'工作流解析错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private fun promptScript(encodedUpdates: String) = """
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
            setPhase('解析手机参数');
            const text = new TextDecoder().decode(Uint8Array.from(atob('$encodedUpdates'), c => c.charCodeAt(0)));
            const updates = JSON.parse(text);
            const nodeMap = new Map();
            const visit = (graph, prefix = '') => {
              for (const node of (graph?._nodes || [])) {
                const nodeKey = prefix + String(node.id);
                nodeMap.set(nodeKey, node);
                if (node.subgraph) visit(node.subgraph, nodeKey + ':');
              }
              for (const [id, subgraph] of (graph?.subgraphs?.entries?.() || [])) {
                visit(subgraph, prefix + 'subgraph:' + String(id) + ':');
              }
            };
            setPhase('扫描工作流节点');
            visit(app.rootGraph || app.graph);
            setPhase('应用手机参数');
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
            const relevantIds = window.__comfyMobileRelevantNodeIds || new Set();
            for (const [nodeId, node] of nodeMap.entries()) {
              if (!relevantIds.has(String(nodeId))) continue;
              for (const widget of (node.widgets || [])) {
                if (typeof widget.beforeQueued !== 'function') continue;
                setPhase('执行排队前回调', node, widget);
                try {
                  widget.beforeQueued({isPartialExecution:false});
                } catch (error) {
                  throw new Error('节点 ' + String(node.title || node.type || node.id) +
                    '（' + String(node.id) + '）控件 ' + String(widget.name || widget.label || '?') +
                    ' 的排队前回调失败：' + String(error?.stack || error));
                }
              }
            }
            setPhase('准备转换 API Prompt');
            const graph = app.rootGraph || app.graph;
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
            setPhase('筛选当前输出链');
            for (const nodeId of Object.keys(result.output || {})) {
              if (!relevantIds.has(String(nodeId))) delete result.output[nodeId];
            }
            if (!Object.keys(result.output || {}).length) {
              return JSON.stringify({ok:false, error:'当前工作流没有可执行的输出链'});
            }
            setPhase('序列化 Prompt 和工作流');
            return JSON.stringify({ok:true, prompt:result.output, workflow:result.workflow});
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
            const nodeMap = new Map();
            const visit = (current, prefix = '') => {
              for (const node of (current?._nodes || [])) {
                const nodeKey = prefix + String(node.id);
                nodeMap.set(nodeKey, node);
                if (node.subgraph) visit(node.subgraph, nodeKey + ':');
              }
              for (const [id, subgraph] of (current?.subgraphs?.entries?.() || [])) {
                visit(subgraph, prefix + 'subgraph:' + String(id) + ':');
              }
            };
            visit(graph);
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

        private val READY_SCRIPT = """
            (async () => {
              try {
                const app = window.comfyAPI?.app?.app;
                if (!(app?.rootGraph || app?.graph)) return JSON.stringify({ok:false,error:'工作流画布尚未就绪'});
                const settings = app?.ui?.settings;
                const locale = settings?.getSettingValue?.('Comfy.Locale');
                if (settings && locale !== 'zh') {
                  if (typeof settings?.setSettingValueAsync === 'function') await settings.setSettingValueAsync('Comfy.Locale', 'zh');
                  else settings?.setSettingValue?.('Comfy.Locale', 'zh');
                  await new Promise(resolve => setTimeout(resolve, 300));
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
