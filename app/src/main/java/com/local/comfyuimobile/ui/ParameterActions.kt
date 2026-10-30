package com.local.comfyuimobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ParameterFeedback(
    message: String,
    isError: Boolean = false,
    completed: Boolean = false,
    progress: Float? = null,
    onDismiss: (() -> Unit)? = null,
    maxLines: Int = 2,
    onDetails: (() -> Unit)? = null,
) {
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        completed -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            if (onDetails != null) Modifier.clickable(onClickLabel = "查看完整提示", onClick = onDetails) else Modifier,
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FloatingToolbarIcon(
                when { isError -> Icons.Default.ErrorOutline; completed -> Icons.Default.CheckCircle; else -> Icons.Default.Info },
                null, Modifier.size(18.dp), tint = color,
            )
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(shadow = toolbarTextShadow()), color = color,
                maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            if (onDismiss != null) IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                FloatingToolbarIcon(Icons.Default.Close, "关闭提示", Modifier.size(18.dp))
            }
        }
        if (progress != null && !isError && !completed) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun ParameterActionDock(
    onAdvancedEditor: () -> Unit,
    onGenerate: () -> Unit,
    advancedEnabled: Boolean,
    generateEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onAdvancedEditor, enabled = advancedEnabled,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("高级编辑", maxLines = 1)
            }
            Button(
                onClick = onGenerate, enabled = generateEnabled,
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("生成", maxLines = 1)
            }
        }
    }
}
