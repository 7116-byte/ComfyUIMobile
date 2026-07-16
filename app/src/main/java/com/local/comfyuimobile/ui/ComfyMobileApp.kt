package com.local.comfyuimobile.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.bridge.FieldValidator
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.WorkflowEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private enum class MainPage(val label: String, val icon: ImageVector) {
    WORKFLOWS("工作流", Icons.Default.Folder),
    PARAMETERS("参数", Icons.Default.Tune),
    RESULTS("结果", Icons.Default.Image),
    TASKS("任务", Icons.AutoMirrored.Filled.List),
}

@Composable
fun ComfyMobileApp(viewModel: MainViewModel, bridge: ComfyBridge) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error, state.notice) {
        val message = state.error ?: state.notice
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!state.advancedEditor) {
            if (state.activeServer == null) {
                ConnectionPage(state, viewModel, snackbar)
            } else {
                ConnectedApp(state, viewModel, snackbar)
            }
        }
        AndroidView(
            factory = { bridge.webView },
            modifier = if (state.advancedEditor) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f),
        )
        if (state.advancedEditor) {
            FilledTonalButton(
                onClick = viewModel::finishAdvancedEditor,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(6.dp))
                Text("完成并返回参数页")
            }
        }
    }
}

@Composable
private fun ConnectionPage(state: AppUiState, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Wifi, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("ComfyUI 手机端", style = MaterialTheme.typography.headlineMedium)
            Text("在可信局域网中连接电脑上的 ComfyUI。不会把提示词或工作流上传到云端。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.serverInput,
                onValueChange = viewModel::setServerInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ComfyUI 地址") },
                placeholder = { Text("http://192.168.10.109:8188") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { viewModel.connect() }, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(6.dp))
                    Text("连接")
                }
                OutlinedButton(onClick = viewModel::scanLan, enabled = !state.scanning) {
                    if (state.scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(6.dp))
                    Text("扫描局域网")
                }
            }
            if (state.savedServers.isNotEmpty()) {
                Text("已保存", style = MaterialTheme.typography.titleMedium)
                state.savedServers.forEach { profile ->
                    ServerCard(profile, onClick = { viewModel.setServerInput(profile.baseUrl); viewModel.connect(profile.baseUrl) }, onDelete = { viewModel.removeServer(profile.baseUrl) })
                }
            }
            if (state.discoveredServers.isNotEmpty()) {
                Text("扫描结果", style = MaterialTheme.typography.titleMedium)
                state.discoveredServers.forEach { profile -> ServerCard(profile, onClick = { viewModel.setServerInput(profile.baseUrl); viewModel.connect(profile.baseUrl) }) }
            }
            HorizontalDivider()
            Text("软件更新", style = MaterialTheme.typography.titleMedium)
            state.updateInfo?.let { info ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("发现新版本 ${info.tag}")
                        Button(onClick = viewModel::downloadUpdate) {
                            Icon(Icons.Default.Download, null)
                            Spacer(Modifier.width(6.dp))
                            Text("下载并安装")
                        }
                    }
                }
            } ?: OutlinedButton(onClick = { viewModel.checkUpdate() }) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(6.dp))
                Text("检查更新")
            }
            Text("不需要先连接 ComfyUI，也可以在这里检查和安装新版。", style = MaterialTheme.typography.bodySmall)
            Text("电脑端需要使用 --listen 0.0.0.0 启动，并允许 Windows 防火墙放行 8188 端口。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServerCard(profile: ServerProfile, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall)
                Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall)
            }
            Text(profile.comfyVersion, style = MaterialTheme.typography.labelSmall)
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除服务器") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedApp(state: AppUiState, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    var page by remember { mutableStateOf(MainPage.WORKFLOWS) }
    var settings by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeServer?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (state.status) {
                                ConnectionStatus.CONNECTED -> "在线 · 队列 ${state.queueRemaining} · ${state.systemStats?.comfyVersion.orEmpty()}"
                                ConnectionStatus.RECONNECTING -> "正在重连"
                                else -> state.connectionMessage
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
                navigationIcon = { Icon(Icons.Default.Wifi, null, Modifier.padding(start = 12.dp), tint = MaterialTheme.colorScheme.secondary) },
                actions = {
                    IconButton(onClick = viewModel::refreshAll) { Icon(Icons.Default.Refresh, "刷新") }
                    IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "设置") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainPage.entries.forEach { target ->
                    NavigationBarItem(
                        selected = page == target,
                        onClick = { page = target },
                        icon = { Icon(target.icon, null) },
                        label = { Text(target.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (page) {
                MainPage.WORKFLOWS -> WorkflowScreen(state, viewModel, onOpenParameters = { page = MainPage.PARAMETERS })
                MainPage.PARAMETERS -> ParameterScreen(state, viewModel)
                MainPage.RESULTS -> ResultScreen(state, viewModel)
                MainPage.TASKS -> TaskScreen(state, viewModel)
            }
            if (state.loading || state.generating) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
    if (settings) SettingsDialog(state, viewModel) { settings = false }
}

@Composable
private fun WorkflowScreen(state: AppUiState, viewModel: MainViewModel, onOpenParameters: () -> Unit) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var duplicateDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var forceDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    var exportRaw by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri) ?: "imported.json"
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw != null) viewModel.importWorkflow(name, raw)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw = exportRaw
        if (uri != null && raw != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
        exportRaw = null
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            label = { Text("搜索工作流") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(4.dp)); Text("导入")
            }
            state.selectedWorkflow?.let {
                FilledTonalButton(onClick = { dialogText = it.entry.name.substringBeforeLast('.'); duplicateDialog = true }) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("新建副本")
                }
                OutlinedButton(onClick = onOpenParameters) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(4.dp)); Text("参数") }
            }
        }
        if (state.selectedWorkflow != null) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { viewModel.saveWorkflow() }) { Icon(Icons.Default.Save, "保存") }
                IconButton(onClick = { dialogText = state.selectedWorkflow.entry.name.substringBeforeLast('.'); renameDialog = true }) { Icon(Icons.Default.Edit, "改名") }
                IconButton(onClick = { dialogText = state.selectedWorkflow.entry.path.substringBeforeLast('/', "workflows"); moveDialog = true }) { Icon(Icons.Default.Folder, "移动") }
                IconButton(onClick = {
                    state.selectedWorkflow.let { export -> exportRaw = export.rawJson; exportLauncher.launch(export.entry.name) }
                }) { Icon(Icons.Default.Download, "导出") }
                IconButton(onClick = { forceDialog = true }) { Icon(Icons.Default.MoreVert, "强制覆盖") }
                IconButton(onClick = { deleteDialog = true }) { Icon(Icons.Default.Delete, "删除") }
            }
        }
        val filtered = state.workflows.filter {
            search.isBlank() || it.path.contains(search, ignoreCase = true)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.path }) { entry ->
                WorkflowRow(entry, selected = state.selectedWorkflow?.entry?.path == entry.path) {
                    if (!entry.isDirectory) viewModel.selectWorkflow(entry)
                }
            }
        }
    }
    if (duplicateDialog) NameDialog("复制为新工作流", dialogText, { duplicateDialog = false }) { viewModel.duplicateWorkflow(it); duplicateDialog = false }
    if (renameDialog) NameDialog("工作流改名", dialogText, { renameDialog = false }) { viewModel.renameWorkflow(it); renameDialog = false }
    if (moveDialog) NameDialog("移动到文件夹", dialogText, { moveDialog = false }) { viewModel.moveWorkflow(it); moveDialog = false }
    if (deleteDialog) ConfirmDialog("删除工作流", "将从 ComfyUI 服务器永久删除 ${state.selectedWorkflow?.entry?.name}。", { deleteDialog = false }) { viewModel.deleteWorkflow(); deleteDialog = false }
    if (forceDialog) ConfirmDialog("强制覆盖", "忽略服务器修改时间并覆盖当前工作流，仅在确认桌面端改动可以丢弃时使用。", { forceDialog = false }) { viewModel.saveWorkflow(force = true); forceDialog = false }
}

