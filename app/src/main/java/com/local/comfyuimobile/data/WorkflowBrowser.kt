package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry

object WorkflowBrowser {
    const val ROOT = "workflows"

    fun parent(path: String): String = path.trimEnd('/').substringBeforeLast('/', "")

    fun entries(entries: List<WorkflowEntry>, folder: String, search: String): List<WorkflowEntry> {
        val query = search.trim()
        return entries.filter { entry ->
            if (query.isNotBlank()) {
                entry.name.contains(query, ignoreCase = true) || entry.path.contains(query, ignoreCase = true)
            } else {
                parent(entry.path) == folder.trimEnd('/')
            }
        }.sortedWith(compareBy<WorkflowEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    fun up(folder: String): String = parent(folder).takeIf { it.startsWith(ROOT) } ?: ROOT
}
