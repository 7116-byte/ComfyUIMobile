package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import org.json.JSONObject

/** Queue metadata may contain a resolved seed; the editable workflow must retain rgthree's mode. */
object RgthreeSeedPolicy {
    fun preserveModes(queuedWorkflowJson: String, fields: List<ParameterField>): String {
        val modes = fields.filter { field ->
            field.nodeType.contains("Seed (rgthree)", ignoreCase = true) &&
                field.name == "seed" && field.widgetIndex >= 0 &&
                field.valueJson.toLongOrNull() in setOf(-1L, -2L, -3L)
        }
        if (modes.isEmpty()) return queuedWorkflowJson
        val workflow = JSONObject(queuedWorkflowJson)
        val nodes = workflow.optJSONArray("nodes") ?: return queuedWorkflowJson
        for (index in 0 until nodes.length()) {
            val node = nodes.optJSONObject(index) ?: continue
            val widgetValues = node.optJSONArray("widgets_values") ?: continue
            modes.filter { it.nodeId == node.opt("id")?.toString() }.forEach { field ->
                if (field.widgetIndex < widgetValues.length()) {
                    widgetValues.put(field.widgetIndex, field.valueJson.toLong())
                }
            }
        }
        return workflow.toString()
    }
}