@Composable
private fun WorkflowRow(entry: WorkflowEntry, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.FileOpen, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall)
                Text(entry.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            if (!entry.isDirectory) Text(formatSize(entry.size), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ParameterScreen(state: AppUiState, viewModel: MainViewModel) {
    val workflow = state.selectedWorkflow
    if (workflow == null) {
        EmptyState(Icons.Default.Tune, "请先在工作流页选择一个工作流")
        return
    }
    var showMore by remember { mutableStateOf(false) }
    var layoutDialog by remember { mutableStateOf(false) }
    var historyField by remember { mutableStateOf<ParameterField?>(null) }
    var uploadField by remember { mutableStateOf<ParameterField?>(null) }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val field = uploadField
        if (uri != null && field != null) viewModel.uploadField(field, uri)
        uploadField = null
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workflow.entry.name, style = MaterialTheme.typography.titleMedium)
                Text("${state.fields.count { it.visible }} 个可见参数", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { layoutDialog = true }) { Icon(Icons.Default.Tune, "表单布局") }
            IconButton(onClick = { viewModel.saveWorkflow() }) { Icon(Icons.Default.Save, "保存默认值") }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val primary = state.fields.filter { it.visible && it.section == ParameterSection.PRIMARY }
            val more = state.fields.filter { it.visible && it.section == ParameterSection.MORE }
            items(primary, key = { it.key }) { field ->
                ParameterCard(field, viewModel, onHistory = { historyField = field }, onUpload = {
                    uploadField = field
                    uploadLauncher.launch(if (field.kind == ParameterKind.VIDEO) arrayOf("video/*") else arrayOf("image/*"))
                })
            }
            if (more.isNotEmpty()) {
                item {
                    OutlinedButton(onClick = { showMore = !showMore }, Modifier.fillMaxWidth()) {
                        Text(if (showMore) "收起更多参数" else "更多参数（${more.size}）")
                    }
                }
                if (showMore) items(more, key = { it.key }) { field ->
                    ParameterCard(field, viewModel, onHistory = { historyField = field }, onUpload = {
                        uploadField = field
                        uploadLauncher.launch(if (field.kind == ParameterKind.VIDEO) arrayOf("video/*") else arrayOf("image/*"))
                    })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val problems = FieldValidator.problems(state.fields)
            OutlinedButton(onClick = { viewModel.setAdvancedEditor(true) }, Modifier.weight(1f)) { Text("高级编辑") }
            Button(onClick = viewModel::generate, enabled = !state.generating && state.bridgeReady && problems.isEmpty(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("生成")
            }
        }
        FieldValidator.problems(state.fields).firstOrNull()?.let { problem ->
            Text(problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
    if (historyField != null) PromptHistoryDialog(historyField!!, state, viewModel) { historyField = null }
    if (layoutDialog) LayoutDialog(state.fields, viewModel) { layoutDialog = false }
}

@Composable
private fun ParameterCard(field: ParameterField, viewModel: MainViewModel, onHistory: () -> Unit, onUpload: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.label, style = MaterialTheme.typography.titleSmall)
                }
                if (field.kind == ParameterKind.MULTILINE) IconButton(onClick = onHistory) { Icon(Icons.Default.History, "历史") }
            }
            if (field.linked) Text("此参数已由其他节点连接，当前值只读。", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
            when (field.kind) {
                ParameterKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = field.displayValue.toBoolean(), onCheckedChange = { viewModel.updateField(field.key, it.toString()) }, enabled = !field.linked)
                    Spacer(Modifier.width(10.dp)); Text(if (field.displayValue.toBoolean()) "开启" else "关闭")
                }
                ParameterKind.COMBO -> ComboField(field, viewModel)
                ParameterKind.INTEGER, ParameterKind.DECIMAL -> {
                    NumberField(field, viewModel)
                    if (field.name.contains("seed", ignoreCase = true)) {
                        FilledTonalButton(onClick = {
                            val upper = field.maximum?.toLong()?.coerceAtMost(Long.MAX_VALUE - 1) ?: Long.MAX_VALUE - 1
                            viewModel.updateField(field.key, Random.nextLong(0, upper.coerceAtLeast(1) + 1).toString())
                        }, enabled = !field.linked) { Text("随机种子") }
                    }
                }
                ParameterKind.IMAGE, ParameterKind.VIDEO -> {
                    OutlinedTextField(field.displayValue, { viewModel.updateField(field.key, it) }, Modifier.fillMaxWidth(), enabled = !field.linked, singleLine = true)
                    FilledTonalButton(onClick = onUpload, enabled = !field.linked) {
                        Icon(if (field.kind == ParameterKind.VIDEO) Icons.Default.VideoFile else Icons.Default.UploadFile, null)
                        Spacer(Modifier.width(6.dp)); Text("选择并上传")
                    }
                }
                ParameterKind.MULTILINE -> OutlinedTextField(
                    value = field.displayValue,
                    onValueChange = { viewModel.updateField(field.key, it) },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = !field.linked,
                    minLines = 5,
                )
                ParameterKind.TEXT -> OutlinedTextField(field.displayValue, { viewModel.updateField(field.key, it) }, Modifier.fillMaxWidth(), enabled = !field.linked)
                ParameterKind.UNSUPPORTED -> Text(field.warning ?: "此控件需在高级编辑中修改", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ComboField(field: ParameterField, viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = !field.linked, modifier = Modifier.fillMaxWidth()) {
            Text(field.displayValue, modifier = Modifier.weight(1f)); Icon(Icons.Default.ArrowDownward, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false; query = "" }) {
            if (field.options.size > 12) {
                OutlinedTextField(query, { query = it }, label = { Text("搜索选项") }, singleLine = true, modifier = Modifier.padding(8.dp))
            }
            val matches = field.options.filter { query.isBlank() || it.contains(query, ignoreCase = true) }
            matches.take(100).forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { viewModel.updateField(field.key, option); expanded = false; query = "" })
            }
            if (matches.size > 100) DropdownMenuItem(text = { Text("还有 ${matches.size - 100} 项，请继续搜索") }, onClick = {})
        }
    }
}

