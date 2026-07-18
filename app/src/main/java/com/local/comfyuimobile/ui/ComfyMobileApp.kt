package com.local.comfyuimobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.bridge.FieldValidator
import com.local.comfyuimobile.data.WorkflowBrowser
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import com.local.comfyuimobile.model.ResultMedia
import com.local.comfyuimobile.model.ResultSource
import com.local.comfyuimobile.model.ServerProfile
import com.local.comfyuimobile.model.WorkflowEntry
import com.local.comfyuimobile.model.WorkflowConnectionMarker
import com.local.comfyuimobile.model.WorkflowNode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class MainPage(val label: String, val icon: ImageVector) {
    WORKFLOWS("工作流", Icons.Default.Folder),
    PARAMETERS("参数", Icons.Default.Tune),
    RESULTS("结果", Icons.Default.Image),
    TASKS("任务", Icons.AutoMirrored.Filled.List),
}

private enum class ResultLayout { ALL, ALBUMS }
private data class ResultAlbum(val jobId: String, val media: List<ResultMedia>)

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

    BackHandler(enabled = state.advancedEditor) { viewModel.finishAdvancedEditor() }
    Column(Modifier.fillMaxSize()) {
        if (state.advancedEditor) {
            Surface(tonalElevation = 4.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ComfyUI 网页编辑", style = MaterialTheme.typography.titleMedium)
                        Text(state.activeServer?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    TextButton(onClick = viewModel::finishAdvancedEditor) {
                        Icon(Icons.Default.Close, null)
                        Spacer(Modifier.width(4.dp))
                        Text("关闭并刷新参数")
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (!state.advancedEditor) {
                if (state.activeServer == null) {
                    ConnectionPage(state, viewModel, snackbar)
                } else {
                    ConnectedApp(state, viewModel, snackbar)
                }
            }
            key(bridge.webView) {
                AndroidView(
                    factory = { bridge.webView },
                    update = { view ->
                        if (state.advancedEditor) {
                            view.onResume()
                            view.requestLayout()
                            view.invalidate()
                        }
                    },
                    modifier = if (state.advancedEditor) Modifier.fillMaxSize() else Modifier.size(1.dp).alpha(0f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionPage(state: AppUiState, viewModel: MainViewModel, snackbar: SnackbarHostState) {
    var settings by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { settings = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
            if (state.status == ConnectionStatus.CONNECTING || state.status == ConnectionStatus.ERROR) {
                ConnectionProgressCard(state)
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
            Text("电脑端需要使用 --listen 0.0.0.0 启动，并允许 Windows 防火墙放行 8188 端口。", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (settings) SettingsDialog(state, viewModel) { settings = false }
}

private val connectionStepNames = listOf(
    "检查局域网地址",
    "读取服务器信息",
    "打开 ComfyUI 网页",
    "初始化前端",
    "读取节点定义",
    "同步连接数据",
)

@Composable
private fun ConnectionProgressCard(state: AppUiState) {
    val current = state.connectionStep.coerceIn(1, state.connectionTotalSteps)
    val failed = state.status == ConnectionStatus.ERROR
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (failed) "连接失败（第 $current/${state.connectionTotalSteps} 步）"
                else "正在连接（第 $current/${state.connectionTotalSteps} 步）",
                style = MaterialTheme.typography.titleMedium,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            LinearProgressIndicator(
                progress = { current.toFloat() / state.connectionTotalSteps },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(state.connectionMessage, style = MaterialTheme.typography.bodyMedium)
            connectionStepNames.forEachIndexed { index, name ->
                val step = index + 1
                val statusText = when {
                    step < current -> "已完成"
                    step == current && failed -> "失败"
                    step == current -> "进行中"
                    else -> "等待"
                }
                val color = when {
                    step == current && failed -> MaterialTheme.colorScheme.error
                    step <= current -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text("$step. $name · $statusText", color = color, style = MaterialTheme.typography.bodySmall)
            }
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
    var resultSource by rememberSaveable { mutableStateOf(ResultSource.LOCAL) }
    var resultLayout by rememberSaveable { mutableStateOf(ResultLayout.ALBUMS) }
    var resultAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.activeServer?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.activeJobId != null && state.generationProgress != 1f &&
                                state.generationMessage.isNotBlank() && !state.generationMessage.startsWith("生成失败")
                            ) {
                                state.generationMessage
                            } else when (state.status) {
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
                    IconButton(onClick = viewModel::refreshOrReconnect) {
                        Icon(
                            Icons.Default.Refresh,
                            if (state.status == ConnectionStatus.CONNECTED) "刷新" else "重新连接",
                        )
                    }
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
                MainPage.RESULTS -> ResultScreen(
                    state = state,
                    viewModel = viewModel,
                    source = resultSource,
                    onSourceChange = {
                        resultSource = it
                        resultAlbumId = null
                    },
                    layout = resultLayout,
                    onLayoutChange = { resultLayout = it },
                    selectedAlbumId = resultAlbumId,
                    onSelectedAlbumChange = { resultAlbumId = it },
                )
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
    var currentFolder by rememberSaveable(state.activeServer?.baseUrl) { mutableStateOf(WorkflowBrowser.ROOT) }
    var duplicateDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var moveDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    var exportRaw by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri) ?: "imported.json"
            viewModel.importWorkflow(uri, name, context.contentResolver.getType(uri))
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val raw = exportRaw
        if (uri != null && raw != null) context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
        exportRaw = null
    }
    LaunchedEffect(state.workflows, currentFolder) {
        if (currentFolder != WorkflowBrowser.ROOT && state.workflows.none { it.isDirectory && it.path == currentFolder }) {
            currentFolder = WorkflowBrowser.ROOT
        }
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
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentFolder != WorkflowBrowser.ROOT && search.isBlank()) {
                OutlinedButton(onClick = { currentFolder = WorkflowBrowser.up(currentFolder) }) { Text("上一级") }
            }
            Text(
                if (search.isBlank()) currentFolder.removePrefix("workflows/").ifBlank { "全部分类" } else "搜索全部分类",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "image/*"))
            }) {
                Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(4.dp)); Text("打开工作流文件")
            }
        }
        Text(
            "单击选择工作流，选择后点击“打开参数”",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.selectedWorkflow != null) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenParameters) { Text("打开参数") }
                OutlinedButton(onClick = {
                    dialogText = state.selectedWorkflow.entry.name.substringBeforeLast('.')
                    duplicateDialog = true
                }) { Text("新建副本") }
                OutlinedButton(onClick = {
                    dialogText = state.selectedWorkflow.entry.name.substringBeforeLast('.')
                    renameDialog = true
                }) { Text("改名") }
                OutlinedButton(onClick = {
                    dialogText = state.selectedWorkflow.entry.path.substringBeforeLast('/', "workflows")
                    moveDialog = true
                }) { Text("移动") }
                OutlinedButton(onClick = {
                    state.selectedWorkflow.let { export -> exportRaw = export.rawJson; exportLauncher.launch(export.entry.name) }
                }) { Text("导出") }
                OutlinedButton(onClick = { deleteDialog = true }) { Text("删除") }
            }
        }
        val filtered = WorkflowBrowser.entries(state.workflows, currentFolder, search)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.path }) { entry ->
                WorkflowRow(
                    entry = entry,
                    selected = state.selectedWorkflow?.entry?.path == entry.path,
                    onClick = {
                        if (entry.isDirectory) {
                            currentFolder = entry.path
                            search = ""
                        } else {
                            viewModel.selectWorkflow(entry)
                        }
                    },
                )
            }
        }
    }
    if (duplicateDialog) NameDialog("复制为新工作流", dialogText, { duplicateDialog = false }) { viewModel.duplicateWorkflow(it); duplicateDialog = false }
    if (renameDialog) NameDialog("工作流改名", dialogText, { renameDialog = false }) { viewModel.renameWorkflow(it); renameDialog = false }
    if (moveDialog) NameDialog("移动到文件夹", dialogText, { moveDialog = false }) { viewModel.moveWorkflow(it); moveDialog = false }
    if (deleteDialog) ConfirmDialog("删除工作流", "将从 ComfyUI 服务器永久删除 ${state.selectedWorkflow?.entry?.name}。", { deleteDialog = false }) { viewModel.deleteWorkflow(); deleteDialog = false }
}

