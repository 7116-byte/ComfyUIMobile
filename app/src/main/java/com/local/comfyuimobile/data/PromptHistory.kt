package com.local.comfyuimobile.data

object PromptHistory {
    const val MAX_SIZE = 50

    fun add(history: List<String>, value: String): List<String> {
        val normalized = value.trim()
        if (normalized.isEmpty()) return history.take(MAX_SIZE)
        return buildList {
            add(normalized)
            history.filterNot { it.trim() == normalized }.forEach(::add)
        }.take(MAX_SIZE)
    }
}
