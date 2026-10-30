package com.local.comfyuimobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PhotoAlbum
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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.AdvancedEditorActivity
import com.local.comfyuimobile.bridge.ComfyBridge
import com.local.comfyuimobile.bridge.FieldValidator
import com.local.comfyuimobile.bridge.ImageListValue
import com.local.comfyuimobile.data.CachePolicy
import com.local.comfyuimobile.data.ResultParameterReport
import com.local.comfyuimobile.data.RecentWorkflows
import com.local.comfyuimobile.data.WorkflowBrowser
import com.local.comfyuimobile.data.WorkflowPath
import com.local.comfyuimobile.model.AppDestination
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
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.launch

private const val IME_RELOCATION_SUPPRESSION_MILLIS = 700L

private enum class MainPage(val label: String, val icon: ImageVector) {
    WORKFLOWS("工作流", Icons.Default.Folder),
    PARAMETERS("参数", Icons.Default.Tune),
    RESULTS("结果", Icons.Default.Image),
    BACKEND("后台", Icons.Default.Hub),
}

private enum class ResultLayout { ALL, ALBUMS }
private data class ResultAlbum(val jobId: String, val media: List<ResultMedia>)

@Composable
fun ComfyMobileApp(viewModel: MainViewModel, bridge: ComfyBridge) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val advancedEditorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.finishAdvancedEditor(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(state.error, state.notice) {
        val message = state.error ?: state.notice
        if (!message.isNullOrBlank()) {
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.advancedEditor) {
        if (state.advancedEditor) {
            advancedEditorLauncher.launch(
                Intent(context, AdvancedEditorActivity::class.java)
                    .putExtra(
                        AdvancedEditorActivity.EXTRA_SERVER_URL,
                        state.activeServer?.baseUrl.orEmpty(),
                    )
                    .putExtra(
                        AdvancedEditorActivity.EXTRA_WORKFLOW_PATH,
                        state.selectedWorkflow?.entry?.path.orEmpty(),
                    ),
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(if (showSettings) Modifier.clearAndSetSemantics { } else Modifier) {
            if (state.activeServer == null) {
                ConnectionPage(state, viewModel, snackbar, onOpenSettings = { showSettings = true })
            } else {
                ConnectedApp(state, viewModel, snackbar, onOpenSettings = { showSettings = true })
            }
        }
        key(bridge.webView) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        addView(bridge.webView.apply {
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                },
                modifier = Modifier.size(1.dp).alpha(0f).clearAndSetSemantics { },
                update = { container ->
                    container.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    bridge.webView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                },
            )
        }
        if (state.loading && state.advancedEditor) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text("正在准备高级编辑…")
                }
            }
        }
        if (showSettings) {
            SettingsScreen(state, viewModel, snackbar) { showSettings = false }
        }
    }
}

@Composable
private fun ConnectionPage(state: AppUiState, viewModel: MainViewModel, snackbar: SnackbarHostState, onOpenSettings: () -> Unit) {
    ConnectionScreen(
        state = state, snackbar = snackbar, onOpenSettings = onOpenSettings,
        onServerInput = viewModel::setServerInput,
        onConnect = { address -> viewModel.setServerInput(address); viewModel.connect(address) },
        onScan = viewModel::scanLan, onRemoveServer = viewModel::removeServer,
    )
}

private val connectionStepNames = listOf(
    "检查地址格式",
    "读取服务器信息",
    "打开 ComfyUI 网页",
    "初始化前端",
    "读取节点定义",
    "同步连接数据",
)

