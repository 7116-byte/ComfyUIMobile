package com.local.comfyuimobile

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.bridge.AdvancedEditorSession
import com.local.comfyuimobile.bridge.WorkflowImageReader
import com.local.comfyuimobile.data.AppPreferences
import com.local.comfyuimobile.data.AppLogger
import com.local.comfyuimobile.data.LocalResultCache
import com.local.comfyuimobile.data.PromptHistory
import com.local.comfyuimobile.data.WorkflowPolicy
import com.local.comfyuimobile.data.WorkflowPath
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.WorkflowDocument
import com.local.comfyuimobile.model.WorkflowEntry
import com.local.comfyuimobile.model.WorkflowNode
import com.local.comfyuimobile.network.ComfyClient
import com.local.comfyuimobile.network.ExecutionNodeResolver
import com.local.comfyuimobile.network.LanAddress
import com.local.comfyuimobile.network.LanScanner
import com.local.comfyuimobile.network.ResultParser
import com.local.comfyuimobile.network.PromptSubmissionException
import com.local.comfyuimobile.network.ProgressStateParser
import com.local.comfyuimobile.service.JobMonitorService
import com.local.comfyuimobile.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val preferences = AppPreferences(application)
    private val localResultCache = LocalResultCache(application)
    private val client = ComfyClient()
    private val scanner = LanScanner(application, client)
    private val updates = UpdateManager(application)
    private val clientId = application
        .getSharedPreferences("comfy_mobile_runtime", android.content.Context.MODE_PRIVATE)
        .let { store ->
            store.getString("stable_client_id", null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { store.edit().putString("stable_client_id", it).apply() }
        }
    private val _state = MutableStateFlow(AppUiState(loggingEnabled = AppLogger.isEnabled(application)))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var bridge: ComfyBridge? = null
    private var reconnectJob: Job? = null
    private var parameterRefreshJob: Job? = null
    private var generationJob: Job? = null
    private var workflowSaveJob: Job? = null
    private var visibleNodeJob: Job? = null
    private val bridgeOperationMutex = Mutex()
    private val monitoredJobIds = mutableSetOf<String>()
    private var visibleNodeChangedAt = 0L
    @Volatile private var lastUpdateCheck: Long = 0L

    init {
        viewModelScope.launch {
            preferences.settings.collect { stored ->
                val submittedJobsChanged = _state.value.submittedJobIds != stored.submittedJobs
                lastUpdateCheck = stored.lastUpdateCheck
                _state.update {
                    it.copy(
                        savedServers = stored.profiles,
                        promptHistory = stored.promptHistory,
                        submittedJobIds = stored.submittedJobs,
                        autoSaveResults = stored.autoSaveResults,
                        cacheOutputRules = stored.cacheOutputRules,
                        cacheClearedAt = stored.cacheClearedAt,
                        favoriteResultKeys = stored.favoriteResultKeys,
                        recentWorkflowPaths = stored.recentWorkflows,
                        serverInput = it.activeServer?.baseUrl
                            ?: stored.activeServerUrl.ifBlank { it.serverInput },
                    )
                }
                if (submittedJobsChanged && _state.value.activeServer != null) {
                    refreshTasksInternal()
                }
            }
        }
        viewModelScope.launch {
            val cached = localResultCache.load()
            _state.update { it.copy(localResults = cached) }
        }
    }

    fun attachBridge(value: ComfyBridge) {
        bridge = value
    }

    fun setServerInput(value: String) = _state.update { it.copy(serverInput = value) }
    fun clearMessage() = _state.update { it.copy(error = null, notice = null) }

    fun openAdvancedEditor() {
        if (_state.value.loading || _state.value.generating || generationJob?.isActive == true) return
        _state.value.selectedWorkflow ?: return
        _state.value.activeServer ?: return
        val activeBridge = bridge ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                bridgeOperationMutex.withLock {
                    val currentWorkflow = activeBridge.syncWorkflow(_state.value.fields)
                    AdvancedEditorSession.begin(currentWorkflow)
                }
            }.onSuccess {
                _state.update { it.copy(advancedEditor = true, loading = false) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                AdvancedEditorSession.clear()
                AppLogger.error("打开高级编辑失败", error)
                _state.update {
                    it.copy(
                        advancedEditor = false,
                        loading = false,
                        error = "打开高级编辑失败：${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun connect(address: String = state.value.serverInput) {
        AppLogger.info("请求连接服务器：$address")
        reconnectJob?.cancel()
        client.closeWebSocket()
        viewModelScope.launch {
            runOperation("连接失败") {
                val activeBridge = bridge ?: error("前端桥接尚未初始化")
                _state.update {
                    it.copy(
                        status = ConnectionStatus.CONNECTING,
                        connectionMessage = "正在检查服务器地址格式",
                        connectionStep = 1,
                        loading = true,
                        activeServer = null,
                        bridgeReady = false,
                        error = null,
                    )
                }
                val normalized = LanAddress.normalize(address)
                _state.update { it.copy(serverInput = normalized) }
                client.setServer(normalized)

                setConnectionStep(2, "地址检查通过，正在读取服务器版本和显卡信息")
                val (stats, profile) = client.probe(normalized)

                setConnectionStep(3, "服务器接口正常，正在打开 ComfyUI 网页")
                activeBridge.loadServer(normalized)

                setConnectionStep(4, "网页已经打开，正在初始化 ComfyUI 前端")
                activeBridge.awaitReady()

                setConnectionStep(5, "前端已经就绪，正在读取节点定义")
                client.features()
                require(client.objectInfo().length() > 0) { "服务器没有返回节点定义" }

                setConnectionStep(6, "节点定义正常，正在保存连接并同步数据")
                preferences.saveServer(profile)
                _state.update {
                    it.copy(
                        status = ConnectionStatus.CONNECTED,
                        connectionMessage = "已连接 ${profile.name}",
                        connectionStep = it.connectionTotalSteps,
                        activeServer = profile,
                        systemStats = stats,
                        bridgeReady = true,
                        loading = false,
                    )
                }
                openSocket()
                refreshAll()
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        client.closeWebSocket()
        _state.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                connectionMessage = "已断开",
                connectionStep = 0,
                activeServer = null,
                systemStats = null,
                workflows = emptyList(),
                selectedWorkflow = null,
                fields = emptyList(),
                jobs = emptyList(),
                results = emptyList(),
                nodeProblems = emptyMap(),
                activeJobId = null,
                currentExecutingNodeId = null,
                generationProgress = null,
                generationMessage = "",
                bridgeReady = false,
            )
        }
    }

    fun scanLan() {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, error = null) }
            runCatching { scanner.scan() }
                .onSuccess { found ->
                    _state.update { it.copy(discoveredServers = found, scanning = false, notice = "发现 ${found.size} 台 ComfyUI") }
                }
                .onFailure { error -> _state.update { it.copy(scanning = false, error = "扫描失败：${error.message}") } }
        }
    }

    fun refreshAll() {
        if (_state.value.status != ConnectionStatus.CONNECTED) return
        viewModelScope.launch {
            coroutineScope {
                listOf(
                    async { refreshStatsInternal() },
                    async { refreshWorkflowsInternal() },
                    async { refreshTasksInternal() },
                    async { refreshResultsInternal() },
                ).awaitAll()
            }
        }
    }

    fun refreshOrReconnect() {
        val current = _state.value
        if (current.loading || current.status == ConnectionStatus.CONNECTING) return
        val address = current.activeServer?.baseUrl ?: current.serverInput
        if (current.status != ConnectionStatus.CONNECTED) {
            connect(address)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(connectionMessage = "正在检查服务器连接", error = null) }
            val stats = runCatching { client.systemStats() }.getOrElse {
                _state.update {
                    it.copy(
                        status = ConnectionStatus.RECONNECTING,
                        connectionMessage = "刷新失败，正在重新连接",
                    )
                }
                connect(address)
                return@launch
            }
            _state.update {
                it.copy(
                    status = ConnectionStatus.CONNECTED,
                    connectionMessage = "已连接 ${it.activeServer?.name.orEmpty()}",
                    systemStats = stats,
                )
            }
            refreshAll()
        }
    }

    fun selectWorkflow(entry: WorkflowEntry) {
        if (entry.isDirectory) return
        AppLogger.info("加载工作流：${entry.path}")
        parameterRefreshJob?.cancel()
        viewModelScope.launch {
            runOperation("工作流加载失败") {
                _state.update { it.copy(loading = true, error = null, selectedWorkflow = null, fields = emptyList()) }
                val raw = client.readWorkflow(entry.path)
                val manifest = (bridge ?: error("前端桥接不可用")).loadWorkflow(raw)
                val document = WorkflowDocument(entry, raw, manifest.fields, manifest.nodes)
                _state.update {
                    it.copy(
                        selectedWorkflow = document,
                        fields = manifest.fields,
                        loading = false,
                        nodeProblems = emptyMap(),
                        notice = "已加载 ${entry.name}",
                    )
                }
                preferences.setRecentWorkflow(entry.path)
            }
        }
    }

    fun updateField(key: String, value: String) {
        val changedField = _state.value.fields.firstOrNull { it.key == key }
        val refreshesWorkflow = changedField?.refreshesWorkflow == true
        _state.update { ui ->
            ui.copy(
                fields = ui.fields.map { field ->
                    if (field.key != key) field else field.copy(
                        displayValue = value,
                        valueJson = valueJson(field.kind, value),
                    )
                },
                nodeProblems = changedField?.nodeId?.let { ui.nodeProblems - it } ?: ui.nodeProblems,
            )
        }
        if (refreshesWorkflow) refreshParametersAfterWorkflowSwitch()
    }

    fun setFieldVisibility(key: String, visible: Boolean) = updateFieldLayout(key) { it.copy(visible = visible) }
    fun setFieldSection(key: String, section: ParameterSection) = updateFieldLayout(key) { it.copy(section = section) }
    fun renameField(key: String, label: String) = updateFieldLayout(key) { it.copy(label = label.ifBlank { it.name }) }

    fun moveField(key: String, direction: Int) {
        _state.update { ui ->
            val sorted = ui.fields.sortedBy { it.order }.toMutableList()
            val index = sorted.indexOfFirst { it.key == key }
            val target = index + direction
            if (index !in sorted.indices || target !in sorted.indices) return@update ui
            val first = sorted[index]
            val second = sorted[target]
            sorted[index] = first.copy(order = second.order)
            sorted[target] = second.copy(order = first.order)
            ui.copy(fields = sorted.sortedWith(compareBy<ParameterField> { it.section.ordinal }.thenBy { it.order }))
        }
    }

    fun uploadField(field: ParameterField, uri: Uri) {
        viewModelScope.launch {
            runOperation("文件上传失败") {
                _state.update { it.copy(loading = true) }
                val resolver = app.contentResolver
                var size = -1L
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) null else {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                } ?: "upload_${System.currentTimeMillis()}"
                val mime = resolver.getType(uri)
                val subfolder = "ComfyUIMobile/${UUID.randomUUID()}"
                val result = client.upload(name, mime, size, { resolver.openInputStream(uri) ?: error("无法读取所选文件") }, subfolder)
                updateField(field.key, listOf(result.subfolder, result.name).filter { it.isNotBlank() }.joinToString("/"))
                _state.update { it.copy(loading = false, notice = "已上传 ${result.name}") }
            }
        }
    }

    fun finishAdvancedEditor(saved: Boolean) {
        val document = _state.value.selectedWorkflow
        _state.update { it.copy(advancedEditor = false, loading = saved && document != null) }
        if (!saved || document == null) {
            AdvancedEditorSession.clear()
            return
        }
        val raw = AdvancedEditorSession.consumeOutput()
        if (raw.isNullOrBlank()) {
            _state.update { it.copy(loading = false, error = "高级编辑没有返回工作流内容") }
            return
        }
        viewModelScope.launch {
            runCatching {
                bridgeOperationMutex.withLock {
                    val activeBridge = bridge ?: error("前端桥接不可用")
                    raw to activeBridge.loadWorkflow(raw)
                }
            }.onSuccess { (raw, manifest) ->
                _state.update {
                    it.copy(
                        selectedWorkflow = document.copy(rawJson = raw, fields = manifest.fields, nodes = manifest.nodes),
                        fields = manifest.fields,
                        nodeProblems = emptyMap(),
                        loading = false,
                        notice = "已关闭 ComfyUI 网页并刷新工作流参数",
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                AppLogger.error("高级编辑同步失败", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "高级编辑同步失败：${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun invokeSeedAction(nodeId: String, actionToken: String, successMessage: String) {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("种子操作失败") {
                val activeBridge = bridge ?: error("前端桥接不可用")
                val raw = activeBridge.invokeWidgetButton(nodeId, actionToken)
                val manifest = activeBridge.loadWorkflow(raw)
                _state.update {
                    it.copy(
                        selectedWorkflow = document.copy(rawJson = raw, fields = manifest.fields, nodes = manifest.nodes),
                        fields = manifest.fields,
                        nodeProblems = emptyMap(),
                        notice = successMessage,
                    )
                }
            }
        }
    }

    fun removeServer(baseUrl: String) {
        viewModelScope.launch { preferences.removeServer(baseUrl) }
    }

    fun generate() {
        if (
            _state.value.generating || _state.value.loading ||
            generationJob?.isActive == true || workflowSaveJob?.isActive == true
        ) return
        val workflow = _state.value.selectedWorkflow ?: return
        AppLogger.info("开始提交生成：${workflow.entry.path}")
        _state.update {
            it.copy(
                generating = true,
                error = null,
                currentExecutingNodeId = null,
                generationProgress = null,
                generationMessage = "正在整理工作流参数",
            )
        }
        generationJob = viewModelScope.launch {
            runOperation("提交生成失败") {
                val generated = bridgeOperationMutex.withLock {
                    (bridge ?: error("前端桥接不可用")).buildPrompt(_state.value.fields)
                }
                val response = try {
                    client.queuePrompt(
                        generated.promptJson,
                        generated.workflowJson,
                        clientId,
                        workflow.entry.path,
                        workflow.entry.name,
                    )
                } catch (error: PromptSubmissionException) {
                    _state.update { it.copy(nodeProblems = error.nodeProblems) }
                    throw error
                }
                val submitted = _state.value.submittedJobIds + response.promptId
                preferences.saveSubmittedJobs(submitted)
                var history = _state.value.promptHistory
                _state.value.fields
                    .filter { it.kind == ParameterKind.MULTILINE && it.nodeType.contains("TextEncode", true) }
                    .forEach { history = PromptHistory.add(history, it.displayValue) }
                preferences.savePromptHistory(history)
                _state.update {
                    it.copy(
                        submittedJobIds = submitted,
                        promptHistory = history,
                        generating = false,
                        nodeProblems = emptyMap(),
                        activeJobId = response.promptId,
                        currentExecutingNodeId = null,
                        generationProgress = 0f,
                        generationMessage = "已经加入队列，等待服务器执行",
                        notice = "已加入队列：${response.promptId.take(8)}",
                    )
                }
                AppLogger.info("生成任务已加入队列：${response.promptId}")
                startMonitor(response.promptId, workflow.entry.name)
                refreshTasksInternal()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (generationJob === job) generationJob = null
            }
        }
    }

    fun saveWorkflow(force: Boolean = false) {
        if (workflowSaveJob?.isActive == true || generationJob?.isActive == true || _state.value.generating) return
        val document = _state.value.selectedWorkflow ?: return
        _state.update {
            it.copy(
                loading = true,
                workflowOverwriteRequired = false,
                workflowOverwriteReason = "",
                error = null,
            )
        }
        workflowSaveJob = viewModelScope.launch {
            runOperation("工作流保存失败") {
                val current = client.listWorkflows().firstOrNull { it.path == document.entry.path }
                if (!force && current != null) {
                    val changed = WorkflowPolicy.hasModifiedConflict(document.entry.modified, current.modified)
                    _state.update {
                        it.copy(
                            loading = false,
                            workflowOverwriteRequired = true,
                            workflowOverwriteReason = if (changed) {
                                "服务器上的同名工作流已被其他设备修改。强制覆盖会丢失服务器上的改动，是否继续？"
                            } else {
                                "服务器已有同名工作流。是否用当前参数和工作流强制覆盖服务器文件？"
                            },
                        )
                    }
                    return@runOperation
                }
                val workflowJson = bridgeOperationMutex.withLock {
                    (bridge ?: error("前端桥接不可用")).syncWorkflow(_state.value.fields)
                }
                val saved = client.writeWorkflow(document.entry.path, workflowJson, overwrite = current != null)
                val updated = document.copy(entry = saved, rawJson = workflowJson, fields = _state.value.fields)
                _state.update {
                    it.copy(
                        selectedWorkflow = updated,
                        loading = false,
                        workflowOverwriteRequired = false,
                        workflowOverwriteReason = "",
                        notice = "工作流已保存到服务器",
                    )
                }
                refreshWorkflowsInternal()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (workflowSaveJob === job) workflowSaveJob = null
            }
        }
    }

    fun saveWorkflowAs(name: String, folder: String) {
        if (workflowSaveJob?.isActive == true || generationJob?.isActive == true || _state.value.generating) return
        val document = _state.value.selectedWorkflow ?: return
        _state.update { it.copy(loading = true, error = null) }
        workflowSaveJob = viewModelScope.launch {
            runOperation("工作流另存失败") {
                val fileName = WorkflowPath.fileName(name)
                val destination = "${WorkflowPath.folder(folder)}/$fileName"
                require(destination != document.entry.path) { "另存名称不能与当前工作流相同" }
                require(client.listWorkflows().none { it.path == destination }) { "同名工作流已存在，请换一个名称" }

                val workflowJson = bridgeOperationMutex.withLock {
                    (bridge ?: error("前端桥接不可用")).syncWorkflow(_state.value.fields)
                }
                val savedJson = JSONObject(workflowJson)
                    .put("id", UUID.randomUUID().toString())
                    .put("revision", 0)
                    .toString()
                val saved = client.writeWorkflow(destination, savedJson, overwrite = false)
                val updated = document.copy(
                    entry = saved,
                    rawJson = savedJson,
                    fields = _state.value.fields,
                )
                preferences.setRecentWorkflow(saved.path)
                _state.update {
                    it.copy(
                        selectedWorkflow = updated,
                        loading = false,
                        nodeProblems = emptyMap(),
                        notice = "已另存为 $fileName",
                    )
                }
                refreshWorkflowsInternal()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (workflowSaveJob === job) workflowSaveJob = null
            }
        }
    }

    fun dismissWorkflowOverwrite() {
        _state.update { it.copy(workflowOverwriteRequired = false, workflowOverwriteReason = "") }
    }

    fun duplicateWorkflow(name: String) {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("复制工作流失败") {
                val folder = document.entry.path.substringBeforeLast('/', "workflows")
                val fileName = WorkflowPath.fileName(name)
                val json = JSONObject(document.rawJson)
                    .put("id", UUID.randomUUID().toString())
                    .put("revision", 0)
                val entry = client.writeWorkflow("$folder/$fileName", json.toString(), overwrite = false)
                refreshWorkflowsInternal()
                selectWorkflow(entry)
            }
        }
    }

    fun renameWorkflow(name: String) {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("工作流改名失败") {
                val folder = document.entry.path.substringBeforeLast('/', "workflows")
                val fileName = WorkflowPath.fileName(name)
                val moved = client.moveWorkflow(document.entry.path, "$folder/$fileName")
                _state.update { it.copy(selectedWorkflow = document.copy(entry = moved), notice = "已改名为 $fileName") }
                preferences.setRecentWorkflow(moved.path, replacedPath = document.entry.path)
                refreshWorkflowsInternal()
            }
        }
    }

    fun moveWorkflow(folder: String) {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("移动工作流失败") {
                val destination = "${WorkflowPath.folder(folder)}/${document.entry.name}"
                val moved = client.moveWorkflow(document.entry.path, destination)
                _state.update { it.copy(selectedWorkflow = document.copy(entry = moved), notice = "已移动到 ${WorkflowPath.folder(folder)}") }
                preferences.setRecentWorkflow(moved.path, replacedPath = document.entry.path)
                refreshWorkflowsInternal()
            }
        }
    }

    fun deleteWorkflow() {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("删除工作流失败") {
                client.deleteWorkflow(document.entry.path)
                preferences.removeRecentWorkflow(document.entry.path)
                _state.update { it.copy(selectedWorkflow = null, fields = emptyList(), notice = "已删除 ${document.entry.name}") }
                refreshWorkflowsInternal()
            }
        }
    }

    fun importWorkflow(uri: Uri, filename: String, mimeType: String?) {
        viewModelScope.launch {
            runOperation("导入工作流失败") {
                _state.update { it.copy(loading = true, error = null) }
                val extension = filename.substringAfterLast('.', "").lowercase()
                val isImage = mimeType.orEmpty().startsWith("image/") || extension in setOf("png", "webp", "avif")
                val raw = if (isImage) {
                    if (extension == "png" || mimeType.equals("image/png", ignoreCase = true)) {
                        withContext(Dispatchers.IO) {
                            app.contentResolver.openInputStream(uri)?.use { WorkflowImageReader.readPngWorkflow(it) }
                                ?: error("无法读取所选图片")
                        }
                    } else {
                        (bridge ?: error("前端桥接不可用")).extractWorkflowFromImage(uri, mimeType, filename)
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("无法读取所选文件")
                    }
                }
                val json = JSONObject(raw)
                require(json.optJSONArray("nodes") != null) { "不是 ComfyUI 画布工作流 JSON" }
                val manifest = (bridge ?: error("前端桥接不可用")).loadWorkflow(json.toString())
                val sourceName = filename.substringAfterLast('/').substringAfterLast('\\')
                val targetName = if (isImage) sourceName.substringBeforeLast('.', sourceName) else sourceName
                val safeName = WorkflowPath.fileName(targetName)
                val existingPaths = client.listWorkflows().mapTo(mutableSetOf()) { it.path }
                val baseName = safeName.substringBeforeLast(".json", safeName)
                var candidateName = safeName
                var copyNumber = 2
                while ("workflows/$candidateName" in existingPaths) {
                    candidateName = "$baseName-$copyNumber.json"
                    copyNumber += 1
                }
                val entry = client.writeWorkflow("workflows/$candidateName", json.toString(), overwrite = false)
                refreshWorkflowsInternal()
                _state.update {
                    it.copy(
                        selectedWorkflow = WorkflowDocument(entry, json.toString(), manifest.fields, manifest.nodes),
                        fields = manifest.fields,
                        loading = false,
                        nodeProblems = emptyMap(),
                        notice = "已从${if (isImage) "图片" else "文件"}导入 $candidateName",
                    )
                }
                preferences.setRecentWorkflow(entry.path)
            }
        }
    }

    fun currentWorkflowExport(): Pair<String, String>? = _state.value.selectedWorkflow?.let { it.entry.name to it.rawJson }

    fun refreshTasks() = viewModelScope.launch { refreshTasksInternal() }
    fun refreshResults() = viewModelScope.launch { refreshResultsInternal() }
    fun refreshLocalResults() = viewModelScope.launch {
        val local = localResultCache.load()
        _state.update { it.copy(localResults = local) }
    }

    fun onLocalResultsSaved(count: Int, failed: Boolean) = viewModelScope.launch {
        val local = localResultCache.load()
        _state.update {
            it.copy(
                localResults = local,
                generationMessage = if (failed) "生成完成，但部分本地作品保存失败" else "生成完成，本地已保存 $count 项",
                notice = if (failed) "部分本地作品保存失败，可保持连接后重新生成" else "本地已完整保存 $count 项",
            )
        }
    }

    fun cancelJob(job: JobSummary) {
        viewModelScope.launch {
            runOperation("取消任务失败") {
                client.cancel(job)
                stopMonitor(job.id)
                if (_state.value.activeJobId == job.id) {
                    _state.update {
                        it.copy(
                            activeJobId = null,
                            currentExecutingNodeId = null,
                            generationProgress = null,
                            generationMessage = "任务已取消",
                            notice = "任务已取消：${job.id.take(8)}",
                        )
                    }
                }
                delay(250)
                refreshTasksInternal()
            }
        }
    }

    fun clearPendingJobs() {
        viewModelScope.launch {
            runOperation("清空队列失败") {
                client.clearPending()
                refreshTasksInternal()
            }
        }
    }

    fun setAutoSaveResults(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAutoSaveResults(enabled)
            _state.update { it.copy(autoSaveResults = enabled) }
        }
    }

    fun setLoggingEnabled(enabled: Boolean) {
        AppLogger.setEnabled(app, enabled)
        _state.update {
            it.copy(
                loggingEnabled = enabled,
                notice = if (enabled) "诊断日志已开启" else "诊断日志已关闭",
            )
        }
    }

    fun diagnosticLog(): String = AppLogger.read()

    fun clearDiagnosticLog() {
        AppLogger.clear()
        _state.update { it.copy(notice = "诊断日志已清空") }
    }

    fun reportDiagnosticLogExport(success: Boolean) {
        _state.update {
            if (success) it.copy(notice = "诊断日志已导出")
            else it.copy(error = "诊断日志导出失败")
        }
    }

    fun removePromptHistory(value: String) {
        viewModelScope.launch {
            val updated = _state.value.promptHistory.filterNot { it == value }
            preferences.savePromptHistory(updated)
            _state.update { it.copy(promptHistory = updated) }
        }
    }

    fun clearPromptHistory() {
        viewModelScope.launch {
            preferences.savePromptHistory(emptyList())
            _state.update { it.copy(promptHistory = emptyList()) }
        }
    }

    fun toggleCacheOutput(node: WorkflowNode) {
        if (!node.isOutput) return
        viewModelScope.launch {
            val serverUrl = client.serverUrl()
            val current = _state.value.cacheOutputRules
            val existing = current.firstOrNull {
                it.serverUrl == serverUrl && it.nodeType == node.type
            }
            val updated = if (existing == null) {
                current.filterNot { it.serverUrl == serverUrl && it.nodeType == node.type } + CacheOutputRule(
                    serverUrl = serverUrl,
                    nodeTitle = node.title,
                    nodeType = node.type,
                )
            } else {
                current - existing
            }
            preferences.saveCacheOutputRules(updated)
            _state.update {
                it.copy(
                    cacheOutputRules = updated,
                    notice = if (existing == null) {
                        "已将 ${node.title} 加入全工作流保存白名单"
                    } else {
                        "已将 ${node.title} 移出全工作流保存白名单"
                    },
                )
            }
        }
    }

    fun setCacheRuleEnabled(rule: CacheOutputRule, enabled: Boolean) {
        viewModelScope.launch {
            val updated = _state.value.cacheOutputRules.map { if (it == rule) it.copy(enabled = enabled) else it }
            preferences.saveCacheOutputRules(updated)
            _state.update { it.copy(cacheOutputRules = updated) }
        }
    }

    fun removeCacheRule(rule: CacheOutputRule) {
        viewModelScope.launch {
            val updated = _state.value.cacheOutputRules - rule
            preferences.saveCacheOutputRules(updated)
            _state.update { it.copy(cacheOutputRules = updated) }
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            runOperation("删除本地作品失败") {
                val clearedAt = System.currentTimeMillis()
                preferences.setCacheClearedAt(clearedAt)
                localResultCache.clear()
                _state.update {
                    it.copy(
                        localResults = emptyList(),
                        cacheClearedAt = clearedAt,
                        notice = "本地作品已删除，旧任务不会重新下载",
                    )
                }
            }
        }
    }

    fun saveResult(media: ResultMedia) {
        saveResults(listOf(media))
    }

    fun saveResultWithFeedback(media: ResultMedia, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val verb = if (media.source == com.local.comfyuimobile.model.ResultSource.CLOUD) "下载" else "保存"
            runCatching { saveToMediaStore(media) }
                .onSuccess {
                    val message = "${verb}完成"
                    _state.update { it.copy(notice = "$message：${media.filename}") }
                    onComplete(message)
                }
                .onFailure { error ->
                    val message = "${verb}失败：${error.message ?: error.javaClass.simpleName}"
                    _state.update { it.copy(error = message) }
                    onComplete(message)
                }
        }
    }

    fun saveResults(media: Collection<ResultMedia>) {
        viewModelScope.launch {
            val items = media.distinctBy(ResultMedia::stableKey)
            val verb = if (items.any { it.source == com.local.comfyuimobile.model.ResultSource.CLOUD }) "下载" else "保存"
            runOperation("${verb}结果失败") {
                var succeeded = 0
                var failed = 0
                items.forEach { item ->
                    runCatching { saveToMediaStore(item) }
                        .onSuccess { succeeded += 1 }
                        .onFailure { failed += 1 }
                }
                _state.update {
                    it.copy(
                        notice = if (failed == 0) "已${verb} $succeeded 项" else "已${verb} $succeeded 项，$failed 项失败",
                    )
                }
            }
        }
    }

    fun deleteLocalResults(media: Collection<ResultMedia>) {
        viewModelScope.launch {
            runOperation("删除本地作品失败") {
                val deleted = localResultCache.remove(media.filter { it.source == com.local.comfyuimobile.model.ResultSource.LOCAL })
                val remaining = localResultCache.load()
                _state.update { it.copy(localResults = remaining, notice = "已删除 $deleted 项本地作品") }
            }
        }
    }

    fun toggleResultFavorite(media: ResultMedia) {
        viewModelScope.launch {
            val key = media.stableKey()
            val updated = _state.value.favoriteResultKeys.toMutableSet().apply {
                if (!add(key)) remove(key)
            }.toSet()
            preferences.saveFavoriteResultKeys(updated)
            _state.update { it.copy(favoriteResultKeys = updated) }
        }
    }

    fun shareResult(media: ResultMedia) {
        viewModelScope.launch {
            runOperation("分享结果失败") {
                val file = media.localPath?.let(::File)?.takeIf { it.isFile } ?: run {
                    val dir = File(app.cacheDir, "shared").apply { mkdirs() }
                    File(dir, media.filename).also { client.downloadTo(media.url, it.outputStream()) }
                }
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType(media)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(Intent.createChooser(intent, "分享生成结果").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun openResult(media: ResultMedia) {
        val localFile = media.localPath?.let(::File)?.takeIf { it.isFile }
        val uri = localFile?.let { FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", it) }
            ?: Uri.parse(media.url)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setDataAndType(uri, mimeType(media))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { app.startActivity(intent) }
            .onFailure { _state.update { state -> state.copy(error = "无法打开原文件：${it.message}") } }
    }

    fun checkUpdate(manual: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!manual && now - lastUpdateCheck < 24 * 60 * 60 * 1_000L) return
        viewModelScope.launch {
            runOperation("检查更新失败") {
                val info = updates.checkLatest()
                preferences.setLastUpdateCheck(now)
                lastUpdateCheck = now
                _state.update {
                    it.copy(
                        updateInfo = info,
                        notice = if (info == null && manual) "已是最新版本" else if (info != null) "发现新版本 ${info.tag}" else it.notice,
                    )
                }
            }
        }
    }

    fun downloadUpdate() {
        val info = _state.value.updateInfo ?: return
        viewModelScope.launch {
            runOperation("更新下载失败") {
                val result = updates.enqueue(info)
                _state.update { it.copy(notice = "已通过${result.source}开始下载") }
            }
        }
    }

    private fun openSocket() {
        client.openWebSocket(
            clientId = clientId,
            onOpen = {
                reconnectJob?.cancel()
                _state.update { it.copy(status = ConnectionStatus.CONNECTED, connectionMessage = "已连接 ${it.activeServer?.name.orEmpty()}") }
            },
            onMessage = ::handleSocketMessage,
            onFailure = { scheduleReconnect() },
        )
    }

    private fun scheduleReconnect() {
        if (_state.value.activeServer == null || reconnectJob?.isActive == true) return
        reconnectJob = viewModelScope.launch {
            _state.update { it.copy(status = ConnectionStatus.RECONNECTING, connectionMessage = "连接中断，正在重连") }
            for (seconds in listOf(1L, 2L, 5L, 10L, 30L)) {
                delay(seconds * 1_000)
                if (!isActive || _state.value.activeServer == null) return@launch
                val ok = runCatching { client.systemStats() }.isSuccess
                if (ok) {
                    openSocket()
                    refreshAll()
                    return@launch
                }
            }
            _state.update { it.copy(status = ConnectionStatus.ERROR, connectionMessage = "服务器离线") }
        }
    }

    private fun handleSocketMessage(message: JSONObject) {
        val type = message.optString("type")
        val data = message.optJSONObject("data") ?: JSONObject()
        when (type) {
            "execution_start" -> {
                val id = data.optString("prompt_id")
                if (id.isNotBlank()) {
                    updateJob(id) { it.copy(state = JobState.RUNNING, progress = 0f, currentNode = null) }
                    if (id in _state.value.submittedJobIds) {
                        visibleNodeJob?.cancel()
                        visibleNodeChangedAt = 0L
                        _state.update {
                            it.copy(
                                activeJobId = id,
                                currentExecutingNodeId = null,
                                generationProgress = 0f,
                                generationMessage = "服务器已经开始生成",
                            )
                        }
                    }
                }
            }
            "status" -> {
                val remaining = data.optJSONObject("status")?.optJSONObject("exec_info")?.optInt("queue_remaining") ?: 0
                _state.update { it.copy(queueRemaining = remaining) }
                viewModelScope.launch { refreshTasksInternal() }
            }
            "progress" -> {
                val id = data.optString("prompt_id")
                val value = data.optDouble("value")
                val max = data.optDouble("max")
                if (id.isNotBlank() && max > 0) {
                    val progress = (value / max).toFloat()
                    updateJob(id) { it.copy(progress = progress) }
                    updateMonitor(id, (progress * 100).toInt(), null)
                    if (id in _state.value.submittedJobIds) {
                        _state.update {
                            it.copy(
                                activeJobId = id,
                                generationProgress = progress,
                                generationMessage = "正在生成 ${(progress * 100).toInt()}%",
                            )
                        }
                    }
                }
            }
            "progress_state" -> {
                ProgressStateParser.parse(data)?.let { update ->
                    val nodeId = resolveVisibleNode(update.nodeId)
                    updateJob(update.promptId) { it.copy(progress = update.progress, currentNode = nodeId, state = JobState.RUNNING) }
                    updateMonitor(update.promptId, (update.progress * 100).toInt(), nodeId)
                    if (update.promptId in _state.value.submittedJobIds) {
                        showVisibleExecutingNode(update.promptId, nodeId, update.progress)
                    }
                }
            }
            "executing" -> {
                val id = data.optTextOrEmpty("prompt_id")
                val runtimeNode = data.optTextOrEmpty("display_node_id")
                    .ifBlank { data.optTextOrEmpty("display_node") }
                    .ifBlank { data.optTextOrEmpty("node_id") }
                    .ifBlank { data.optTextOrEmpty("node") }
                val node = resolveVisibleNode(runtimeNode).orEmpty()
                if (id.isNotBlank()) {
                    val previousState = _state.value.jobs.firstOrNull { it.id == id }?.state
                    val wasFailed = previousState in setOf(JobState.ERROR, JobState.CANCELLED)
                    updateJob(id) {
                        when {
                            node.isNotBlank() -> it.copy(currentNode = node, state = JobState.RUNNING)
                            wasFailed -> it.copy(currentNode = null)
                            else -> it.copy(currentNode = null, state = JobState.SUCCESS, progress = 1f)
                        }
                    }
                    if (node.isNotBlank()) updateMonitor(id, -1, node) else updateMonitor(id, 100, null)
                    if (id in _state.value.submittedJobIds) {
                        if (node.isBlank()) {
                            if (!wasFailed) {
                                finishVisibleExecution(id)
                            }
                        } else {
                            showVisibleExecutingNode(id, node)
                        }
                    }
                    if (node.isBlank()) {
                        // ComfyUI 在最终 node=null 之前才把历史写入磁盘；稍后刷新才能取得完整输出。
                        viewModelScope.launch {
                            delay(250)
                            refreshTasksInternal()
                            refreshResultsInternal()
                        }
                    }
                }
            }
            "executed" -> Unit
            "execution_success" -> {
                val id = data.optTextOrEmpty("prompt_id")
                if (id.isNotBlank()) {
                    updateJob(id) { it.copy(state = JobState.SUCCESS, progress = 1f, currentNode = null) }
                    if (id in _state.value.submittedJobIds) {
                        finishVisibleExecution(id)
                    }
                }
                // 某些版本的 execution_success 早于历史落盘，保留延迟刷新作为兼容兜底。
                viewModelScope.launch {
                    delay(250)
                    refreshTasksInternal()
                    refreshResultsInternal()
                }
            }
            "execution_error", "execution_interrupted" -> {
                val id = data.optTextOrEmpty("prompt_id")
                val nodeId = resolveVisibleNode(data.optTextOrEmpty("node_id")).orEmpty()
                val detail = data.optTextOrEmpty("exception_message").ifBlank { if (type == "execution_interrupted") "任务已中断" else "服务器执行失败" }
                if (id.isNotBlank()) {
                    updateJob(id) { it.copy(state = if (type == "execution_interrupted") JobState.CANCELLED else JobState.ERROR, currentNode = nodeId.ifBlank { null }, message = detail) }
                    if (id in _state.value.submittedJobIds) {
                        _state.update {
                            it.copy(
                                activeJobId = id,
                                currentExecutingNodeId = nodeId.ifBlank { null },
                                generationProgress = null,
                                generationMessage = "生成失败：$detail",
                                error = "生成失败：$detail",
                            )
                        }
                    }
                }
                viewModelScope.launch { refreshTasksInternal(); refreshResultsInternal() }
            }
        }
    }

    private suspend fun refreshStatsInternal() {
        runCatching { client.systemStats() }.onSuccess { stats ->
            val updatedProfile = _state.value.activeServer?.copy(lastSeen = System.currentTimeMillis(), comfyVersion = stats.comfyVersion)
            _state.update { it.copy(systemStats = stats, activeServer = updatedProfile ?: it.activeServer) }
            if (updatedProfile != null) preferences.saveServer(updatedProfile)
        }
    }

    private suspend fun refreshWorkflowsInternal() {
        runCatching { client.listWorkflows() }.onSuccess { entries -> _state.update { it.copy(workflows = entries) } }
    }

    private suspend fun refreshTasksInternal() {
        runCatching {
            val existing = _state.value.jobs.associateBy { it.id }
            val live = client.queue()
            val history = client.historyJobs()
            val submitted = _state.value.submittedJobIds
            (live + history).distinctBy { it.id }.map { fresh ->
                val previous = existing[fresh.id]
                fresh.copy(
                    progress = if (fresh.state in setOf(JobState.RUNNING, JobState.PENDING)) previous?.progress else fresh.progress,
                    currentNode = if (fresh.state == JobState.RUNNING) previous?.currentNode else null,
                    submittedByApp = fresh.id in submitted,
                )
            }
        }.onSuccess { jobs ->
            val activeAppJobs = jobs.filter {
                it.submittedByApp && it.state in setOf(JobState.RUNNING, JobState.PENDING)
            }
            val currentActiveId = _state.value.activeJobId
            val recovered = activeAppJobs.firstOrNull { it.id == currentActiveId }
                ?: activeAppJobs.firstOrNull { it.state == JobState.RUNNING }
                ?: activeAppJobs.firstOrNull()
            _state.update { ui ->
                if (recovered == null) {
                    ui.copy(jobs = jobs)
                } else {
                    val resolvedNode = ExecutionNodeResolver.resolve(recovered.currentNode, ui.selectedWorkflow?.nodes.orEmpty())
                    ui.copy(
                        jobs = jobs,
                        activeJobId = recovered.id,
                        currentExecutingNodeId = resolvedNode,
                        generationProgress = recovered.progress,
                        generationMessage = when {
                            recovered.state == JobState.PENDING -> "已接管此前提交的任务，正在等待服务器执行"
                            resolvedNode != null -> {
                                val title = ui.selectedWorkflow?.nodes?.firstOrNull { it.id == resolvedNode }?.title
                                "已接管运行中的任务：${title ?: "部件 $resolvedNode"}"
                            }
                            else -> "已接管此前提交的运行中任务"
                        },
                    )
                }
            }
            activeAppJobs.forEach { job ->
                startMonitor(
                    job.id,
                    job.workflowName.ifBlank {
                        _state.value.selectedWorkflow?.entry?.name ?: "ComfyUI 工作流"
                    },
                )
            }
        }
    }

    private suspend fun refreshResultsInternal() {
        runCatching {
            val history = client.history()
            ResultParser.parse(client.serverUrl(), history)
        }.onSuccess { results ->
            _state.update { it.copy(results = results) }
        }
    }

    private suspend fun saveToMediaStore(media: ResultMedia): Uri = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val collection = if (media.kind == MediaKind.IMAGE) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, media.filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(media))
            if (Build.VERSION.SDK_INT >= 29) {
                val folder = if (media.kind == MediaKind.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/ComfyUIMobile")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("无法创建媒体文件")
        try {
            val output = resolver.openOutputStream(uri) ?: error("无法写入媒体文件")
            val localFile = media.localPath?.let(::File)?.takeIf { it.isFile }
            if (localFile != null) {
                output.use { target -> localFile.inputStream().use { source -> source.copyTo(target) } }
            } else {
                client.downloadTo(media.url, output)
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun startMonitor(promptId: String, workflowName: String) {
        if (!monitoredJobIds.add(promptId)) return
        val intent = Intent(app, JobMonitorService::class.java)
            .putExtra(JobMonitorService.EXTRA_BASE_URL, client.serverUrl())
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
            .putExtra(JobMonitorService.EXTRA_WORKFLOW_NAME, workflowName)
        runCatching { ContextCompat.startForegroundService(app, intent) }
            .onFailure { error ->
                monitoredJobIds.remove(promptId)
                AppLogger.error("后台监控启动失败，任务=$promptId", error)
                _state.update {
                    it.copy(notice = "任务已提交，但后台监控启动失败：${error.message.orEmpty()}")
                }
            }
    }

    private fun updateMonitor(promptId: String, progress: Int, node: String?) {
        if (promptId !in _state.value.submittedJobIds) return
        val intent = Intent(app, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_PROGRESS)
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
            .putExtra(JobMonitorService.EXTRA_PROGRESS, progress)
            .putExtra(JobMonitorService.EXTRA_NODE, node)
        runCatching { app.startService(intent) }
            .onFailure { AppLogger.error("后台进度通知失败，任务=$promptId", it) }
    }

    private fun stopMonitor(promptId: String) {
        monitoredJobIds.remove(promptId)
        val intent = Intent(app, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_STOP)
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
        runCatching { app.startService(intent) }
            .onFailure { AppLogger.error("停止后台监控失败，任务=$promptId", it) }
    }

    private fun updateFieldLayout(key: String, transform: (ParameterField) -> ParameterField) {
        _state.update { ui -> ui.copy(fields = ui.fields.map { if (it.key == key) transform(it) else it }) }
    }

    private fun refreshParametersAfterWorkflowSwitch() {
        parameterRefreshJob?.cancel()
        parameterRefreshJob = viewModelScope.launch {
            delay(80)
            runOperation("切换工作流程失败") {
                val document = _state.value.selectedWorkflow ?: return@runOperation
                val activeBridge = bridge ?: error("前端桥接不可用")
                val snapshot = _state.value.fields
                _state.update { it.copy(loading = true, error = null) }
                val raw = activeBridge.syncWorkflow(snapshot)
                val manifest = activeBridge.loadWorkflow(raw)
                _state.update { ui ->
                    if (ui.selectedWorkflow?.entry?.path != document.entry.path) {
                        ui.copy(loading = false)
                    } else {
                        ui.copy(
                            selectedWorkflow = document.copy(rawJson = raw, fields = manifest.fields, nodes = manifest.nodes),
                            fields = manifest.fields,
                            loading = false,
                            notice = "已切换工作流程并刷新可设置节点",
                        )
                    }
                }
            }
        }
    }

    private fun updateJob(id: String, transform: (JobSummary) -> JobSummary) {
        _state.update { ui ->
            val current = ui.jobs.firstOrNull { it.id == id } ?: JobSummary(id, JobState.RUNNING, submittedByApp = id in ui.submittedJobIds)
            ui.copy(jobs = listOf(transform(current)) + ui.jobs.filterNot { it.id == id })
        }
    }

    private fun resolveVisibleNode(runtimeNodeId: String?): String? =
        ExecutionNodeResolver.resolve(runtimeNodeId, _state.value.selectedWorkflow?.nodes.orEmpty())

    private fun showVisibleExecutingNode(promptId: String, nodeId: String?, progress: Float? = null) {
        val resolvedNodeId = resolveVisibleNode(nodeId) ?: return
        val applyUpdate = {
            val title = _state.value.selectedWorkflow?.nodes?.firstOrNull { it.id == resolvedNodeId }?.title
            val percent = progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
            _state.update { ui ->
                if (promptId !in ui.submittedJobIds) ui else ui.copy(
                    activeJobId = promptId,
                    currentExecutingNodeId = resolvedNodeId,
                    generationProgress = progress ?: ui.generationProgress,
                    generationMessage = "正在执行：${title ?: "部件 $resolvedNodeId"}$percent",
                )
            }
            visibleNodeChangedAt = SystemClock.elapsedRealtime()
        }

        val current = _state.value.currentExecutingNodeId
        if (current == null || current == resolvedNodeId) {
            visibleNodeJob?.cancel()
            if (current == resolvedNodeId) {
                val title = _state.value.selectedWorkflow?.nodes?.firstOrNull { it.id == resolvedNodeId }?.title
                val percent = progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
                _state.update { ui ->
                    ui.copy(
                        activeJobId = promptId,
                        generationProgress = progress ?: ui.generationProgress,
                        generationMessage = "正在执行：${title ?: "部件 $resolvedNodeId"}$percent",
                    )
                }
            } else {
                applyUpdate()
            }
            return
        }

        val waitMillis = (MIN_VISIBLE_NODE_MILLIS - (SystemClock.elapsedRealtime() - visibleNodeChangedAt)).coerceAtLeast(0L)
        visibleNodeJob?.cancel()
        visibleNodeJob = viewModelScope.launch {
            delay(waitMillis)
            if (_state.value.activeJobId == promptId) applyUpdate()
        }
    }

    private fun finishVisibleExecution(promptId: String) {
        val waitMillis = (MIN_VISIBLE_NODE_MILLIS - (SystemClock.elapsedRealtime() - visibleNodeChangedAt)).coerceAtLeast(0L)
        visibleNodeJob?.cancel()
        visibleNodeJob = viewModelScope.launch {
            delay(waitMillis)
            _state.update { ui ->
                if (ui.activeJobId != promptId) ui else ui.copy(
                    currentExecutingNodeId = null,
                    generationProgress = 1f,
                    generationMessage = "生成完成，正在后台保存本地作品",
                    notice = "生成完成：${promptId.take(8)}",
                )
            }
        }
    }

    private fun JSONObject.optTextOrEmpty(name: String): String {
        val value = opt(name)
        return if (value == null || value === JSONObject.NULL || value.toString().equals("null", ignoreCase = true)) "" else value.toString()
    }

    private fun valueJson(kind: ParameterKind, value: String): String = when (kind) {
        ParameterKind.INTEGER -> value.toLongOrNull()?.toString() ?: "0"
        ParameterKind.DECIMAL -> value.toDoubleOrNull()?.toString() ?: "0.0"
        ParameterKind.BOOLEAN -> value.toBooleanStrictOrNull()?.toString() ?: "false"
        else -> JSONObject.quote(value)
    }

    private fun mimeType(media: ResultMedia): String = when (media.filename.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> if (media.kind == MediaKind.IMAGE) "image/png" else "video/*"
    }

    private suspend fun runOperation(prefix: String, block: suspend () -> Unit) {
        val operation = prefix.removeSuffix("失败")
        AppLogger.info("$operation 开始")
        runCatching { block() }
            .onSuccess { AppLogger.info("$operation 完成") }
            .onFailure { error ->
                if (error is CancellationException) throw error
                AppLogger.error(prefix, error)
                _state.update {
                    val detail = error.message ?: error.javaClass.simpleName
                    val connecting = prefix.startsWith("连接")
                    it.copy(
                        loading = false,
                        generating = false,
                        scanning = false,
                        error = "$prefix：$detail",
                        status = if (connecting) ConnectionStatus.ERROR else it.status,
                        connectionMessage = if (connecting) "第 ${it.connectionStep} 步失败：$detail" else it.connectionMessage,
                        activeServer = if (connecting) null else it.activeServer,
                        bridgeReady = if (connecting) false else it.bridgeReady,
                    )
                }
            }
    }

    private fun setConnectionStep(step: Int, message: String) {
        _state.update {
            it.copy(
                status = ConnectionStatus.CONNECTING,
                connectionStep = step.coerceIn(1, it.connectionTotalSteps),
                connectionMessage = message,
            )
        }
    }

    override fun onCleared() {
        client.closeWebSocket()
        super.onCleared()
    }

    private companion object {
        const val MIN_VISIBLE_NODE_MILLIS = 450L
    }
}
