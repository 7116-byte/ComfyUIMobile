package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.bridge.FieldValidator
import com.local.comfyuimobile.model.AppUiState

@Composable
internal fun toolbarTextShadow() = Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 1.5f), 3f)

@Composable
internal fun FloatingToolbarIcon(vector: ImageVector, description: String?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(modifier) {
        Icon(vector, null, Modifier.matchParentSize().offset(y = 1.dp).blur(2.dp), tint = Color.Black.copy(alpha = 0.28f))
        Icon(vector, description, Modifier.size(24.dp), tint = tint)
    }
}

internal fun parameterProblemMessage(state: AppUiState): String? {
    val workflow = state.selectedWorkflow ?: return null
    val problem = FieldValidator.detailedProblems(state.fields).firstOrNull()
    val nodeId = problem?.nodeId ?: state.nodeProblems.entries.firstOrNull { it.value.isNotEmpty() }?.key ?: return null
    val message = problem?.message ?: state.nodeProblems[nodeId]?.firstOrNull() ?: return null
    val title = workflow.nodes.firstOrNull { it.id == nodeId }?.title ?: "节点 $nodeId"
    return "$title：$message"
}

@Composable
internal fun StudioToolbar(
    title: String,
    state: AppUiState?,
    snackbar: SnackbarHostState,
    connectionLabel: String?,
    onOpenBackend: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var details by remember { mutableStateOf<String?>(null) }
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("studio-toolbar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge.copy(shadow = toolbarTextShadow()), maxLines = 1)
        Column(Modifier.weight(1f).testTag("toolbar-feedback"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state != null) {
                val problem = parameterProblemMessage(state)
                val generation = state.generationMessage
                val transient = snackbar.currentSnackbarData?.visuals?.message
                val count = listOfNotNull(problem, generation.takeIf { it.isNotBlank() }, transient?.takeIf { it != problem && it != generation }).size
                val maxLines = if (count > 1) 1 else 2
                problem?.let {
                    ParameterFeedback(it, isError = true, maxLines = maxLines, onDetails = { details = it })
                }
                if (generation.isNotBlank()) ParameterFeedback(
                    generation,
                    isError = generation.contains("失败"),
                    completed = state.generationProgress == 1f && !generation.contains("正在确认"),
                    progress = state.generationProgress,
                    maxLines = maxLines,
                    onDetails = { details = generation },
                )
                if (snackbar.currentSnackbarData != null) SnackbarHost(snackbar) { data ->
                    if (data.visuals.message != problem && data.visuals.message != generation) {
                        ParameterFeedback(data.visuals.message, isError = state.error != null,
                            maxLines = maxLines, onDetails = { details = data.visuals.message }, onDismiss = data::dismiss)
                    }
                }
            }
        }
        if (connectionLabel != null) TextButton(onClick = onOpenBackend) {
            Text(connectionLabel, style = MaterialTheme.typography.labelLarge.copy(shadow = toolbarTextShadow()), color = MaterialTheme.colorScheme.error)
        }
        IconButton(onClick = onOpenSettings) { FloatingToolbarIcon(Icons.Default.Settings, "设置") }
    }
    details?.let { message ->
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("运行提示") },
            text = { androidx.compose.foundation.text.selection.SelectionContainer { Text(message) } },
            confirmButton = { TextButton(onClick = { details = null }) { Text("关闭") } },
        )
    }
}
