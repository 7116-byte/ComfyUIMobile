package com.local.comfyuimobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.BuildConfig
import com.local.comfyuimobile.MainViewModel
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsPage(val title: String) {
    OVERVIEW("设置"), SERVER("服务器连接"), RESULTS("本地作品"),
    DRAFTS("工作流草稿"), LOGS("诊断日志"), LOG_VIEW("日志内容"), UPDATE("软件更新"),
}

private enum class SettingsConfirmation { DELETE_RESULTS, CLEAR_DRAFTS, CLEAR_LOGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: AppUiState,
    viewModel: MainViewModel,
    snackbar: SnackbarHostState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.OVERVIEW) }
    val expanded = LocalConfiguration.current.screenWidthDp >= 720
    var confirmation by remember { mutableStateOf<SettingsConfirmation?>(null) }
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
    val goBack: () -> Unit = {
        when (page) {
            SettingsPage.OVERVIEW -> onDismiss()
            SettingsPage.LOG_VIEW -> page = SettingsPage.LOGS
            else -> page = SettingsPage.OVERVIEW
        }
    }
    BackHandler(onBack = goBack)
    LaunchedEffect(Unit) { viewModel.refreshLocalDraftCount() }
    LaunchedEffect(page) {
        if (page == SettingsPage.LOG_VIEW) diagnosticLog = viewModel.diagnosticLog()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(page.title) },
                    navigationIcon = {
                        IconButton(onClick = goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, if (page == SettingsPage.OVERVIEW) "关闭设置" else "返回设置")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (expanded) {
                    Column(Modifier.width(192.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 8.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsPage.entries.filter { it != SettingsPage.LOG_VIEW }.forEach { target ->
                            val selected = page == target || page == SettingsPage.LOG_VIEW && target == SettingsPage.LOGS
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { page = target },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(when (target) {
                                        SettingsPage.SERVER -> Icons.Default.Wifi
                                        SettingsPage.RESULTS -> Icons.Default.Image
                                        SettingsPage.DRAFTS -> Icons.Default.Save
                                        SettingsPage.LOGS, SettingsPage.LOG_VIEW -> Icons.Default.Description
                                        SettingsPage.UPDATE -> Icons.Default.SystemUpdate
                                        SettingsPage.OVERVIEW -> Icons.Default.Settings
                                    }, null, Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (target == SettingsPage.OVERVIEW) "设置概览" else target.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    key(page) {
                        LazyColumn(
                            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            when (page) {
                                SettingsPage.OVERVIEW -> {
                                    item {
                                        SettingsGroup("连接") {
                                            SettingsLink(Icons.Default.Wifi, "服务器连接", serverSummary(state)) { page = SettingsPage.SERVER }
                                        }
                                    }
                                    item {
                                        SettingsGroup("本地数据") {
                                            SettingsLink(
                                                Icons.Default.Image, "本地作品",
                                                "${state.localResults.size} 项作品 · ${state.cacheOutputRules.count { it.enabled }} 条保存规则已启用",
                                            ) { page = SettingsPage.RESULTS }
                                            SettingsDivider()
                                            SettingsLink(
                                                Icons.Default.Save, "工作流草稿",
                                                "${if (state.localDraftsEnabled) "已开启" else "已关闭"} · ${state.localDraftCount} 个草稿",
                                            ) { page = SettingsPage.DRAFTS }
                                        }
                                    }
                                    item {
                                        SettingsGroup("应用") {
                                            SettingsLink(
                                                Icons.Default.Description, "诊断日志",
                                                if (state.loggingEnabled) "正在记录运行和闪退日志" else "日志记录已关闭",
                                            ) { page = SettingsPage.LOGS }
                                            SettingsDivider()
                                            SettingsLink(
                                                Icons.Default.SystemUpdate, "软件更新",
                                                when {
                                                    state.updateDownloading -> "正在下载更新"
                                                    state.updateInfo != null -> "发现新版本 ${state.updateInfo.tag}"
                                                    else -> "当前版本 ${BuildConfig.VERSION_NAME}"
                                                },
                                            ) { page = SettingsPage.UPDATE }
                                        }
                                    }
                                    item {
                                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("ComfyUI Mobile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            SettingsHint("版本 ${BuildConfig.VERSION_NAME}")
                                        }
                                    }
                                }
                                SettingsPage.SERVER -> {
                                    item {
                                        SettingsGroup("当前连接") {
                                            SettingsDetail("状态", serverSummary(state))
                                            state.activeServer?.let { server ->
                                                SettingsDivider()
                                                SettingsDetail("服务器地址", server.baseUrl)
                                                if (server.lastSeen > 0L) {
                                                    SettingsDivider()
                                                    SettingsDetail("最后在线", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(server.lastSeen)))
                                                }
                                            }
                                        }
                                    }
                                    state.systemStats?.let { stats ->
                                        item {
                                            SettingsGroup("服务器信息") {
                                                SettingsDetail("ComfyUI", stats.comfyVersion.ifBlank { "未知" })
                                                SettingsDivider()
                                                SettingsDetail("前端版本", stats.frontendVersion.ifBlank { "未知" })
                                                stats.devices.forEach { device ->
                                                    SettingsDivider()
                                                    SettingsDetail(device.name, "显存可用 ${settingsSize(device.vramFree)} / ${settingsSize(device.vramTotal)}")
                                                }
                                            }
                                        }
                                    }
                                    item {
                                        if (state.activeServer != null) {
                                            OutlinedButton(
                                                onClick = { viewModel.disconnect(); onDismiss() },
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("断开连接")
                                            }
                                        } else {
                                            SettingsHint("返回连接页可添加服务器或扫描局域网。")
                                        }
                                    }
                                }
                                SettingsPage.RESULTS -> {
                                    item {
                                        SettingsGroup("自动保存规则") {
                                            SettingsDetail("已启用 ${state.cacheOutputRules.count { it.enabled }} / ${state.cacheOutputRules.size} 条", "在参数页长按输出部件，即可加入保存白名单。")
                                        }
                                    }
                                    item {
                                        SettingsHint("规则按服务器和输出部件类型生效，适用于该服务器的所有工作流。仅自动保存本 App 提交或你明确接管且命中白名单的任务输出。")
                                    }
                                    if (state.cacheOutputRules.isEmpty()) {
                                        item {
                                            SettingsGroup {
                                                SettingsDetail("尚未添加保存规则", "添加后，符合规则的新作品会自动保存到手机。")
                                            }
                                        }
                                    }
                                    items(state.cacheOutputRules) { rule ->
                                        SettingsGroup {
                                            Row(
                                                Modifier.fillMaxWidth().toggleable(value = rule.enabled, role = Role.Switch, onValueChange = { viewModel.setCacheRuleEnabled(rule, it) }).padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(rule.nodeTitle.ifBlank { rule.nodeType }, style = MaterialTheme.typography.titleSmall)
                                                    SettingsHint(rule.nodeType)
                                                    SettingsHint(rule.serverUrl)
                                                }
                                                Switch(rule.enabled, onCheckedChange = null)
                                            }
                                            SettingsDivider()
                                            TextButton(onClick = { viewModel.removeCacheRule(rule) }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                                                Text("移除规则")
                                            }
                                        }
                                    }
                                    item {
                                        SettingsGroup("存储管理") {
                                            SettingsDetail("${state.localResults.size} 项本地作品", "删除后无法从手机恢复，电脑端作品和自动保存规则不受影响。")
                                            SettingsDangerButton("删除全部本地作品", state.localResults.isNotEmpty()) { confirmation = SettingsConfirmation.DELETE_RESULTS }
                                        }
                                    }
                                }
                                SettingsPage.DRAFTS -> {
                                    item {
                                        SettingsGroup {
                                            SettingsToggle("保存本地草稿", "保留工作流未保存的修改，下次打开时继续编辑。", state.localDraftsEnabled, viewModel::setLocalDraftsEnabled)
                                        }
                                    }
                                    item {
                                        SettingsHint("关闭后，打开工作流会直接读取服务器版本，不再保存或恢复未保存修改。图片和文件导入的临时工作流仅在当前会话保留。")
                                    }
                                    item {
                                        SettingsGroup("草稿管理") {
                                            SettingsDetail("${state.localDraftCount} 个工作流有本地草稿", "清除草稿不影响服务器上已保存的工作流文件。")
                                            SettingsDangerButton("清除全部本地草稿", state.localDraftCount > 0) { confirmation = SettingsConfirmation.CLEAR_DRAFTS }
                                        }
                                    }
                                }
                                SettingsPage.LOGS -> {
                                    item {
                                        SettingsGroup {
                                            SettingsToggle("记录运行和闪退日志", "用于排查连接、任务和应用异常。", state.loggingEnabled, viewModel::setLoggingEnabled)
                                        }
                                    }
                                    item { SettingsHint("不记录提示词、工作流正文或生成图片。关闭记录后，仍可查看和导出已有日志。") }
                                    item {
                                        SettingsGroup("日志文件") {
                                            SettingsLink(Icons.Default.Description, "查看日志", "读取当前设备上已有的记录") {
                                                page = SettingsPage.LOG_VIEW
                                            }
                                            SettingsDivider()
                                            SettingsLink(Icons.Default.Download, "导出日志", "保存为文本文件，便于反馈问题") {
                                                pendingLogExport = viewModel.diagnosticLog()
                                                logExportLauncher.launch("ComfyUIMobile-${System.currentTimeMillis()}.log")
                                            }
                                        }
                                    }
                                    item {
                                        SettingsDangerButton("清空日志") { confirmation = SettingsConfirmation.CLEAR_LOGS }
                                    }
                                }
                                SettingsPage.LOG_VIEW -> {
                                    item {
                                        SelectionContainer {
                                            Text(diagnosticLog.ifBlank { "暂无日志" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                                SettingsPage.UPDATE -> {
                                    item {
                                        SettingsGroup {
                                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.SystemUpdate, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text("ComfyUI Mobile", style = MaterialTheme.typography.titleLarge)
                                                SettingsHint("当前版本 ${BuildConfig.VERSION_NAME}")
                                            }
                                        }
                                    }
                                    item {
                                        OutlinedButton(onClick = { viewModel.checkUpdate() }, modifier = Modifier.fillMaxWidth(), enabled = !state.updateDownloading) {
                                            Text("检查更新")
                                        }
                                    }
                                    state.updateInfo?.let { info ->
                                        item {
                                            SettingsGroup("可用更新") {
                                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Text("新版本 ${info.tag}", style = MaterialTheme.typography.titleMedium)
                                                    if (state.updateDownloading) {
                                                        val progress = state.updateDownloadProgress
                                                        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                                                        else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                                                        SettingsHint("下载中 ${progress?.let { "${(it * 100).toInt()}%" }.orEmpty()} · ${state.updateDownloadSource.orEmpty()}")
                                                    } else {
                                                        Button(onClick = viewModel::downloadUpdate, modifier = Modifier.fillMaxWidth()) {
                                                            Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                                                            Spacer(Modifier.width(8.dp))
                                                            Text("下载并安装")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    item { SettingsHint("更新来自 GitHub Release，安装前会校验文件、包名、版本号和签名证书。未连接服务器时也可检查更新。") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { action ->
        val title = when (action) {
            SettingsConfirmation.DELETE_RESULTS -> "删除全部本地作品？"
            SettingsConfirmation.CLEAR_DRAFTS -> "清除全部本地草稿？"
            SettingsConfirmation.CLEAR_LOGS -> "清空诊断日志？"
        }
        val message = when (action) {
            SettingsConfirmation.DELETE_RESULTS -> "将删除手机中现有的 ${state.localResults.size} 项作品。电脑端作品不会被删除，保存规则仍会生效，之后符合规则的新作品仍会自动保存。"
            SettingsConfirmation.CLEAR_DRAFTS -> "将删除全部 ${state.localDraftCount} 个本地草稿及其中未保存的修改。服务器上的工作流文件不受影响。"
            SettingsConfirmation.CLEAR_LOGS -> "将清除当前设备上已有的诊断日志。日志记录开关保持不变。"
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    when (action) {
                        SettingsConfirmation.DELETE_RESULTS -> viewModel.clearLocalCache()
                        SettingsConfirmation.CLEAR_DRAFTS -> viewModel.clearAllWorkflowDrafts()
                        SettingsConfirmation.CLEAR_LOGS -> viewModel.clearDiagnosticLog()
                    }
                    confirmation = null
                }) { Text("确认清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            content = content,
        )
    }
}

@Composable
private fun SettingsLink(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsToggle(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            SettingsHint(summary)
        }
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
private fun SettingsDetail(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        SelectionContainer { SettingsHint(value) }
    }
}

@Composable
private fun SettingsHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun SettingsDangerButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

private fun serverSummary(state: AppUiState): String = when (state.status) {
    ConnectionStatus.CONNECTED -> "已连接 · ${state.activeServer?.name.orEmpty()}"
    ConnectionStatus.CONNECTING -> "正在连接服务器"
    ConnectionStatus.RECONNECTING -> "正在重新连接"
    ConnectionStatus.ERROR -> "连接异常 · ${state.connectionMessage}"
    ConnectionStatus.DISCONNECTED -> "尚未连接服务器"
}

private fun settingsSize(bytes: Long): String = if (bytes >= 1024L * 1024L * 1024L) {
    String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
} else {
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024))
}
