package com.local.comfyuimobile.network

import org.json.JSONObject

data class NodeProgressUpdate(val promptId: String, val nodeId: String, val progress: Float)

object ProgressStateParser {
    fun parse(data: JSONObject): NodeProgressUpdate? {
        val promptId = data.optString("prompt_id")
        if (promptId.isBlank()) return null
        val nodes = data.optJSONObject("nodes") ?: return null
        var update: NodeProgressUpdate? = null
        nodes.keys().forEach { key ->
            val item = nodes.optJSONObject(key) ?: return@forEach
            if (!item.optString("state").equals("running", ignoreCase = true)) return@forEach
            val nodeId = item.optString("display_node_id").ifBlank {
                item.optString("real_node_id").ifBlank { item.optString("node_id", key) }
            }
            val maximum = item.optDouble("max", 1.0)
            val progress = if (maximum > 0) (item.optDouble("value") / maximum).toFloat().coerceIn(0f, 1f) else 0f
            if (nodeId.isNotBlank()) update = NodeProgressUpdate(promptId, nodeId, progress)
        }
        return update
    }
}