@Composable
internal fun ConnectionProgressCard(state: AppUiState) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectedApp(state: AppUiState, viewModel: MainViewModel, snackbar: SnackbarHostState, onOpenSettings: () -> Unit) {
    val expanded = LocalConfiguration.current.screenWidthDp >= 720
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var page by rememberSaveable { mutableStateOf(MainPage.WORKFLOWS) }
    val pageStates = rememberSaveableStateHolder()
    var resultSource by rememberSaveable { mutableStateOf(ResultSource.LOCAL) }
    var resultLayout by rememberSaveable { mutableStateOf(ResultLayout.ALBUMS) }
    var resultAlbumId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = page == MainPage.RESULTS && resultAlbumId != null) {
        resultAlbumId = null
    }
    LaunchedEffect(state.navigationRequest?.id) {
        val request = state.navigationRequest ?: return@LaunchedEffect
        page = when (request.destination) {
            AppDestination.PARAMETERS -> MainPage.PARAMETERS
            AppDestination.RESULTS -> MainPage.RESULTS
        }
        if (request.destination == AppDestination.RESULTS && request.jobId != null) {
            resultLayout = ResultLayout.ALBUMS
            resultAlbumId = request.jobId
            resultSource = if (state.localResults.any { it.jobId == request.jobId }) {
                ResultSource.LOCAL
            } else {
                ResultSource.CLOUD
            }
            viewModel.refreshResults()
            viewModel.refreshLocalResults()
        }
        viewModel.consumeNavigationRequest(request.id)
    }
    LaunchedEffect(resultAlbumId, state.localResults, state.results) {
        val jobId = resultAlbumId ?: return@LaunchedEffect
        if (state.localResults.any { it.jobId == jobId }) resultSource = ResultSource.LOCAL
        else if (state.results.any { it.jobId == jobId }) resultSource = ResultSource.CLOUD
    }
    val toolbar: @Composable (Modifier) -> Unit = { modifier ->
        StudioToolbar(
            title = page.label,
            parameterState = state.takeIf { page == MainPage.PARAMETERS && it.selectedWorkflow != null },
            snackbar = snackbar,
            connectionLabel = if (page != MainPage.BACKEND && (state.status != ConnectionStatus.CONNECTED || !state.bridgeReady)) backendConnectionLabel(state) else null,
            onOpenBackend = { page = MainPage.BACKEND }, onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            toolbar(Modifier.windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top)))
        },
        bottomBar = {
            if (!expanded && !imeVisible) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainPage.entries.forEach { target ->
                        NavigationBarItem(
                            selected = page == target,
                            onClick = { page = target },
                            icon = { Icon(target.icon, null) },
                            label = { Text(target.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = {
            if (page != MainPage.PARAMETERS || state.selectedWorkflow == null) SnackbarHost(snackbar)
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (expanded) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(16.dp))
                    MainPage.entries.forEach { target ->
                        NavigationRailItem(
                            selected = page == target,
                            onClick = { page = target },
                            icon = { Icon(target.icon, null) },
                            label = { Text(target.label) },
                            modifier = Modifier.padding(vertical = 6.dp),
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                pageStates.SaveableStateProvider(page.name) {
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
                        MainPage.BACKEND -> BackendScreen(state, viewModel)
                    }
                }
                if (state.loading || state.generating) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
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
    var selectedEntryPath by rememberSaveable(state.activeServer?.baseUrl) { mutableStateOf<String?>(null) }
    BackHandler(enabled = currentFolder != WorkflowBrowser.ROOT) {
        currentFolder = WorkflowBrowser.up(currentFolder)
        search = ""
        selectedEntryPath = null
    }
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
        if (selectedEntryPath != null && state.workflows.none { !it.isDirectory && it.path == selectedEntryPath }) {
            selectedEntryPath = null
        }
    }
    val selectedEntry = state.workflows.firstOrNull { !it.isDirectory && it.path == selectedEntryPath }
    val actionEntry = selectedEntry ?: state.previewWorkflow?.entry
    val actionFolders = remember(state.workflows, actionEntry?.path) {
        actionEntry?.let { WorkflowPath.availableFolders(state.workflows, it.path) }.orEmpty()
    }
    var actionMenuExpanded by remember(actionEntry?.path) { mutableStateOf(false) }
    val filtered = WorkflowBrowser.entries(state.workflows, currentFolder, search)
    val selectionPanel: @Composable () -> Unit = {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (actionEntry != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("当前选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(actionEntry.name.removeSuffix(".json"), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { actionMenuExpanded = true }) { Icon(Icons.Default.MoreHoriz, "工作流操作") }
                            DropdownMenu(expanded = actionMenuExpanded, onDismissRequest = { actionMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("新建副本") }, onClick = { actionMenuExpanded = false; dialogText = actionEntry.name.substringBeforeLast('.'); duplicateDialog = true })
                                DropdownMenuItem(text = { Text("改名") }, onClick = { actionMenuExpanded = false; dialogText = actionEntry.name.substringBeforeLast('.'); renameDialog = true })
                                DropdownMenuItem(text = { Text("移动") }, onClick = { actionMenuExpanded = false; moveDialog = true })
                                DropdownMenuItem(text = { Text("导出") }, onClick = {
                                    actionMenuExpanded = false
                                    viewModel.exportWorkflow(actionEntry) { name, raw -> exportRaw = raw; exportLauncher.launch(name) }
                                })
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("删除工作流", color = MaterialTheme.colorScheme.error) }, onClick = { actionMenuExpanded = false; deleteDialog = true })
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (state.previewWorkflow?.entry?.path == actionEntry.path) viewModel.openPreviewedWorkflow()
                            else viewModel.selectWorkflow(actionEntry, recordAsOpened = true)
                            selectedEntryPath = null
                            onOpenParameters()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("打开参数")
                    }
                } else {
                    Icon(Icons.Default.Hub, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("准备好开始创作", style = MaterialTheme.typography.titleMedium)
                    Text("选择左侧工作流，再打开参数。双击工作流也可直接打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val browserHeader: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.bridgeReady) {
                OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            when (state.status) {
                                ConnectionStatus.ERROR -> "连接出现问题，请到后台页重新连接"
                                ConnectionStatus.RECONNECTING -> "正在重连，当前工作流会尽量保留"
                                else -> "正在准备工作流，可先浏览列表"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.status != ConnectionStatus.ERROR) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("搜索工作流") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentFolder != WorkflowBrowser.ROOT && search.isBlank()) {
                    IconButton(onClick = { currentFolder = WorkflowBrowser.up(currentFolder) }) { Icon(Icons.Default.ChevronLeft, "上一级") }
                }
                Text(
                    if (search.isNotBlank()) "搜索结果" else if (currentFolder == WorkflowBrowser.ROOT) "全部工作流" else currentFolder.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${filtered.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "image/*")) }) {
                    Icon(Icons.Default.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("导入")
                }
            }
        }
    }
    val listContent: @Composable (Modifier, Boolean) -> Unit = { modifier, includeSelection ->
        LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "browser-controls") { browserHeader() }
            if (includeSelection && actionEntry != null) item(key = "current-selection") { selectionPanel() }
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Folder, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (search.isNotBlank()) "没有匹配的工作流" else "这里还没有工作流", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("可从上方导入工作流文件或图片", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(filtered, key = { it.path }) { entry ->
                WorkflowRow(
                    entry = entry,
                    selected = if (selectedEntryPath != null) selectedEntryPath == entry.path else state.previewWorkflow?.entry?.path == entry.path,
                    onClick = {
                        if (entry.isDirectory) { currentFolder = entry.path; search = "" }
                        else { selectedEntryPath = null; viewModel.selectWorkflow(entry) }
                    },
                    onLongClick = if (entry.isDirectory) null else ({ selectedEntryPath = entry.path }),
                    onDoubleClick = {
                        if (!entry.isDirectory) {
                            selectedEntryPath = null
                            viewModel.selectWorkflow(entry, recordAsOpened = true)
                            onOpenParameters()
                        }
                    },
                )
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 660.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                listContent(Modifier.weight(1f).fillMaxHeight(), false)
                Column(Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    selectionPanel()
                    Text("点击预览，打开参数后再编辑。保存时才会写入服务器工作流。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else listContent(Modifier.fillMaxSize().padding(horizontal = 20.dp), true)
    }
    if (duplicateDialog && actionEntry != null) NameDialog("复制为新工作流", dialogText, { duplicateDialog = false }) {
        viewModel.duplicateWorkflow(actionEntry, it); duplicateDialog = false; selectedEntryPath = null
    }
    if (renameDialog && actionEntry != null) NameDialog("工作流改名", dialogText, { renameDialog = false }) {
        viewModel.renameWorkflow(actionEntry, it); renameDialog = false; selectedEntryPath = null
    }
    if (moveDialog && actionEntry != null) MoveWorkflowDialog(
        initialFolder = actionEntry.path.substringBeforeLast('/', "workflows"),
        folders = actionFolders,
        onDismiss = { moveDialog = false },
    ) { folder ->
        viewModel.moveWorkflow(actionEntry, folder); moveDialog = false; selectedEntryPath = null
    }
    if (deleteDialog && actionEntry != null) ConfirmDialog(
        "删除工作流",
        "将从 ComfyUI 服务器永久删除 ${actionEntry.name}。",
        { deleteDialog = false },
    ) { viewModel.deleteWorkflow(actionEntry); deleteDialog = false; selectedEntryPath = null }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkflowRow(
    entry: WorkflowEntry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick,
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Icon(if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Hub, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name.removeSuffix(".json"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (entry.isDirectory) "文件夹" else "工作流 · ${formatSize(entry.size)}", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.CheckCircle, "已选择", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            else if (entry.isDirectory) Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParameterScreen(state: AppUiState, viewModel: MainViewModel) {
    val workflow = state.selectedWorkflow
    if (workflow == null) {
        ParameterHistoryScreen(state, viewModel)
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
    var saveAsFolder by remember(workflow.entry.path) {
        mutableStateOf(workflow.entry.path.substringBeforeLast('/', "workflows"))
    }
    var confirmGenerateWithoutLocalOutput by remember { mutableStateOf(false) }
    val multilineEditorStates = remember(workflow.entry.path) { mutableStateMapOf<String, TextFieldValue>() }
    val imageUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val field = uploadField
        if (uri != null && field != null) viewModel.uploadField(field, uri)
        uploadField = null
    }
    val imageListUploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 16),
    ) { uris ->
        val field = uploadField
        if (uris.isNotEmpty() && field != null) viewModel.uploadImageListField(field, uris)
        uploadField = null
    }
    val videoUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val field = uploadField
        if (uri != null && field != null) viewModel.uploadField(field, uri)
        uploadField = null
    }
    val visibleFields = state.fields.filter { it.visible }.groupBy { it.nodeId }
    val nodes = workflow.nodes.sortedBy { it.order }.map { node ->
        val fields = visibleFields[node.id].orEmpty().sortedBy { it.order }
        node to fields
    }
    val workflowFolders = remember(state.workflows, workflow.entry.path) {
        WorkflowPath.availableFolders(state.workflows, workflow.entry.path)
    }
    val outputNodeTypes = nodes.asSequence().map { it.first }.filter { it.isOutput }.map { it.type }.toSet()
    val hasConfiguredLocalOutput = CachePolicy.hasConfiguredOutput(
        state.cacheOutputRules,
        state.activeServer?.baseUrl,
        outputNodeTypes,
    )
    val localProblems = FieldValidator.detailedProblems(state.fields)
    val localProblemsByNode = localProblems.groupBy { it.nodeId }.mapValues { (_, items) -> items.map { it.message } }
    val problemNodeIds = localProblemsByNode.keys + state.nodeProblems.keys
    val showActions = WindowInsets.ime.getBottom(LocalDensity.current) == 0
    val recentWorkflows = RecentWorkflows.resolveEntries(state.recentWorkflowPaths, state.workflows)
        .let { entries -> listOf(workflow.entry) + entries.filterNot { it.path == workflow.entry.path } }
        .distinctBy { it.path }
    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val suppressAutomaticRelocationUntil = remember(workflow.entry.path) { AtomicLong(0L) }
    val parameterBringIntoViewSpec = remember(workflow.entry.path, defaultBringIntoViewSpec) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (SystemClock.uptimeMillis() < suppressAutomaticRelocationUntil.get()) {
                    0f
                } else {
                    defaultBringIntoViewSpec.calculateScrollDistance(offset, size, containerSize)
                }
        }
    }
    LaunchedEffect(problemNodeIds) {
        if (problemNodeIds.isNotEmpty()) expandedNodeIds = expandedNodeIds + problemNodeIds
    }
    var toolbarMenuExpanded by remember(workflow.entry.path) { mutableStateOf(false) }
    val parameterScroll = rememberScrollState()
    val parameterScope = rememberCoroutineScope()
    val nodeRequesters = remember(workflow.entry.path, workflow.nodes) { nodes.associate { it.first.id to BringIntoViewRequester() } }
    val parameterCards: @Composable (Modifier) -> Unit = { modifier ->
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides parameterBringIntoViewSpec,
        ) {
            Column(
                modifier.verticalScroll(parameterScroll).padding(top = 4.dp, bottom = if (showActions) 96.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { recentMenuExpanded = true },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("当前工作流", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(workflow.entry.name.removeSuffix(".json"), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.ExpandMore, "选择最近打开的工作流", Modifier.size(20.dp))
                                }
                                DropdownMenu(expanded = recentMenuExpanded, onDismissRequest = { recentMenuExpanded = false }) {
                                    recentWorkflows.forEach { entry ->
                                        DropdownMenuItem(
                                            text = { Text(entry.name.removeSuffix(".json"), maxLines = 2) },
                                            leadingIcon = { Icon(if (entry.path == workflow.entry.path) Icons.Default.CheckCircle else Icons.Default.History, null) },
                                            onClick = { recentMenuExpanded = false; if (entry.path != workflow.entry.path) viewModel.selectWorkflow(entry, recordAsOpened = true) },
                                        )
                                    }
                                }
                            }
                            FilledTonalButton(
                                onClick = { viewModel.saveWorkflow() },
                                enabled = !state.loading && !state.generating,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            ) { Text("保存", maxLines = 1) }
                            Box {
                                IconButton(onClick = { toolbarMenuExpanded = true }) { Icon(Icons.Default.MoreHoriz, "参数页操作") }
                                DropdownMenu(expanded = toolbarMenuExpanded, onDismissRequest = { toolbarMenuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("另存工作流") }, onClick = {
                                        toolbarMenuExpanded = false
                                        saveAsName = workflow.entry.name.substringBeforeLast('.') + "-副本"
                                        saveAsFolder = workflow.entry.path.substringBeforeLast('/', "workflows")
                                        saveAsDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("表单布局") }, onClick = { toolbarMenuExpanded = false; layoutDialog = true })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioBadge("${nodes.size} 个部件")
                            Text("${state.fields.count { it.visible }} 个参数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (workflow.isTemporary || workflow.hasUnsavedChanges) {
                            Text(
                                if (workflow.isTemporary) "临时工作流 · 另存后可长期保留" else "有未保存的修改",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                nodes.forEach { (node, fields) ->
                    key(node.id) {
                        val nodeId = node.id
                        NodeParameterCard(
                            modifier = Modifier.bringIntoViewRequester(nodeRequesters.getValue(nodeId)),
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
                                if (field.kind == ParameterKind.VIDEO) {
                                    videoUploadLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                                    )
                                } else if (field.kind == ParameterKind.IMAGE_LIST) {
                                    imageListUploadLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                } else {
                                    imageUploadLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                            },
                            multilineEditorStates = multilineEditorStates,
                            onMultilineFocusGained = {
                                suppressAutomaticRelocationUntil.set(
                                    SystemClock.uptimeMillis() + IME_RELOCATION_SUPPRESSION_MILLIS,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (LocalConfiguration.current.screenWidthDp >= 720) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    modifier = Modifier.width(174.dp).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text("流程目录", Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall)
                    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(nodes, key = { it.first.id }) { (node, _) ->
                            val active = state.currentExecutingNodeId == node.id
                            Row(
                                Modifier.fillMaxWidth().background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable {
                                        expandedNodeIds = expandedNodeIds + node.id
                                        parameterScope.launch { kotlinx.coroutines.delay(80); nodeRequesters[node.id]?.bringIntoView() }
                                    }.padding(horizontal = 10.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("${node.order + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(node.title.ifBlank { node.type }, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                parameterCards(Modifier.weight(1f).fillMaxHeight())
            }
        } else parameterCards(Modifier.fillMaxSize())
        if (showActions) ParameterActionDock(
            onAdvancedEditor = viewModel::openAdvancedEditor,
            onGenerate = { if (hasConfiguredLocalOutput) viewModel.generate() else confirmGenerateWithoutLocalOutput = true },
            advancedEnabled = state.bridgeReady && !state.loading && !state.generating,
            generateEnabled = !state.generating && !state.loading && state.bridgeReady && localProblems.isEmpty(),
            modifier = Modifier.align(if (LocalConfiguration.current.screenWidthDp >= 720) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(bottom = 16.dp).widthIn(max = 360.dp),
        )
    }

    if (state.workflowDraftConflictRequired) {
        WorkflowDraftConflictDialog(
            message = state.workflowDraftConflictReason,
            onKeepLocal = viewModel::dismissWorkflowDraftConflict,
            onLoadServer = viewModel::discardLocalWorkflowDraft,
            onSaveAs = {
                viewModel.dismissWorkflowDraftConflict()
                saveAsName = workflow.entry.name.substringBeforeLast('.') + "-本地草稿"
                saveAsFolder = workflow.entry.path.substringBeforeLast('/', "workflows")
                saveAsDialog = true
            },
        )
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
        SaveWorkflowAsDialog(
            initialName = saveAsName,
            initialFolder = saveAsFolder,
            folders = workflowFolders,
            onDismiss = { saveAsDialog = false },
        ) { name, folder ->
            viewModel.saveWorkflowAs(name, folder)
            saveAsDialog = false
        }
    }
    if (confirmGenerateWithoutLocalOutput) {
        ConfirmDialog(
            title = "尚未配置本地输出",
            message = "当前工作流没有命中任何已启用的本地保存输出。继续生成后，结果仍会保留在 ComfyUI 云端媒体资产，但不会进入手机本地结果。请长按输出部件加入白名单。是否仍然生成？",
            onDismiss = { confirmGenerateWithoutLocalOutput = false },
            confirmLabel = "仍然生成",
            onConfirm = {
                confirmGenerateWithoutLocalOutput = false
                viewModel.generate()
            },
        )
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
    multilineEditorStates: MutableMap<String, TextFieldValue>,
    onMultilineFocusGained: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = node.title.ifBlank { node.type.ifBlank { "未命名节点" } }
    val bringIntoViewRequester = remember(node.id) { BringIntoViewRequester() }
    LaunchedEffect(active) {
        if (active) {
            kotlinx.coroutines.delay(50)
            bringIntoViewRequester.bringIntoView()
        }
    }
    var headerHeightPx by remember(node.id) { mutableIntStateOf(0) }
    var inputMarkerHeightPx by remember(node.id) { mutableIntStateOf(0) }
    var outputMarkerHeightPx by remember(node.id) { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val inputMarkerTop = with(density) { ((headerHeightPx - inputMarkerHeightPx).coerceAtLeast(0) / 2f).toDp() }
    val outputMarkerTop = with(density) { ((headerHeightPx - outputMarkerHeightPx).coerceAtLeast(0) / 2f).toDp() }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 7.dp)
            .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                    Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeightPx = it.height }
                        .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ConnectionMarkerLabels(node.inputMarkers)
                    if (node.inputMarkers.isNotEmpty()) Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                if (node.type.isNotBlank() && node.type != title) append(node.type).append(" · ")
                                append(fields.size).append(" 个参数")
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
                    ConnectionMarkerLabels(node.outputMarkers)
                }
                if (expanded) {
                    HorizontalDivider()
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (fields.isEmpty()) {
                            Text("此部件没有可调整参数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (fields.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Column(Modifier.padding(horizontal = 2.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    fields.forEachIndexed { index, field ->
                                        ParameterEditor(
                                            field,
                                            viewModel,
                                            onHistory = { onHistory(field) },
                                            onUpload = { onUpload(field) },
                                            multilineEditorStates = multilineEditorStates,
                                            onMultilineFocusGained = onMultilineFocusGained,
                                        )
                                        if (index < fields.lastIndex) HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        ConnectionMarkerDashes(
            node.inputMarkers,
            input = true,
            onHeightChanged = { inputMarkerHeightPx = it },
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-7).dp, y = inputMarkerTop),
        )
        ConnectionMarkerDashes(
            node.outputMarkers,
            input = false,
            onHeightChanged = { outputMarkerHeightPx = it },
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = outputMarkerTop),
        )
    }
}

@Composable
private fun ConnectionMarkerLabels(markers: List<WorkflowConnectionMarker>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markers.forEach { marker ->
            Text(
                marker.label,
                color = connectionMarkerColor(marker.color),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ConnectionMarkerDashes(
    markers: List<WorkflowConnectionMarker>,
    input: Boolean,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.onSizeChanged { onHeightChanged(it.height) },
        verticalArrangement = Arrangement.spacedBy(3.dp),
        horizontalAlignment = if (input) Alignment.Start else Alignment.End,
    ) {
        markers.forEach { marker ->
            Box(contentAlignment = if (input) Alignment.CenterStart else Alignment.CenterEnd) {
                Text(
                    marker.label,
                    modifier = Modifier.alpha(0f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Box(
                    Modifier.width(14.dp).height(3.dp).background(
                        connectionMarkerColor(marker.color),
                        RoundedCornerShape(2.dp),
                    ),
                )
            }
        }
    }
}

private fun connectionMarkerColor(value: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(Color(0xFF9E9E9E))

@Composable
private fun ParameterEditor(
    field: ParameterField,
    viewModel: MainViewModel,
    onHistory: () -> Unit,
    onUpload: () -> Unit,
    multilineEditorStates: MutableMap<String, TextFieldValue>,
    onMultilineFocusGained: () -> Unit,
) {
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
                field.actions.forEach { action ->
                    FilledTonalButton(
                        onClick = { viewModel.invokeSeedAction(action) },
                        enabled = !field.linked && action.enabled,
                    ) { Text(action.label) }
                }
            }
            ParameterKind.IMAGE, ParameterKind.VIDEO -> {
                OutlinedTextField(
                    field.displayValue,
                    { viewModel.updateField(field.key, it) },
                    Modifier.fillMaxWidth(),
                    enabled = !field.linked,
                    singleLine = true,
                )
                FilledTonalButton(onClick = onUpload, enabled = !field.linked) {
                    Icon(if (field.kind == ParameterKind.VIDEO) Icons.Default.VideoFile else Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (field.kind == ParameterKind.VIDEO) "从视频相册选择并上传" else "从相册选择并上传")
                }
            }
            ParameterKind.IMAGE_LIST -> ImageListField(field, viewModel, onUpload)
            ParameterKind.MULTILINE -> MultilineTextField(
                field,
                viewModel,
                multilineEditorStates,
                onFocusGained = onMultilineFocusGained,
            )
            ParameterKind.TEXT -> OutlinedTextField(
                field.displayValue,
                { viewModel.updateField(field.key, it) },
                Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
        enabled = !field.linked,
        singleLine = true,
    )
}

@Composable
private fun MultilineTextField(
    field: ParameterField,
    viewModel: MainViewModel,
    editorStates: MutableMap<String, TextFieldValue>,
    onFocusGained: () -> Unit,
) {
    var editorValue by remember(field.key) {
        mutableStateOf(
            editorStates[field.key] ?: TextFieldValue(
                text = field.displayValue,
                selection = TextRange(field.displayValue.length),
            ),
        )
    }
    var focused by remember(field.key) { mutableStateOf(false) }
    LaunchedEffect(field.displayValue, focused) {
        if (!focused && field.displayValue != editorValue.text) {
            val updated = TextFieldValue(
                text = field.displayValue,
                selection = TextRange(field.displayValue.length),
            )
            editorValue = updated
            editorStates[field.key] = updated
        }
    }
    OutlinedTextField(
        value = editorValue,
        onValueChange = { value ->
            editorValue = value
            editorStates[field.key] = value
            if (value.text != field.displayValue) viewModel.updateField(field.key, value.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .onFocusChanged {
                val gainedFocus = it.isFocused && !focused
                focused = it.isFocused
                if (gainedFocus) onFocusGained()
            },
        enabled = !field.linked,
    )
}

@Composable
private fun ParameterHistoryScreen(state: AppUiState, viewModel: MainViewModel) {
    val recentWorkflows = RecentWorkflows.resolveEntries(state.recentWorkflowPaths, state.workflows)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.History, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text("最近打开的工作流", style = MaterialTheme.typography.titleLarge)
        Text(
            "点选一项即可恢复参数；也可以回到“工作流”页选择其他文件。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (recentWorkflows.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    "暂无历史记录，请先在工作流页打开一个工作流。",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            recentWorkflows.forEach { entry ->
                OutlinedCard(
                    Modifier.fillMaxWidth().clickable {
                        viewModel.selectWorkflow(entry, recordAsOpened = true)
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                entry.path.substringBeforeLast('/', "workflows"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "打开参数")
                    }
                }
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
    BackHandler(enabled = selectionMode) { selectedKeys = emptySet() }
    val resultHeader: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioChoices(
                labels = listOf("本地作品", "云端作品"),
                selected = if (source == ResultSource.LOCAL) 0 else 1,
                onSelect = { onSourceChange(if (it == 0) ResultSource.LOCAL else ResultSource.CLOUD) },
                modifier = Modifier.padding(top = 12.dp),
            )
            if (!selectionMode) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedAlbumId != null) {
                    IconButton(onClick = { onSelectedAlbumChange(null) }) { Icon(Icons.Default.ChevronLeft, "返回全部相册") }
                    Text("任务相册", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                } else {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (layout == ResultLayout.ALL) "全部作品" else "任务相册", style = MaterialTheme.typography.titleMedium)
                        Text("${media.size} 项作品 · 长按可多选", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onLayoutChange(if (layout == ResultLayout.ALL) ResultLayout.ALBUMS else ResultLayout.ALL) }) {
                        Icon(if (layout == ResultLayout.ALL) Icons.Default.PhotoAlbum else Icons.Default.GridView, if (layout == ResultLayout.ALL) "切换到任务相册" else "切换到全部平铺")
                    }
                }
                IconButton(onClick = { if (source == ResultSource.LOCAL) viewModel.refreshLocalResults() else viewModel.refreshResults() }) { Icon(Icons.Default.Refresh, "刷新作品") }
            }
            selectedAlbum?.let { Text(albumTitle(it), style = MaterialTheme.typography.titleMedium) }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (selectionMode) Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(top = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedKeys = emptySet() }) { Icon(Icons.Default.Close, "退出多选") }
                Text("已选 ${selectedItems.size} 项", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { selectedKeys = media.map(ResultMedia::stableKey).toSet() }) { Icon(Icons.Default.SelectAll, "全选") }
                IconButton(onClick = { viewModel.saveResults(selectedItems); selectedKeys = emptySet() }) {
                    Icon(if (source == ResultSource.CLOUD) Icons.Default.Download else Icons.Default.Save, if (source == ResultSource.CLOUD) "一键下载" else "一键保存")
                }
                if (source == ResultSource.LOCAL) IconButton(onClick = { confirmDeleteSelection = true }) { Icon(Icons.Default.Delete, "删除所选") }
            }
        }
        when {
            selectedAlbum != null -> ResultMediaGrid(
                media = selectedAlbum.media,
                selectedKeys = selectedKeys,
                selectionMode = selectionMode,
                onOpen = { openMedia(it, selectedAlbum.media) },
                onToggleSelection = { toggleSelection(listOf(it)) },
                header = resultHeader,
            )
            selectedAlbumId != null || media.isEmpty() -> Column(Modifier.fillMaxSize()) {
                resultHeader()
                EmptyState(Icons.Default.Image, when {
                    selectedAlbumId != null -> "正在读取任务结果\n请确认任务已完成，或刷新重试"
                    source == ResultSource.LOCAL -> "暂无本地作品\n在参数页长按输出部件，添加自动保存规则"
                    else -> "云端暂无作品\n生成完成后，可在这里查看图片和视频"
                })
            }
            layout == ResultLayout.ALL -> ResultMediaGrid(
                media = media,
                selectedKeys = selectedKeys,
                selectionMode = selectionMode,
                onOpen = { openMedia(it, media) },
                onToggleSelection = { toggleSelection(listOf(it)) },
                header = resultHeader,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (LocalConfiguration.current.screenWidthDp >= 720) 160.dp else 124.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "results-controls", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { resultHeader() }
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
            onViewParameters = viewModel::readResultParameters,
            onOpenWorkflow = { item ->
                galleryItems = emptyList()
                viewModel.openWorkflowFromResult(item)
            },
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
    header: @Composable () -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (LocalConfiguration.current.screenWidthDp >= 720) 160.dp else 124.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "results-controls", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        gridItems(media, key = { it.stableKey() }) { item ->
            val selected = item.stableKey() in selectedKeys
            Card(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (selectionMode) onToggleSelection(item) else onOpen(item) },
                    onLongClick = { onToggleSelection(item) },
                ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
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
                Text(item.filename, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box {
            MediaCover(cover, Modifier.fillMaxWidth().aspectRatio(1.33f))
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
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(albumTitle(album), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    album.media.maxOfOrNull(ResultMedia::createdAt)?.takeIf { it > 0L }?.let { formatTime(it).substring(5, 16) }.orEmpty(),
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
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
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

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ImageGalleryViewer(
    items: List<ResultMedia>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onSave: (ResultMedia, (String) -> Unit) -> Unit,
    onShare: (ResultMedia) -> Unit,
    onOpen: (ResultMedia) -> Unit,
    onViewParameters: suspend (ResultMedia) -> ResultParameterReport,
    onOpenWorkflow: (ResultMedia) -> Unit,
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
    var parameterMedia by remember { mutableStateOf<ResultMedia?>(null) }
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
    FullscreenGalleryDialog(onDismiss = onDismiss) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            // Reserve stable control insets even when system bars are transiently shown.
            val controlInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
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
                            .background(Color.Black.copy(alpha = 0.62f))
                            .windowInsetsPadding(controlInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
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
                            .background(Color.Black.copy(alpha = 0.68f))
                            .windowInsetsPadding(controlInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                            .padding(horizontal = 4.dp, vertical = 12.dp),
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
                                    text = { Text("查看参数") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                                    enabled = current.kind == MediaKind.IMAGE,
                                    onClick = { moreExpanded = false; parameterMedia = current },
                                )
                                DropdownMenuItem(
                                    text = { Text("打开工作流") },
                                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                                    enabled = current.kind == MediaKind.IMAGE,
                                    onClick = { moreExpanded = false; onOpenWorkflow(current) },
                                )
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
    parameterMedia?.let { item ->
        ResultParametersDialog(item, onViewParameters) { parameterMedia = null }
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
private fun BackendScreen(state: AppUiState, viewModel: MainViewModel) {
    var appOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val jobs = if (appOnly) state.jobs.filter { it.submittedByApp } else state.jobs
    val pending = state.jobs.count { it.state == JobState.PENDING }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "backend-connection") {
            Box(Modifier.padding(top = 12.dp)) { BackendConnectionCard(state, viewModel::refreshOrReconnect) }
        }
        item(key = "task-filter") {
            StudioChoices(listOf("全部任务", "本 App 任务"), if (appOnly) 1 else 0, { appOnly = it == 1 })
        }
        item(key = "task-toolbar") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("任务记录", style = MaterialTheme.typography.titleMedium)
                    Text("运行 ${jobs.count { it.state == JobState.RUNNING }} · 等待 ${jobs.count { it.state == JobState.PENDING }} · 已结束 ${jobs.count { it.state in setOf(JobState.SUCCESS, JobState.ERROR, JobState.CANCELLED) }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { confirmClear = true }, enabled = pending > 0) { Text("清空等待") }
                IconButton(onClick = viewModel::refreshTasks) { Icon(Icons.Default.Refresh, "刷新任务") }
            }
        }
        if (jobs.isEmpty()) item {
            Box(Modifier.fillMaxWidth().height(280.dp)) { EmptyState(Icons.AutoMirrored.Filled.List, "暂无任务记录\n提交任务后，可在这里查看进度与状态") }
        }
        items(jobs, key = { it.id }) { job -> JobCard(job, viewModel, tracked = state.activeJobId == job.id) }
    }
    if (confirmClear) ConfirmDialog(
        title = "清空服务器等待队列？",
        message = "将移除服务器上全部待执行任务，包括其他客户端的任务；正在运行的任务不会中断。",
        onDismiss = { confirmClear = false },
    ) { confirmClear = false; viewModel.clearPendingJobs() }
}

@Composable
private fun JobCard(job: JobSummary, viewModel: MainViewModel, tracked: Boolean) {
    val trackable = job.state in setOf(JobState.RUNNING, JobState.PENDING)
    var confirmCancel by remember(job.id) { mutableStateOf(false) }
    val statusColor = when (job.state) {
        JobState.ERROR -> MaterialTheme.colorScheme.error
        JobState.SUCCESS -> MaterialTheme.colorScheme.secondary
        JobState.CANCELLED, JobState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Card(
        Modifier.fillMaxWidth().clickable(enabled = trackable) { viewModel.takeoverJob(job) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (tracked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(job.workflowName.ifBlank { "服务器任务" }.removeSuffix(".json"), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("任务 ${job.id.take(8)}${if (job.submittedByApp) " · 本 App 提交" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StudioBadge(when (job.state) {
                    JobState.RUNNING -> "运行中"
                    JobState.PENDING -> "等待中"
                    JobState.SUCCESS -> "已完成"
                    JobState.ERROR -> "失败"
                    JobState.CANCELLED -> "已取消"
                    JobState.UNKNOWN -> "历史"
                }, color = statusColor)
            }
            job.progress?.let { LinearProgressIndicator(progress = { it.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
            if (tracked || trackable) Text(
                if (tracked) "正在跟踪 · 点击查看参数" else "点击接管并查看对应工作流",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            )
            job.currentNode?.let { Text("执行部件：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (trackable || (job.durationMillis ?: 0L) > 0L) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(job.durationMillis?.takeIf { it > 0 }?.let { "耗时 ${formatDuration(it)}" }.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (trackable) TextButton(onClick = { confirmCancel = true }) { Text("取消任务", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmCancel && trackable) ConfirmDialog(
        title = "取消此任务？",
        message = "将取消任务 ${job.id.take(8)}。已经保存的作品不受影响。",
        onDismiss = { confirmCancel = false },
    ) { confirmCancel = false; viewModel.cancelJob(job) }
}

@Composable
private fun EmptyState(icon: ImageVector, message: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(68.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(message.substringBefore('\n'), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            message.substringAfter('\n', "").takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
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
private fun SaveWorkflowAsDialog(
    initialName: String,
    initialFolder: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var folder by remember(initialFolder) { mutableStateOf(initialFolder) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("工作流另存为") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                )
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { folderMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Folder, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (folder == "workflows") "工作流根目录" else folder.removePrefix("workflows/"),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(
                        expanded = folderMenuExpanded,
                        onDismissRequest = { folderMenuExpanded = false },
                    ) {
                        folders.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(if (option == "workflows") "工作流根目录" else option.removePrefix("workflows/"))
                                },
                                leadingIcon = {
                                    Icon(
                                        if (option == folder) Icons.Default.CheckCircle else Icons.Default.Folder,
                                        null,
                                    )
                                },
                                onClick = {
                                    folder = option
                                    folderMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Text("保存位置：$folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, folder) }, enabled = name.isNotBlank()) { Text("另存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MoveWorkflowDialog(
    initialFolder: String,
    folders: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var folder by remember(initialFolder) { mutableStateOf(initialFolder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择目标文件夹") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.58f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(folders, key = { it }) { option ->
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth().clickable { folder = option },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (option == folder) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(if (option == folder) Icons.Default.CheckCircle else Icons.Default.Folder, null)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (option == "workflows") "工作流根目录" else option.removePrefix("workflows/"),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folder) },
                enabled = folder.isNotBlank() && folder != initialFolder,
            ) { Text("移动到这里") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ImageListField(field: ParameterField, viewModel: MainViewModel, onUpload: () -> Unit) {
    val images = ImageListValue.parse(field.displayValue)
    if (images == null) {
        Text(
            "当前图片列表格式无效，请清空后重新选择",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    } else if (images.isEmpty()) {
        Text("尚未添加参考图；不添加时仍可作为纯文本生成", style = MaterialTheme.typography.bodySmall)
    } else {
        images.forEachIndexed { index, path ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}.", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(7.dp))
                    Text(path, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    IconButton(
                        onClick = {
                            val reordered = images.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index - 1, item)
                            }
                            viewModel.updateField(field.key, ImageListValue.encode(reordered))
                        },
                        enabled = !field.linked && index > 0,
                        modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Default.ArrowUpward, "前移", modifier = Modifier.size(18.dp)) }
                    IconButton(
                        onClick = {
                            val reordered = images.toMutableList().also {
                                val item = it.removeAt(index)
                                it.add(index + 1, item)
                            }
                            viewModel.updateField(field.key, ImageListValue.encode(reordered))
                        },
                        enabled = !field.linked && index < images.lastIndex,
                        modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Default.ArrowDownward, "后移", modifier = Modifier.size(18.dp)) }
                    IconButton(
                        onClick = {
                            viewModel.updateField(
                                field.key,
                                ImageListValue.encode(images.filterIndexed { itemIndex, _ -> itemIndex != index }),
                            )
                        },
                        enabled = !field.linked,
                        modifier = Modifier.size(34.dp),
                    ) { Icon(Icons.Default.Delete, "移除", modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onUpload, enabled = !field.linked) {
            Icon(Icons.Default.UploadFile, null)
            Spacer(Modifier.width(6.dp))
            Text("选择多张并上传")
        }
        if (!images.isNullOrEmpty()) {
            OutlinedButton(
                onClick = { viewModel.updateField(field.key, "[]") },
                enabled = !field.linked,
            ) { Text("清空") }
        } else if (images == null) {
            OutlinedButton(
                onClick = { viewModel.updateField(field.key, "[]") },
                enabled = !field.linked,
            ) { Text("重置") }
        }
    }
    Text("顺序会传给 Qwen：第 1 张是主图，提示词可用 <image1>、<image2>…引用", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun WorkflowDraftConflictDialog(
    message: String,
    onKeepLocal: () -> Unit,
    onLoadServer: () -> Unit,
    onSaveAs: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepLocal,
        title = { Text("本地草稿与服务器版本冲突") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onKeepLocal) { Text("继续手机草稿") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onLoadServer) { Text("读取服务器版") }
                TextButton(onClick = onSaveAs) { Text("另存") }
            }
        },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "确认",
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
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

private fun formatDuration(value: Long): String {
    val totalSeconds = value / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}小时${minutes}分${seconds}秒"
        minutes > 0 -> "${minutes}分${seconds}秒"
        else -> "${seconds}秒"
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun previewUrl(media: ResultMedia): String =
    if (media.kind == MediaKind.IMAGE && media.source == ResultSource.CLOUD) "${media.url}&preview=webp;90" else media.url
