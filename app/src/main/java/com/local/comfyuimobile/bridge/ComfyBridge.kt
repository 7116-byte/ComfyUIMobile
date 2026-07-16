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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ComfyBridge(private val activity: Activity) {
    val webView: WebView = WebView(activity)
    @Volatile private var allowedOrigin: String = ""
    @Volatile private var pageLoadError: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.databaseEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.webViewClient = object : WebViewClient() {
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
                    else -> ParameterClassifier.section(nodeType, name, kind)
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
                    ),
                )
            }
        }.sortedWith(compareBy<ParameterField> { it.section.ordinal }.thenBy { it.order })
    }

    suspend fun buildPrompt(fields: List<ParameterField>): GeneratedPrompt {
        awaitReady()
        val updates = JSONArray().apply {
            fields.forEach { field ->
                put(JSONObject().put("key", field.key).put("value", parseJsonValue(field.valueJson)))
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
                for (const widget of (node.widgets || [])) {
                  if (!widget?.name || widget.type === 'button' || widget.options?.serialize === false) continue;
                  const input = (node.inputs || []).find(i => i.widget?.name === widget.name || i.name === widget.name);
                  const values = Array.isArray(widget.options?.values) ? widget.options.values.map(String) : [];
                  let value = widget.value;
                  if (typeof value === 'undefined' || typeof value === 'function') value = null;
                  fields.push({
                    key: nodeKey + '/' + widget.name,
                    nodeId: nodeKey,
                    nodeTitle: node.title || node.type || '',
                    nodeType: node.comfyClass || node.type || '',
                    name: widget.name,
                    label: widget.label || input?.label || widget.name,
                    widgetType: String(widget.type || typeof value),
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
              const widget = node?.widgets?.find(w => w.name === update.key.slice(cut + 1));
              if (!widget) continue;
              widget.value = update.value;
              try { widget.callback?.(update.value, app.canvas, node); } catch (_) {}
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

    companion object {
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
