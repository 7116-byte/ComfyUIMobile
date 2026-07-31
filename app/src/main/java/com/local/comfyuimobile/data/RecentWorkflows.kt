package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry

object RecentWorkflows {
    const val MAX_SIZE = 10

    fun add(current: List<String>, path: String, replacedPath: String? = null): List<String> {
        if (path.isBlank()) return current.filter(String::isNotBlank).distinct().take(MAX_SIZE)
        return (listOf(path) + current)
            .filter { it.isNotBlank() && it != replacedPath }
            .distinct()
            .take(MAX_SIZE)
    }

    fun remove(current: List<String>, path: String): List<String> =
        current.filter { it.isNotBlank() && it != path }.distinct().take(MAX_SIZE)

    fun resolveEntries(paths: List<String>, available: List<WorkflowEntry>): List<WorkflowEntry> =
        paths.filter(String::isNotBlank).distinct().take(MAX_SIZE).map { path ->
            available.firstOrNull { !it.isDirectory && it.path == path }
                ?: WorkflowEntry(
                    name = path.substringAfterLast('/').ifBlank { path },
                    path = path,
                    isDirectory = false,
                )
        }
}
