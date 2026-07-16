package com.local.comfyuimobile.network

import org.json.JSONObject

data class NodeProgressUpdate(val promptId: String, val nodeId: String, val progress: Float)

object ProgressStateParser {
    fun parse(data: JSONObject): NodeProgressUpdate? {
        val promptId = data.optTextOrEmpty("prompt_id")
        if (promptId.isBlank()) return null
        val nodes = data.optJSONObject("nodes") ?: return null
        var update: NodeProgressUpdate? = null
        nodes.keys().forEach { key ->
            val item = nodes.optJSONObject(key) ?: return@forEach
            if (!item.optString("state").equals("running", ignoreCase = true)) return@forEach
            val nodeId = item.optTextOrEmpty("display_node_id").ifBlank {
                item.optTextOrEmpty("real_node_id").ifBlank { item.optTextOrEmpty("node_id").ifBlank { key } }
            }
            val maximum = item.optDouble("max", 1.0)
            val progress = if (maximum > 0) (item.optDouble("value") / maximum).toFloat().coerceIn(0f, 1f) else 0f
            if (nodeId.isNotBlank()) update = NodeProgressUpdate(promptId, nodeId, progress)
        }
        return update
    }

    private fun JSONObject.optTextOrEmpty(name: String): String {
        val value = opt(name)
        return if (value == null || value === JSONObject.NULL || value.toString().equals("null", ignoreCase = true)) "" else value.toString()
    }
}