@Composable
private fun NumberField(field: ParameterField, viewModel: MainViewModel) {
    OutlinedTextField(
        value = field.displayValue,
        onValueChange = { viewModel.updateField(field.key, it) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !field.linked,
        singleLine = true,
    )
    val min = field.minimum
    val max = field.maximum
    val current = field.displayValue.toDoubleOrNull()
    if (min != null && max != null && current != null && max > min && max - min < 1_000_000) {
        Slider(
            value = current.coerceIn(min, max).toFloat(),
            onValueChange = {
                val value = if (field.kind == ParameterKind.INTEGER) it.toLong().toString() else String.format(Locale.US, "%.4f", it).trimEnd('0').trimEnd('.')
                viewModel.updateField(field.key, value)
            },
            valueRange = min.toFloat()..max.toFloat(),
            enabled = !field.linked,
        )
    }
}

@Composable
private fun PromptHistoryDialog(field: ParameterField, state: AppUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文本历史（${state.promptHistory.size}/50）") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("搜索") })
                LazyColumn(Modifier.fillMaxHeight(0.6f)) {
                    items(state.promptHistory.filter { query.isBlank() || it.contains(query, true) }) { value ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.updateField(field.key, value); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(value, Modifier.weight(1f), maxLines = 3)
                            IconButton(onClick = { viewModel.removePromptHistory(value) }) { Icon(Icons.Default.Delete, "删除") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = { TextButton(onClick = viewModel::clearPromptHistory) { Text("清空") } },
    )
}

@Composable
private fun LayoutDialog(fields: List<ParameterField>, viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("参数页布局") },
        text = {
            LazyColumn(Modifier.fillMaxHeight(0.7f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(fields.sortedBy { it.order }, key = { it.key }) { field ->
                    OutlinedCard {
                        Column(Modifier.padding(10.dp)) {
                            OutlinedTextField(field.label, { viewModel.renameField(field.key, it) }, Modifier.fillMaxWidth(), label = { Text(field.name) }, singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("显示", Modifier.weight(1f)); Switch(field.visible, { viewModel.setFieldVisibility(field.key, it) })
                                IconButton(onClick = { viewModel.moveField(field.key, -1) }) { Icon(Icons.Default.ArrowUpward, "上移") }
                                IconButton(onClick = { viewModel.moveField(field.key, 1) }) { Icon(Icons.Default.ArrowDownward, "下移") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistChip(onClick = { viewModel.setFieldSection(field.key, ParameterSection.PRIMARY) }, label = { Text("主要") }, leadingIcon = if (field.section == ParameterSection.PRIMARY) {{ Icon(Icons.Default.CheckCircle, null) }} else null)
                                AssistChip(onClick = { viewModel.setFieldSection(field.key, ParameterSection.MORE) }, label = { Text("更多") }, leadingIcon = if (field.section == ParameterSection.MORE) {{ Icon(Icons.Default.CheckCircle, null) }} else null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun ResultScreen(state: AppUiState, viewModel: MainViewModel) {
    var fullImage by remember { mutableStateOf<ResultMedia?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("生成结果", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${state.results.size} 项", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = viewModel::refreshResults) { Icon(Icons.Default.Refresh, "刷新") }
        }
        if (state.results.isEmpty()) EmptyState(Icons.Default.Image, "暂无图片或视频结果")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.results, key = { it.url }) { media ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    if (media.kind == MediaKind.IMAGE) {
                        AsyncImage(
                            model = previewUrl(media),
                            contentDescription = media.filename,
                            modifier = Modifier.fillMaxWidth().height(260.dp).clickable { fullImage = media },
                            contentScale = ContentScale.Fit,
                        )
                    } else VideoPlayer(media.url)
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(media.filename, Modifier.weight(1f), maxLines = 2)
                        IconButton(onClick = { viewModel.saveResult(media) }) { Icon(Icons.Default.Download, "保存") }
                        IconButton(onClick = { viewModel.shareResult(media) }) { Icon(Icons.Default.Share, "分享") }
                        IconButton(onClick = { viewModel.openResult(media) }) { Icon(Icons.Default.FileOpen, "打开原文件") }
                    }
                }
            }
        }
    }
    fullImage?.let { media ->
        AlertDialog(
            onDismissRequest = { fullImage = null },
            text = { AsyncImage(media.url, media.filename, Modifier.fillMaxWidth().fillMaxHeight(0.8f), contentScale = ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { fullImage = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun VideoPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = false
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().height(260.dp))
}

@Composable
private fun TaskScreen(state: AppUiState, viewModel: MainViewModel) {
    var appOnly by remember { mutableStateOf(false) }
    val jobs = if (appOnly) state.jobs.filter { it.submittedByApp } else state.jobs
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("服务器任务", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("仅本 App"); Switch(appOnly, { appOnly = it })
            IconButton(onClick = viewModel::refreshTasks) { Icon(Icons.Default.Refresh, "刷新") }
        }
        Row(Modifier.padding(horizontal = 12.dp)) {
            OutlinedButton(onClick = viewModel::clearPendingJobs) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("清空待执行") }
        }
        if (jobs.isEmpty()) EmptyState(Icons.AutoMirrored.Filled.List, "暂无任务记录")
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(jobs, key = { it.id }) { job -> JobCard(job, viewModel) }
        }
    }
}

@Composable
private fun JobCard(job: JobSummary, viewModel: MainViewModel) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.id.take(12), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    when (job.state) {
                        JobState.RUNNING -> "运行中"
                        JobState.PENDING -> "等待中"
                        JobState.SUCCESS -> "成功"
                        JobState.ERROR -> "失败"
                        JobState.CANCELLED -> "已取消"
                        JobState.UNKNOWN -> "历史"
                    },
                    color = if (job.state == JobState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (job.submittedByApp) Text("本 App 提交", style = MaterialTheme.typography.labelSmall)
            job.currentNode?.let { Text("节点：$it", style = MaterialTheme.typography.bodySmall) }
            job.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
            if (job.state in setOf(JobState.RUNNING, JobState.PENDING)) {
                TextButton(onClick = { viewModel.cancelJob(job) }, modifier = Modifier.align(Alignment.End)) { Text("取消任务") }
            }
        }
    }
}

@Composable
private fun SettingsDialog(state: AppUiState, viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("服务器", style = MaterialTheme.typography.titleSmall)
                Text(state.activeServer?.baseUrl.orEmpty())
                state.activeServer?.lastSeen?.takeIf { it > 0L }?.let { Text("最后在线：${formatTime(it)}") }
                state.systemStats?.let { stats ->
                    Text("ComfyUI ${stats.comfyVersion} · 前端 ${stats.frontendVersion}")
                    stats.devices.forEach { Text("${it.name}\n显存 ${formatSize(it.vramFree)} / ${formatSize(it.vramTotal)} 可用") }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("自动保存结果"); Text("图片和视频写入系统相册", style = MaterialTheme.typography.bodySmall) }
                    Switch(state.autoSaveResults, viewModel::setAutoSaveResults)
                }
                HorizontalDivider()
                Text("软件更新", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { viewModel.checkUpdate() }) { Text("检查 GitHub 更新") }
                state.updateInfo?.let { info ->
                    Text("发现 ${info.tag}")
                    Button(onClick = viewModel::downloadUpdate) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(4.dp)); Text("下载并安装") }
                }
                OutlinedButton(onClick = { viewModel.disconnect(); onDismiss() }) { Icon(Icons.Default.CloudOff, null); Spacer(Modifier.width(4.dp)); Text("断开连接") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(54.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun displayName(context: Context, uri: Uri): String? = context.contentResolver
    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private fun formatSize(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", value / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024))
    value >= 1024L -> String.format(Locale.US, "%.1f KB", value / 1024.0)
    else -> "$value B"
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(value))

private fun previewUrl(media: ResultMedia): String =
    if (media.kind == MediaKind.IMAGE) "${media.url}&preview=webp;90" else media.url
