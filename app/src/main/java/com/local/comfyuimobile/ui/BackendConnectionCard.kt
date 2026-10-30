package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.model.AppUiState
import com.local.comfyuimobile.model.ConnectionStatus
import java.util.Locale

internal fun backendConnectionLabel(state: AppUiState): String = when (state.status) {
    ConnectionStatus.CONNECTED -> if (state.bridgeReady) "在线" else "初始化中"
    ConnectionStatus.CONNECTING -> "连接中"
    ConnectionStatus.RECONNECTING -> "重连中"
    ConnectionStatus.ERROR -> "连接异常"
    ConnectionStatus.DISCONNECTED -> "未连接"
}

@Composable
internal fun BackendConnectionCard(state: AppUiState, onReconnect: () -> Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    val online = state.status == ConnectionStatus.CONNECTED && state.bridgeReady
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.activeServer?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StudioBadge(backendConnectionLabel(state), color = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            SelectionContainer {
                Text(state.activeServer?.baseUrl.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!online && state.connectionMessage.isNotBlank()) {
                Text(state.connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { details = !details }) {
                    Text(if (details) "收起服务器信息" else "服务器信息")
                    Icon(if (details) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(18.dp))
                }
                TextButton(onClick = onReconnect, enabled = state.status != ConnectionStatus.CONNECTING) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Text("重连", maxLines = 1)
                }
            }
            if (details) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val stats = state.systemStats
                Text("ComfyUI ${stats?.comfyVersion?.takeIf { it.isNotBlank() } ?: "版本未知"}", style = MaterialTheme.typography.bodyMedium)
                stats?.frontendVersion?.takeIf { it.isNotBlank() }?.let {
                    Text("前端 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                stats?.devices?.forEach { device ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(device.name, style = MaterialTheme.typography.bodySmall)
                        if (device.vramTotal > 0L) {
                            val gib = 1024.0 * 1024 * 1024
                            Text(
                                String.format(Locale.getDefault(), "显存可用 %.1f / %.1f GB", device.vramFree / gib, device.vramTotal / gib),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text("服务器等待队列：${state.queueRemaining}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