@Composable
private fun WorkflowRow(
    entry: WorkflowEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
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
    var expandedNodeIds by remember(workflow.entry.path) { mutableStateOf(emptySet<String>()) }
    var layoutDialog by remember { mutableStateOf(false) }
    var historyField by remember { mutableStateOf<ParameterField?>(null) }
    var uploadField by remember { mutableStateOf<ParameterField?>(null) }
    var cacheNode by remember { mutableStateOf<WorkflowNode?>(null) }
    var recentMenuExpanded by remember { mutableStateOf(false) }
    var saveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val field = uploadField
        if (uri != null && field != null) viewModel.uploadField(field, uri)
        uploadField = null
    }
    val visibleFields = state.fields.filter { it.visible }.groupBy { it.nodeId }
    val nodes = workflow.nodes.sortedBy { it.order }.map { node ->
        val fields = visibleFields[node.id].orEmpty().sortedBy { it.order }
        node to fields
    }
    val localProblems = FieldValidator.detailedProblems(state.fields)
    val localProblemsByNode = localProblems.groupBy { it.nodeId }.mapValues { (_, items) -> items.map { it.message } }
    val problemNodeIds = localProblemsByNode.keys + state.nodeProblems.keys
    val recentWorkflows = state.recentWorkflowPaths.mapNotNull { path ->
        state.workflows.firstOrNull { !it.isDirectory && it.path == path }
    }.let { entries -> listOf(workflow.entry) + entries.filterNot { it.path == workflow.entry.path } }
        .distinctBy { it.path }
    val listState = rememberLazyListState()
    LaunchedEffect(problemNodeIds) {
        if (problemNodeIds.isNotEmpty()) expandedNodeIds = expandedNodeIds + problemNodeIds
    }
    LaunchedEffect(state.currentExecutingNodeId, nodes.size) {
        val nodeId = state.currentExecutingNodeId ?: return@LaunchedEffect
        val index = nodes.indexOfFirst { it.first.id == nodeId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(0.72f)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { recentMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(workflow.entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                "${nodes.size} 个流程部件 · ${state.fields.count { it.visible }} 个参数",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Icon(
                            if (recentMenuExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            "选择最近打开的工作流",
                        )
                    }
                }
                DropdownMenu(
                    expanded = recentMenuExpanded,
                    onDismissRequest = { recentMenuExpanded = false },
                ) {
                    if (recentWorkflows.isEmpty()) {
                        DropdownMenuItem(text = { Text("暂无最近打开的工作流") }, onClick = {}, enabled = false)
                    } else {
                        recentWorkflows.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(entry.name, maxLines = 1)
                                        Text(
                                            entry.path.substringBeforeLast('/', "workflows"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        if (entry.path == workflow.entry.path) Icons.Default.CheckCircle else Icons.Default.History,
                                        null,
                                    )
                                },
                                onClick = {
                                    recentMenuExpanded = false
                                    if (entry.path != workflow.entry.path) viewModel.selectWorkflow(entry)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(0.28f))
            TextButton(onClick = { layoutDialog = true }, modifier = Modifier.height(42.dp)) { Text("表单布局") }
            Surface(
                modifier = Modifier.width(158.dp).height(42.dp),
                shape = RoundedCornerShape(21.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable { viewModel.saveWorkflow() },
                        contentAlignment = Alignment.Center,
                    ) { Text("保存", color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    Box(
                        Modifier.width(1.dp).fillMaxHeight(0.58f).background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clickable {
                            saveAsName = workflow.entry.name.substringBeforeLast('.') + "-副本"
                            saveAsDialog = true
                        },
                        contentAlignment = Alignment.Center,
                    ) { Text("另存", color = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }
        if (state.generationMessage.isNotBlank()) {
            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.generationMessage, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        state.activeJobId?.let { Text(it.take(8), style = MaterialTheme.typography.labelSmall) }
                    }
                    state.generationProgress?.let { progress ->
                        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f).imePadding(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(nodes, key = { it.first.id }) { (node, fields) ->
                val nodeId = node.id
                NodeParameterCard(
                    node = node,
                    fields = fields,
                    expanded = nodeId in expandedNodeIds,
                    active = state.currentExecutingNodeId == nodeId,
                    problems = localProblemsByNode[nodeId].orEmpty() + state.nodeProblems[nodeId].orEmpty(),
                    cached = state.cacheOutputRules.any {
                        it.enabled && it.serverUrl == state.activeServer?.baseUrl && it.nodeType == node.type
                    },
                    onToggle = {
                        expandedNodeIds = if (nodeId in expandedNodeIds) expandedNodeIds - nodeId else expandedNodeIds + nodeId
                    },
                    onLongPress = if (node.isOutput) ({ cacheNode = node }) else null,
                    viewModel = viewModel,
                    onHistory = { historyField = it },
                    onUpload = { field ->
                        uploadField = field
                        uploadLauncher.launch(if (field.kind == ParameterKind.VIDEO) arrayOf("video/*") else arrayOf("image/*"))
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { viewModel.setAdvancedEditor(true) }, Modifier.weight(1f)) { Text("高级编辑") }
            Button(
                onClick = viewModel::generate,
                enabled = !state.generating && !state.loading && state.bridgeReady && localProblems.isEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("生成")
            }
        }
        val firstProblem = localProblems.firstOrNull()?.let { problem ->
            val title = workflow.nodes.firstOrNull { it.id == problem.nodeId }?.title ?: "节点 ${problem.nodeId}"
            "$title：${problem.message}"
        } ?: state.nodeProblems.entries.firstOrNull()?.let { (nodeId, messages) ->
            val title = workflow.nodes.firstOrNull { it.id == nodeId }?.title ?: "节点 $nodeId"
            "$title：${messages.firstOrNull().orEmpty()}"
        }
        if (firstProblem != null) {
            Text(firstProblem, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
    if (state.workflowOverwriteRequired) {
        ConfirmDialog(
            "覆盖服务器工作流",
            state.workflowOverwriteReason,
            viewModel::dismissWorkflowOverwrite,
        ) { viewModel.saveWorkflow(force = true) }
    }
    if (historyField != null) PromptHistoryDialog(historyField!!, state, viewModel) { historyField = null }
    if (layoutDialog) LayoutDialog(state.fields, viewModel) { layoutDialog = false }
    if (saveAsDialog) {
        NameDialog("工作流另存为", saveAsName, { saveAsDialog = false }) {
            viewModel.saveWorkflowAs(it)
            saveAsDialog = false
        }
    }
    cacheNode?.let { node ->
        val cached = state.cacheOutputRules.any {
            it.serverUrl == state.activeServer?.baseUrl && it.nodeType == node.type
        }
        ConfirmDialog(
            title = if (cached) "移出全工作流保存白名单" else "加入全工作流保存白名单",
            message = if (cached) {
                "以后所有工作流中的“${node.type}”部件都不再自动保存，已经保存的文件不会删除。"
            } else {
                "以后所有工作流中的“${node.type}”输出部件都会自动保存。仅处理本 App 提交的任务，电脑浏览器提交的任务不会保存。"
            },
            onDismiss = { cacheNode = null },
            onConfirm = { viewModel.toggleCacheOutput(node); cacheNode = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NodeParameterCard(
    node: WorkflowNode,
    fields: List<ParameterField>,
    expanded: Boolean,
    active: Boolean,
    problems: List<String>,
    cached: Boolean,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)?,
    viewModel: MainViewModel,
    onHistory: (ParameterField) -> Unit,
    onUpload: (ParameterField) -> Unit,
) {
    val title = node.title.ifBlank { node.type.ifBlank { "未命名节点" } }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            when {
                problems.isNotEmpty() -> 2.dp
                active -> 3.dp
                else -> 1.dp
            },
            when {
                problems.isNotEmpty() -> MaterialTheme.colorScheme.error
                active -> Color(0xFF35C46A)
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().combinedClickable(onClick = onToggle, onLongClick = onLongPress).padding(horizontal = 6.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConnectionMarkers(node.inputMarkers, input = true)
                if (node.inputMarkers.isNotEmpty()) Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            if (node.type.isNotBlank() && node.type != title) append(node.type).append(" · ")
                            append(fields.size).append(" 个设置")
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (cached) Text("本地保存白名单", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                    if (node.isOutput) Text("长按管理本地保存", style = MaterialTheme.typography.labelSmall)
                    if (active) Text("正在执行", color = Color(0xFF35C46A), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(6.dp))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "收起" else "展开")
                if (node.outputMarkers.isNotEmpty()) Spacer(Modifier.width(8.dp))
                ConnectionMarkers(node.outputMarkers, input = false)
            }
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    val hasSeedActions = node.type.contains("Seed (rgthree)", ignoreCase = true)
                    if (fields.isEmpty() && !hasSeedActions) {
                        Text("此部件没有可调整参数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (fields.isNotEmpty() || hasSeedActions) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
                        ) {
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                fields.forEachIndexed { index, field ->
                                    ParameterEditor(field, viewModel, onHistory = { onHistory(field) }, onUpload = { onUpload(field) })
                                    if (index < fields.lastIndex || hasSeedActions) HorizontalDivider()
                                }
                                if (hasSeedActions) {
                                    Text("种子快捷操作", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                                    FilledTonalButton(
                                        onClick = { viewModel.invokeSeedAction(node.id, "Randomize Each Time", "已设为每次生成随机") },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("每次生成随机") }
                                    FilledTonalButton(
                                        onClick = { viewModel.invokeSeedAction(node.id, "New Fixed Random", "已生成新的固定种子") },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("生成新的固定种子") }
                                    OutlinedButton(
                                        onClick = { viewModel.invokeSeedAction(node.id, "Use Last Queued Seed", "已使用上次排队种子") },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("使用上次排队种子") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionMarkers(markers: List<WorkflowConnectionMarker>, input: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markers.forEach { marker ->
            val markerColor = remember(marker.color) {
                runCatching { Color(android.graphics.Color.parseColor(marker.color)) }.getOrDefault(Color(0xFF9E9E9E))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (input) Box(Modifier.size(10.dp).background(markerColor, CircleShape))
                Text(marker.label, color = markerColor, style = MaterialTheme.typography.labelMedium)
                if (!input) Box(Modifier.size(10.dp).background(markerColor, CircleShape))
            }
        }
    }
}

@Composable
private fun ParameterEditor(field: ParameterField, viewModel: MainViewModel, onHistory: () -> Unit, onUpload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    field.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (!field.name.equals(field.label, ignoreCase = true)) {
                    Text(field.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (field.kind == ParameterKind.MULTILINE) IconButton(onClick = onHistory, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.History, "历史", modifier = Modifier.size(20.dp))
            }
        }
        if (field.linked) Text("已由其他部件连接，当前值只读", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
        when (field.kind) {
            ParameterKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = field.displayValue.toBoolean(), onCheckedChange = { viewModel.updateField(field.key, it.toString()) }, enabled = !field.linked)
                Spacer(Modifier.width(10.dp)); Text(if (field.displayValue.toBoolean()) "开启" else "关闭", style = MaterialTheme.typography.bodyMedium)
            }
            ParameterKind.COMBO -> ComboField(field, viewModel)
            ParameterKind.INTEGER, ParameterKind.DECIMAL -> {
                NumberField(field, viewModel)
                if (field.name.contains("seed", ignoreCase = true) && !field.nodeType.contains("Seed (rgthree)", ignoreCase = true)) {
                    FilledTonalButton(onClick = {
                        val upper = field.maximum?.toLong()?.coerceAtMost(Long.MAX_VALUE - 1) ?: Long.MAX_VALUE - 1
                        viewModel.updateField(field.key, Random.nextLong(0, upper.coerceAtLeast(1) + 1).toString())
                    }, enabled = !field.linked) { Text("随机种子") }
                }
            }
            ParameterKind.IMAGE, ParameterKind.VIDEO -> {
                OutlinedTextField(
                    field.displayValue,
                    { viewModel.updateField(field.key, it) },
                    focusedFieldModifier(Modifier.fillMaxWidth()),
                    enabled = !field.linked,
                    singleLine = true,
                )
                FilledTonalButton(onClick = onUpload, enabled = !field.linked) {
                    Icon(if (field.kind == ParameterKind.VIDEO) Icons.Default.VideoFile else Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(6.dp)); Text("选择并上传")
                }
            }
            ParameterKind.MULTILINE -> OutlinedTextField(
                value = field.displayValue,
                onValueChange = { viewModel.updateField(field.key, it) },
                modifier = focusedFieldModifier(Modifier.fillMaxWidth().height(140.dp)),
                enabled = !field.linked,
                minLines = 5,
            )
            ParameterKind.TEXT -> OutlinedTextField(
                field.displayValue,
                { viewModel.updateField(field.key, it) },
                focusedFieldModifier(Modifier.fillMaxWidth()),
                enabled = !field.linked,
            )
            ParameterKind.UNSUPPORTED -> Text(field.warning ?: "此控件需在高级编辑中修改", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        modifier = focusedFieldModifier(Modifier.fillMaxWidth()),
        enabled = !field.linked,
        singleLine = true,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun focusedFieldModifier(base: Modifier): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return base
        .bringIntoViewRequester(requester)
        .onFocusChanged { focus ->
            if (focus.isFocused) {
                scope.launch {
                    // 等软键盘完成占位后，再把真正获得焦点的参数移入可视区。
                    delay(220)
                    requester.bringIntoView()
                }
            }
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
private fun ResultScreen(
    state: AppUiState,
    viewModel: MainViewModel,
    source: ResultSource,
    onSourceChange: (ResultSource) -> Unit,
    layout: ResultLayout,
    onLayoutChange: (ResultLayout) -> Unit,
    selectedAlbumId: String?,
    onSelectedAlbumChange: (String?) -> Unit,
) {
    var selectedMedia by remember { mutableStateOf<ResultMedia?>(null) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteSelection by remember { mutableStateOf(false) }
    var galleryItems by remember { mutableStateOf<List<ResultMedia>>(emptyList()) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    val media = (if (source == ResultSource.LOCAL) state.localResults else state.results)
        .sortedWith(compareByDescending<ResultMedia> { it.createdAt }.thenByDescending { it.taskNumber })
    val albums = media.groupBy { it.jobId }
        .map { (jobId, items) -> ResultAlbum(jobId, items) }
        .sortedWith(compareByDescending<ResultAlbum> { it.media.maxOfOrNull(ResultMedia::createdAt) ?: 0L }
            .thenByDescending { it.media.maxOfOrNull(ResultMedia::taskNumber) ?: 0L })
    val selectedAlbum = albums.firstOrNull { it.jobId == selectedAlbumId }
    val selectedItems = media.filter { it.stableKey() in selectedKeys }
    val selectionMode = selectedKeys.isNotEmpty()
    fun toggleSelection(items: Collection<ResultMedia>) {
        val keys = items.map(ResultMedia::stableKey).toSet()
        selectedKeys = if (keys.all { it in selectedKeys }) selectedKeys - keys else selectedKeys + keys
    }
    fun openMedia(item: ResultMedia, context: List<ResultMedia>) {
        if (item.kind == MediaKind.IMAGE) {
            val images = context.filter { it.kind == MediaKind.IMAGE }
            galleryInitialIndex = images.indexOfFirst { (it.localPath ?: it.url) == (item.localPath ?: item.url) }.coerceAtLeast(0)
            galleryItems = images
        } else {
            selectedMedia = item
        }
    }
    LaunchedEffect(source) { selectedKeys = emptySet() }
    LaunchedEffect(media.map(ResultMedia::stableKey)) {
        selectedKeys = selectedKeys.intersect(media.map(ResultMedia::stableKey).toSet())
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source == ResultSource.LOCAL) FilledTonalButton({ onSourceChange(ResultSource.LOCAL) }, Modifier.weight(1f)) { Text("本地") }
            else OutlinedButton({ onSourceChange(ResultSource.LOCAL) }, Modifier.weight(1f)) { Text("本地") }
            if (source == ResultSource.CLOUD) FilledTonalButton({ onSourceChange(ResultSource.CLOUD) }, Modifier.weight(1f)) { Text("云端") }
            else OutlinedButton({ onSourceChange(ResultSource.CLOUD) }, Modifier.weight(1f)) { Text("云端") }
        }
        if (selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedKeys = emptySet() }) { Icon(Icons.Default.Close, "退出多选") }
                Text("已选 ${selectedItems.size} 项", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { selectedKeys = media.map(ResultMedia::stableKey).toSet() }) {
                    Icon(Icons.Default.SelectAll, "全选")
                }
                IconButton(onClick = {
                    viewModel.saveResults(selectedItems)
                    selectedKeys = emptySet()
                }) {
                    Icon(if (source == ResultSource.CLOUD) Icons.Default.Download else Icons.Default.Save, if (source == ResultSource.CLOUD) "一键下载" else "一键保存")
                }
                if (source == ResultSource.LOCAL) {
                    IconButton(onClick = { confirmDeleteSelection = true }) { Icon(Icons.Default.Delete, "删除所选") }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectedAlbum != null) {
                    TextButton(onClick = { onSelectedAlbumChange(null) }) { Text("‹ 返回相册") }
                } else {
                    Text(
                        if (source == ResultSource.LOCAL) "手机独立保存的白名单作品" else "ComfyUI 服务器媒体资产",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onLayoutChange(if (layout == ResultLayout.ALL) ResultLayout.ALBUMS else ResultLayout.ALL) }) {
                        Text(if (layout == ResultLayout.ALL) "任务相册" else "全部平铺")
                    }
                }
                IconButton(onClick = { if (source == ResultSource.LOCAL) viewModel.refreshLocalResults() else viewModel.refreshResults() }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }
        }
        when {
            selectedAlbum != null -> {
                Text(albumTitle(selectedAlbum), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                ResultMediaGrid(
                    media = selectedAlbum.media,
                    selectedKeys = selectedKeys,
                    selectionMode = selectionMode,
                    onOpen = { openMedia(it, selectedAlbum.media) },
                    onToggleSelection = { toggleSelection(listOf(it)) },
                )
            }
            media.isEmpty() -> EmptyState(
                Icons.Default.Image,
                if (source == ResultSource.LOCAL) "暂无本地作品\n请在参数页长按输出部件加入全工作流保存白名单" else "云端暂无图片或视频",
            )
            layout == ResultLayout.ALL -> ResultMediaGrid(
                media = media,
                selectedKeys = selectedKeys,
                selectionMode = selectionMode,
                onOpen = { openMedia(it, media) },
                onToggleSelection = { toggleSelection(listOf(it)) },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                gridItems(albums, key = { it.jobId }) { album ->
                    val albumKeys = album.media.map(ResultMedia::stableKey).toSet()
                    AlbumTile(
                        album = album,
                        selected = albumKeys.isNotEmpty() && albumKeys.all { it in selectedKeys },
                        partiallySelected = albumKeys.any { it in selectedKeys },
                        selectionMode = selectionMode,
                        onClick = { onSelectedAlbumChange(album.jobId) },
                        onToggleSelection = { toggleSelection(album.media) },
                    )
                }
            }
        }
    }
    selectedMedia?.let { item ->
        AlertDialog(
            onDismissRequest = { selectedMedia = null },
            title = { Text(item.filename, maxLines = 2) },
            text = {
                if (item.kind == MediaKind.IMAGE) {
                    AsyncImage(item.url, item.filename, Modifier.fillMaxWidth().fillMaxHeight(0.72f), contentScale = ContentScale.Fit)
                } else {
                    VideoPlayer(item.url)
                }
            },
            confirmButton = { TextButton(onClick = { selectedMedia = null }) { Text("关闭") } },
            dismissButton = {
                Row {
                    IconButton(onClick = { viewModel.saveResult(item) }) { Icon(Icons.Default.Download, "保存到系统相册") }
                    IconButton(onClick = { viewModel.shareResult(item) }) { Icon(Icons.Default.Share, "分享") }
                    IconButton(onClick = { viewModel.openResult(item) }) { Icon(Icons.Default.FileOpen, "打开原文件") }
                }
            },
        )
    }
    if (galleryItems.isNotEmpty()) {
        ImageGalleryViewer(
            items = galleryItems,
            initialIndex = galleryInitialIndex,
            onDismiss = { galleryItems = emptyList() },
            onSave = viewModel::saveResultWithFeedback,
            onShare = viewModel::shareResult,
            onOpen = viewModel::openResult,
            favoriteKeys = state.favoriteResultKeys,
            onFavorite = viewModel::toggleResultFavorite,
            onDelete = { item ->
                galleryItems = galleryItems.filterNot { it.stableKey() == item.stableKey() }
                viewModel.deleteLocalResults(listOf(item))
            },
        )
    }
    if (confirmDeleteSelection) {
        ConfirmDialog(
            title = "删除所选作品",
            message = "将删除手机本地缓存中的 ${selectedItems.size} 项作品，不会删除电脑上的文件。",
            onDismiss = { confirmDeleteSelection = false },
        ) {
            viewModel.deleteLocalResults(selectedItems)
            selectedKeys = emptySet()
            confirmDeleteSelection = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultMediaGrid(
    media: List<ResultMedia>,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    onOpen: (ResultMedia) -> Unit,
    onToggleSelection: (ResultMedia) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(105.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItems(media, key = { it.stableKey() }) { item ->
            val selected = item.stableKey() in selectedKeys
            Card(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selectionMode) onToggleSelection(item) else onOpen(item) },
                    onLongClick = { onToggleSelection(item) },
                ),
            ) {
                Box {
                    MediaCover(item, Modifier.fillMaxWidth().aspectRatio(1f))
                    if (selected) {
                        Box(
                            Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                        )
                        Icon(
                            Icons.Default.CheckCircle,
                            "已选择",
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(item.filename, Modifier.padding(6.dp), maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumTile(
    album: ResultAlbum,
    selected: Boolean,
    partiallySelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val cover = album.media.firstOrNull { it.kind == MediaKind.IMAGE } ?: album.media.first()
    Card(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selectionMode) onToggleSelection() else onClick() },
            onLongClick = onToggleSelection,
        ),
    ) {
        Box {
            MediaCover(cover, Modifier.fillMaxWidth().aspectRatio(1.15f))
            if (selected || partiallySelected) {
                Box(
                    Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.24f else 0.12f)),
                )
                Icon(
                    Icons.Default.CheckCircle,
                    if (selected) "已选择整个相册" else "已选择部分作品",
                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(albumTitle(album), maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    album.media.maxOfOrNull(ResultMedia::createdAt)?.takeIf { it > 0L }?.let(::formatTime).orEmpty(),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                Text("${album.media.size} 项", maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MediaCover(media: ResultMedia, modifier: Modifier = Modifier) {
    if (media.kind == MediaKind.IMAGE) {
        AsyncImage(
            model = previewUrl(media),
            contentDescription = media.filename,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.VideoFile, "视频", Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun albumTitle(album: ResultAlbum): String {
    val first = album.media.first()
    return first.workflowName.ifBlank {
        if (first.taskNumber > 0L) "任务 #${first.taskNumber}" else "任务 ${album.jobId.take(8)}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageGalleryViewer(
    items: List<ResultMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSave: (ResultMedia, (String) -> Unit) -> Unit,
    onShare: (ResultMedia) -> Unit,
    onOpen: (ResultMedia) -> Unit,
    favoriteKeys: Set<String>,
    onFavorite: (ResultMedia) -> Unit,
    onDelete: (ResultMedia) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(items.indices)) { items.size }
    val pagerScope = rememberCoroutineScope()
    val transform = remember { GalleryTransformState() }
    val current = items[pagerState.currentPage.coerceIn(items.indices)]
    var chromeVisible by remember { mutableStateOf(true) }
    var moreExpanded by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saveFeedback by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val isFavorite = current.stableKey() in favoriteKeys
    LaunchedEffect(saveFeedback, saving) {
        if (!saving && saveFeedback != null) {
            kotlinx.coroutines.delay(2_500)
            saveFeedback = null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            GallerySystemBars(chromeVisible)
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    ZoomableGalleryImage(
                        media = items[page],
                        transform = transform,
                        onTap = { chromeVisible = !chromeVisible },
                        onZoom = { chromeVisible = false },
                    )
                }
                if (pagerState.currentPage > 0) {
                    Surface(
                        modifier = Modifier.align(Alignment.CenterStart).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = CircleShape,
                    ) {
                        IconButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                            Icon(Icons.Default.ChevronLeft, "上一张", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }
                if (pagerState.currentPage < items.lastIndex) {
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(4.dp),
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = CircleShape,
                    ) {
                        IconButton(onClick = { pagerScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                            Icon(Icons.Default.ChevronRight, "下一张", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                    }
                }
                if (chromeVisible) {
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter)
                            .background(Color.Black.copy(alpha = 0.62f)).padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭", tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(
                                current.createdAt.takeIf { it > 0L }?.let(::formatTime) ?: current.filename,
                                color = Color.White,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "单击沉浸 · 双击缩放 · 左右换图",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text("${pagerState.currentPage + 1}/${items.size}", color = Color.White)
                    }
                    Row(
                        Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.68f)).navigationBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GalleryAction(Icons.Default.Share, "分享") { onShare(current) }
                        GalleryAction(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (isFavorite) "已收藏" else "收藏",
                            tint = if (isFavorite) Color(0xFFFF5A6F) else Color.White,
                        ) { onFavorite(current) }
                        GalleryAction(
                            if (current.source == ResultSource.CLOUD) Icons.Default.Download else Icons.Default.Save,
                            if (saving) "${if (current.source == ResultSource.CLOUD) "下载" else "保存"}中" else if (current.source == ResultSource.CLOUD) "下载" else "保存",
                            enabled = !saving,
                        ) {
                            saving = true
                            saveFeedback = if (current.source == ResultSource.CLOUD) "正在下载…" else "正在保存…"
                            onSave(current) { message ->
                                saving = false
                                saveFeedback = message
                            }
                        }
                        GalleryAction(
                            Icons.Default.Delete,
                            "删除",
                            enabled = current.source == ResultSource.LOCAL,
                        ) { confirmDelete = true }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                Modifier.fillMaxWidth().clickable { moreExpanded = true }.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(Icons.Default.MoreHoriz, "更多", tint = Color.White)
                                Text("更多", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("打开原文件") },
                                    leadingIcon = { Icon(Icons.Default.FileOpen, null) },
                                    onClick = { moreExpanded = false; onOpen(current) },
                                )
                                DropdownMenuItem(
                                    text = { Text("文件信息") },
                                    leadingIcon = { Icon(Icons.Default.Image, null) },
                                    onClick = { moreExpanded = false; showInfo = true },
                                )
                            }
                        }
                    }
                }
                saveFeedback?.let { message ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (chromeVisible) 72.dp else 20.dp),
                        color = Color.Black.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(message, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("删除本地作品", "将从 App 本地缓存中删除 ${current.filename}，不会删除电脑上的原文件。", { confirmDelete = false }) {
            confirmDelete = false
            onDelete(current)
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("文件信息") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.filename)
                    Text("任务：${current.jobId}", style = MaterialTheme.typography.bodySmall)
                    Text("输出部件：${current.nodeId}", style = MaterialTheme.typography.bodySmall)
                    Text("来源：${if (current.source == ResultSource.LOCAL) "本地缓存" else "ComfyUI 服务器"}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun GallerySystemBars(chromeVisible: Boolean) {
    val view = LocalView.current
    val window = remember(view) {
        (view.parent as? DialogWindowProvider)?.window ?: view.context.findActivity()?.window
    }
    LaunchedEffect(chromeVisible, window) {
        window?.let {
            WindowCompat.getInsetsController(it, view).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.navigationBars())
                if (chromeVisible) show(WindowInsetsCompat.Type.statusBars()) else hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    DisposableEffect(window) {
        onDispose { window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) } }
    }
}

private class GalleryTransformState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
}

@Composable
private fun RowScope.GalleryAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).alpha(if (enabled) 1f else 0.34f)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ZoomableGalleryImage(
    media: ResultMedia,
    transform: GalleryTransformState,
    onTap: () -> Unit,
    onZoom: () -> Unit,
) {
    var viewport by remember(media.localPath, media.url) { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(media.localPath, media.url) { mutableStateOf(IntSize.Zero) }
    Box(
        Modifier.fillMaxSize().onSizeChanged { viewport = it }
            .pointerInput(media.localPath, media.url, viewport, imageSize) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        onZoom()
                        if (transform.scale > 1.05f) {
                            transform.scale = 1f
                            transform.offset = Offset.Zero
                        } else {
                            val viewportAspect = viewport.width.toFloat() / viewport.height.coerceAtLeast(1)
                            val imageAspect = imageSize.width.toFloat() / imageSize.height.coerceAtLeast(1)
                            val fillWidthScale = if (imageAspect > 0f && imageAspect < viewportAspect) viewportAspect / imageAspect else 1f
                            transform.scale = (if (fillWidthScale > 1.05f) fillWidthScale else 2f).coerceIn(1f, 5f)
                            transform.offset = Offset.Zero
                        }
                    },
                )
            }.pointerInput(media.localPath, media.url) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    val zoom = event.calculateZoom()
                    val pan = event.calculatePan()
                    val isMultiTouch = event.changes.count { it.pressed } > 1
                    val isMoving = pan.getDistance() > 0.5f
                    if (isMultiTouch || zoom != 1f || (transform.scale > 1f && isMoving)) {
                        if (isMultiTouch || zoom != 1f) onZoom()
                        val newScale = (transform.scale * zoom).coerceIn(1f, 5f)
                        if (newScale <= 1f) {
                            transform.offset = Offset.Zero
                        } else {
                            val maxX = viewport.width * (newScale - 1f) / 2f
                            val maxY = viewport.height * (newScale - 1f) / 2f
                            transform.offset = Offset(
                                x = (transform.offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (transform.offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                        transform.scale = newScale
                        event.changes.forEach { it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = media.url,
            contentDescription = media.filename,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = transform.offset.x
                translationY = transform.offset.y
            },
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                imageSize = IntSize(
                    state.result.drawable.intrinsicWidth.coerceAtLeast(1),
                    state.result.drawable.intrinsicHeight.coerceAtLeast(1),
                )
            },
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
    val context = LocalContext.current
    var confirmDeleteLocal by remember { mutableStateOf(false) }
    var showDiagnosticLog by remember { mutableStateOf(false) }
    var diagnosticLog by remember { mutableStateOf("") }
    var pendingLogExport by remember { mutableStateOf("") }
    val logExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val success = uri != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingLogExport) }
                ?: error("无法创建日志文件")
        }.isSuccess
        pendingLogExport = ""
        if (uri != null) viewModel.reportDiagnosticLogExport(success)
    }
    if (showDiagnosticLog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticLog = false },
            title = { Text("诊断日志") },
            text = {
                Text(
                    diagnosticLog,
                    modifier = Modifier.fillMaxWidth().height(420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showDiagnosticLog = false }) { Text("关闭") } },
        )
        return
    }
    if (confirmDeleteLocal) {
        ConfirmDialog(
            title = "删除全部本地作品",
            message = "只删除手机中现有的 ${state.localResults.size} 项本地作品，不会删除电脑端云端资产。白名单仍会生效，之后新生成的结果仍会保存到手机；已经保存的本地作品不会因云端以后删除而消失。",
            onDismiss = { confirmDeleteLocal = false },
            onConfirm = {
                viewModel.clearLocalCache()
                confirmDeleteLocal = false
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("服务器", style = MaterialTheme.typography.titleSmall)
                Text(state.activeServer?.baseUrl ?: "尚未连接")
                state.activeServer?.lastSeen?.takeIf { it > 0L }?.let { Text("最后在线：${formatTime(it)}") }
                state.systemStats?.let { stats ->
                    Text("ComfyUI ${stats.comfyVersion} · 前端 ${stats.frontendVersion}")
                    stats.devices.forEach { Text("${it.name}\n显存 ${formatSize(it.vramFree)} / ${formatSize(it.vramTotal)} 可用") }
                }
                HorizontalDivider()
                Text("本地作品保存白名单", style = MaterialTheme.typography.titleSmall)
                Text(
                    "白名单按输出部件类型对所有工作流生效，不再绑定某一个工作流。只保存本 App 提交的任务；电脑浏览器提交的任务不会进入本地。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.cacheOutputRules.isEmpty()) {
                    Text("尚未添加输出部件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.cacheOutputRules.forEach { rule ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rule.nodeTitle.ifBlank { rule.nodeType }, style = MaterialTheme.typography.titleSmall)
                                    Text("${rule.nodeType} · 适用于所有工作流", maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    Text(rule.serverUrl, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                                }
                                Switch(rule.enabled, { viewModel.setCacheRuleEnabled(rule, it) })
                                IconButton(onClick = { viewModel.removeCacheRule(rule) }) { Icon(Icons.Default.Delete, "删除白名单") }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { confirmDeleteLocal = true }, enabled = state.localResults.isNotEmpty()) {
                    Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("删除全部本地作品（${state.localResults.size} 项）")
                }
                HorizontalDivider()
                Text("诊断日志", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("记录运行和闪退日志")
                        Text("不记录提示词、工作流正文或生成图片", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(state.loggingEnabled, viewModel::setLoggingEnabled)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        diagnosticLog = viewModel.diagnosticLog()
                        showDiagnosticLog = true
                    }) { Text("查看日志") }
                    OutlinedButton(onClick = {
                        pendingLogExport = viewModel.diagnosticLog()
                        logExportLauncher.launch("ComfyUIMobile-${System.currentTimeMillis()}.log")
                    }) { Text("导出日志") }
                    TextButton(onClick = viewModel::clearDiagnosticLog) { Text("清空日志") }
                }
                HorizontalDivider()
                Text("软件更新", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { viewModel.checkUpdate() }) { Text("检查更新") }
                state.updateInfo?.let { info ->
                    Text("发现 ${info.tag}")
                    Button(onClick = viewModel::downloadUpdate) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(4.dp)); Text("下载并安装") }
                }
                if (state.activeServer != null) {
                    OutlinedButton(onClick = { viewModel.disconnect(); onDismiss() }) { Icon(Icons.Default.CloudOff, null); Spacer(Modifier.width(4.dp)); Text("断开连接") }
                }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun previewUrl(media: ResultMedia): String =
    if (media.kind == MediaKind.IMAGE && media.source == ResultSource.CLOUD) "${media.url}&preview=webp;90" else media.url
