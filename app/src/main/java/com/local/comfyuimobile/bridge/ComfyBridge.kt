package com.local.comfyuimobile.bridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.local.comfyuimobile.model.GeneratedPrompt
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterSection
import com.local.comfyuimobile.data.WorkflowPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
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

    val webView: WebView = WebView(activity)
    @Volatile private var allowedOrigin: String = ""
    @Volatile private var pageLoadError: String? = null
    private val pendingImageImports = ConcurrentHashMap<String, PendingImageImport>()

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClient() {
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

    suspend fun loadWorkflow(rawJson: String): List<ParameterField> {
        awaitReady()
        val encoded = Base64.getEncoder().encodeToString(rawJson.toByteArray(Charsets.UTF_8))
        val response = evaluate(workflowManifestScript(encoded))
        val root = JSONObject(response)
        if (!root.optBoolean("ok")) throw IllegalStateException(root.optString("error", "工作流解析失败"))
        val layout = parseLayout(rawJson)
        val fieldsJson = root.optJSONArray("fields") ?: JSONArray()
        return buildList {
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
                    ),
                )
            }
        }.sortedWith(compareBy<ParameterField> { it.section.ordinal }.thenBy { it.order })
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
        val response = evaluate(promptScript(encoded))
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

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
    }

    private suspend fun evaluate(script: String, timeoutMillis: Long = 120_000L): String = withContext(Dispatchers.Main.immediate) {
        val token = UUID.randomUUID().toString()
        val quotedToken = JSONObject.quote(token)
        val kickoff = """
            (() => {
              window.__comfyMobileResults = window.__comfyMobileResults || Object.create(null);
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
              return 'started';
            })()
        """.trimIndent()
        val started = evaluateImmediate(kickoff)
        if (started != "started") throw IllegalStateException("ComfyUI 页面正在加载")

        val poll = """
            (() => {
              const store = window.__comfyMobileResults;
              const result = store?.[$quotedToken];
              if (!result) return '';
              delete store[$quotedToken];
              return JSON.stringify(result);
            })()
        """.trimIndent()
        val cleanup = "delete window.__comfyMobileResults?.[$quotedToken]"
        val deadline = System.currentTimeMillis() + timeoutMillis
        try {
            while (System.currentTimeMillis() < deadline) {
                val raw = evaluateImmediate(poll)
                if (raw.isNotEmpty()) {
                    val result = JSONObject(raw)
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
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { encodedResult ->
                if (!continuation.isActive) return@evaluateJavascript
                runCatching { decodeJavascriptResult(encodedResult) }
                    .onSuccess(continuation::resume)
                    .onFailure(continuation::resumeWithException)
            }
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
            const fields = [];
            const visit = (graph, prefix = '') => {
                for (const node of (graph?._nodes || [])) {
                const nodeKey = prefix + String(node.id);
                const mode = Number(node.mode ?? 0);
                if (mode === 2 || mode === 4) continue;
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
                  if (node.subgraph) visit(node.subgraph, nodeKey + ':');
                }
                for (const [id, subgraph] of (graph?.subgraphs?.entries?.() || [])) {
                  visit(subgraph, prefix + 'subgraph:' + String(id) + ':');
                }
            };
            visit(rootGraph);
            return JSON.stringify({ok:true, fields});
          } catch (error) {
            return JSON.stringify({ok:false, error:'工作流解析错误：' + String(error?.stack || error)});
          }
        })()
    """.trimIndent()

    private fun promptScript(encodedUpdates: String) = """
        (async () => {
          try {
            const app = window.__comfyMobileApp || window.comfyAPI?.app?.app;
            if (!app) return JSON.stringify({ok:false, error:'ComfyUI 前端对象尚未就绪'});
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
            visit(app.rootGraph || app.graph);
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
            for (const node of nodeMap.values()) {
              for (const widget of (node.widgets || [])) {
                await widget.beforeQueued?.({isPartialExecution:false});
              }
            }
            const result = await app.graphToPrompt();
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
    }
}
