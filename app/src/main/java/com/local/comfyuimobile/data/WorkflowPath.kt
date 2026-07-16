package com.local.comfyuimobile.data

object WorkflowPath {
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
