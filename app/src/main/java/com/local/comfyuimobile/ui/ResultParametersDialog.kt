package com.local.comfyuimobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.local.comfyuimobile.data.ResultParameterReport
import com.local.comfyuimobile.model.ResultMedia
import kotlinx.coroutines.CancellationException

@Composable
internal fun ResultParametersDialog(
    media: ResultMedia,
    load: suspend (ResultMedia) -> ResultParameterReport,
    onDismiss: () -> Unit,
) {
    var report by remember(media.stableKey()) { mutableStateOf<ResultParameterReport?>(null) }
    var error by remember(media.stableKey()) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(media.stableKey(), attempt) {
        report = null
        error = null
        try {
            report = load(media)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            error = failure.message ?: "参数读取失败"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("查看参数") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(media.filename, style = MaterialTheme.typography.bodySmall)
                val current = report
                when {
                    current != null -> {
                        Text(current.source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (current.note.isNotBlank()) Text(current.note, style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(current.groups) { group ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(group.title, style = MaterialTheme.typography.titleSmall)
                                    SelectionContainer {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            group.values.forEach { field ->
                                                Text("${field.label}：${field.value}", style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    error != null -> {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { attempt++ }) { Text("重试") }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text("正在读取这张图片的生成参数…")
                    }
                }
            }
        },
        dismissButton = {
            report?.let { current ->
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString("${media.filename}\n${current.copyText()}"))
                    copied = true
                }) { Text(if (copied) "已复制" else "复制参数") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
