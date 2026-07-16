package com.local.comfyuimobile

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.data.AppPreferences
import com.local.comfyuimobile.data.PromptHistory
import com.local.comfyuimobile.data.WorkflowPolicy
import com.local.comfyuimobile.data.WorkflowPath
import com.local.comfyuimobile.model.AppUiState
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
import com.local.comfyuimobile.network.ComfyClient
import com.local.comfyuimobile.network.LanAddress
import com.local.comfyuimobile.network.LanScanner
import com.local.comfyuimobile.network.ResultParser
import com.local.comfyuimobile.service.JobMonitorService
import com.local.comfyuimobile.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val preferences = AppPreferences(application)
    private val client = ComfyClient()
    private val scanner = LanScanner(application, client)
    private val updates = UpdateManager(application)
    private val clientId = UUID.randomUUID().toString()
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var bridge: ComfyBridge? = null
    private var reconnectJob: Job? = null
    private val autoSavedUrls = mutableSetOf<String>()
    @Volatile private var lastUpdateCheck: Long = 0L

    init {
        viewModelScope.launch {
            preferences.settings.collect { stored ->
                lastUpdateCheck = stored.lastUpdateCheck
                _state.update {
                    it.copy(
                        savedServers = stored.profiles,
                        promptHistory = stored.promptHistory,
                        submittedJobIds = stored.submittedJobs,
                        autoSaveResults = stored.autoSaveResults,
                        serverInput = it.activeServer?.baseUrl
                            ?: stored.activeServerUrl.ifBlank { it.serverInput },
                    )
                }
            }
        }
    }

    fun attachBridge(value: ComfyBridge) {
        bridge = value
    }

    fun setServerInput(value: String) = _state.update { it.copy(serverInput = value) }
    fun setAdvancedEditor(enabled: Boolean) = _state.update { it.copy(advancedEditor = enabled) }
    fun clearMessage() = _state.update { it.copy(error = null, notice = null) }

    fun connect(address: String = state.value.serverInput) {
        viewModelScope.launch {
            runOperation("连接失败") {
                val activeBridge = bridge ?: error("前端桥接尚未初始化")
                _state.update {
                    it.copy(
                        status = ConnectionStatus.CONNECTING,
                        connectionMessage = "正在检查地址是否为可信局域网地址",
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

    fun selectWorkflow(entry: WorkflowEntry) {
        if (entry.isDirectory) return
        viewModelScope.launch {
            runOperation("工作流加载失败") {
                _state.update { it.copy(loading = true, error = null, selectedWorkflow = null, fields = emptyList()) }
                val raw = client.readWorkflow(entry.path)
                val fields = (bridge ?: error("前端桥接不可用")).loadWorkflow(raw)
                val document = WorkflowDocument(entry, raw, fields)
                _state.update { it.copy(selectedWorkflow = document, fields = fields, loading = false, notice = "已加载 ${entry.name}") }
                preferences.setRecentWorkflow(entry.path)
            }
        }
    }

    fun updateField(key: String, value: String) {
        _state.update { ui ->
            ui.copy(fields = ui.fields.map { field ->
                if (field.key != key) field else field.copy(
                    displayValue = value,
                    valueJson = valueJson(field.kind, value),
                )
            })
        }
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

    fun finishAdvancedEditor() {
        val document = _state.value.selectedWorkflow
        if (document == null) {
            setAdvancedEditor(false)
            return
        }
        viewModelScope.launch {
            runOperation("高级编辑同步失败") {
                val activeBridge = bridge ?: error("前端桥接不可用")
                val raw = activeBridge.exportCurrentWorkflow()
                val fields = activeBridge.loadWorkflow(raw)
                _state.update { it.copy(selectedWorkflow = document.copy(rawJson = raw, fields = fields), fields = fields, advancedEditor = false) }
            }
        }
    }

    fun removeServer(baseUrl: String) {
        viewModelScope.launch { preferences.removeServer(baseUrl) }
    }

    fun generate() {
        val workflow = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("提交生成失败") {
                _state.update { it.copy(generating = true, error = null) }
                val generated = (bridge ?: error("前端桥接不可用")).buildPrompt(_state.value.fields)
                val response = client.queuePrompt(generated.promptJson, generated.workflowJson, clientId)
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
                        notice = "已加入队列：${response.promptId.take(8)}",
                    )
                }
                startMonitor(response.promptId, workflow.entry.name)
                refreshTasksInternal()
            }
        }
    }

    fun saveWorkflow(force: Boolean = false) {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("工作流保存失败") {
                _state.update { it.copy(loading = true) }
                val current = client.listWorkflows().firstOrNull { it.path == document.entry.path }
                if (!force && WorkflowPolicy.hasModifiedConflict(document.entry.modified, current?.modified)) {
                    error("桌面端已修改此工作流。请重新加载、复制为新工作流，或选择强制覆盖。")
                }
                val generated = (bridge ?: error("前端桥接不可用")).buildPrompt(_state.value.fields)
                val saved = client.writeWorkflow(document.entry.path, generated.workflowJson, overwrite = true)
                val updated = document.copy(entry = saved, rawJson = generated.workflowJson, fields = _state.value.fields)
                _state.update { it.copy(selectedWorkflow = updated, loading = false, notice = "工作流已保存") }
                refreshWorkflowsInternal()
            }
        }
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
                refreshWorkflowsInternal()
            }
        }
    }

    fun deleteWorkflow() {
        val document = _state.value.selectedWorkflow ?: return
        viewModelScope.launch {
            runOperation("删除工作流失败") {
                client.deleteWorkflow(document.entry.path)
                _state.update { it.copy(selectedWorkflow = null, fields = emptyList(), notice = "已删除 ${document.entry.name}") }
                refreshWorkflowsInternal()
            }
        }
    }

    fun importWorkflow(filename: String, raw: String) {
        viewModelScope.launch {
            runOperation("导入工作流失败") {
                val json = JSONObject(raw)
                require(json.optJSONArray("nodes") != null) { "不是 ComfyUI 画布工作流 JSON" }
                val safeName = WorkflowPath.fileName(filename.substringAfterLast('/').substringAfterLast('\\'))
                client.writeWorkflow("workflows/$safeName", json.toString(), overwrite = false)
                refreshWorkflowsInternal()
                _state.update { it.copy(notice = "已导入 $safeName") }
            }
        }
    }

    fun currentWorkflowExport(): Pair<String, String>? = _state.value.selectedWorkflow?.let { it.entry.name to it.rawJson }

    fun refreshTasks() = viewModelScope.launch { refreshTasksInternal() }
    fun refreshResults() = viewModelScope.launch { refreshResultsInternal() }

    fun cancelJob(job: JobSummary) {
        viewModelScope.launch {
            runOperation("取消任务失败") {
                client.cancel(job)
                stopMonitor(job.id)
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

    fun saveResult(media: ResultMedia) {
        viewModelScope.launch {
            runOperation("保存结果失败") {
                saveToMediaStore(media)
                _state.update { it.copy(notice = "已保存 ${media.filename}") }
            }
        }
    }

    fun shareResult(media: ResultMedia) {
        viewModelScope.launch {
            runOperation("分享结果失败") {
                val dir = File(app.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, media.filename)
                client.downloadTo(media.url, file.outputStream())
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(media.url)).apply {
            setDataAndType(Uri.parse(media.url), mimeType(media))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                updates.enqueue(info)
                _state.update { it.copy(notice = "更新已开始下载") }
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
                }
            }
            "executing" -> {
                val id = data.optString("prompt_id")
                val node = data.optString("node")
                if (id.isNotBlank()) {
                    updateJob(id) { it.copy(currentNode = node.ifBlank { null }, state = JobState.RUNNING) }
                    updateMonitor(id, -1, node)
                }
            }
            "execution_error", "execution_success", "executed" -> viewModelScope.launch {
                refreshTasksInternal()
                refreshResultsInternal()
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
            val live = client.queue()
            val history = client.historyJobs()
            val submitted = _state.value.submittedJobIds
            (live + history).distinctBy { it.id }.map { it.copy(submittedByApp = it.id in submitted) }
        }.onSuccess { jobs -> _state.update { it.copy(jobs = jobs) } }
    }

    private suspend fun refreshResultsInternal() {
        runCatching {
            val history = client.history()
            ResultParser.parse(client.serverUrl(), history)
        }.onSuccess { results ->
            _state.update { it.copy(results = results) }
            if (_state.value.autoSaveResults) {
                results.filter { autoSavedUrls.add(it.url) }.forEach { media ->
                    viewModelScope.launch { runCatching { saveToMediaStore(media) } }
                }
            }
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
            client.downloadTo(media.url, output)
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
        val intent = Intent(app, JobMonitorService::class.java)
            .putExtra(JobMonitorService.EXTRA_BASE_URL, client.serverUrl())
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
            .putExtra(JobMonitorService.EXTRA_WORKFLOW_NAME, workflowName)
        ContextCompat.startForegroundService(app, intent)
    }

    private fun updateMonitor(promptId: String, progress: Int, node: String?) {
        if (promptId !in _state.value.submittedJobIds) return
        val intent = Intent(app, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_PROGRESS)
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
            .putExtra(JobMonitorService.EXTRA_PROGRESS, progress)
            .putExtra(JobMonitorService.EXTRA_NODE, node)
        app.startService(intent)
    }

    private fun stopMonitor(promptId: String) {
        val intent = Intent(app, JobMonitorService::class.java)
            .setAction(JobMonitorService.ACTION_STOP)
            .putExtra(JobMonitorService.EXTRA_PROMPT_ID, promptId)
        app.startService(intent)
    }

    private fun updateFieldLayout(key: String, transform: (ParameterField) -> ParameterField) {
        _state.update { ui -> ui.copy(fields = ui.fields.map { if (it.key == key) transform(it) else it }) }
    }

    private fun updateJob(id: String, transform: (JobSummary) -> JobSummary) {
        _state.update { ui ->
            val current = ui.jobs.firstOrNull { it.id == id } ?: JobSummary(id, JobState.RUNNING, submittedByApp = id in ui.submittedJobIds)
            ui.copy(jobs = listOf(transform(current)) + ui.jobs.filterNot { it.id == id })
        }
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
        runCatching { block() }.onFailure { error ->
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
}
