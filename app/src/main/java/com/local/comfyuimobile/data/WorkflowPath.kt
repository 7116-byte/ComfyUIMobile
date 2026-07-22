package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry

object WorkflowPath {
    fun availableFolders(entries: List<WorkflowEntry>, currentWorkflowPath: String): List<String> = buildSet {
        add("workflows")
        entries.forEach { entry ->
            var folder = if (entry.isDirectory) entry.path else entry.path.substringBeforeLast('/', "workflows")
            while (folder == "workflows" || folder.startsWith("workflows/")) {
                add(folder)
                if (folder == "workflows") break
                folder = folder.substringBeforeLast('/', "workflows")
            }
        }
        add(currentWorkflowPath.substringBeforeLast('/', "workflows"))
    }.sorted()

    fun fileName(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "请输入工作流名称" }
        require('/' !in value && '\\' !in value && value !in setOf(".", "..")) { "工作流名称不能包含路径符号" }
        return if (value.endsWith(".json", ignoreCase = true)) value else "$value.json"
    }

    fun folder(input: String): String {
        val normalized = input.trim().replace('\\', '/').trim('/')
        val relative = when {
            normalized == "workflows" -> ""
            normalized.startsWith("workflows/") -> normalized.removePrefix("workflows/")
            else -> normalized
        }.trim('/')
        val segments = relative.split('/').filter { it.isNotBlank() }
        require(segments.none { it == "." || it == ".." }) { "目标文件夹不能包含 . 或 .." }
        return (listOf("workflows") + segments).joinToString("/")
    }
}
