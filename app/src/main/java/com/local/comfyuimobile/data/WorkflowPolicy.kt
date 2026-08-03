package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterSection
import org.json.JSONObject
import kotlin.math.abs

object WorkflowPolicy {
    private const val DRAFT_STRUCTURE_MIN_COVERAGE = 0.5

    fun hasModifiedConflict(loadedModified: Double, serverModified: Double?): Boolean =
        serverModified != null && abs(serverModified - loadedModified) > 0.001

    /**
     * Extracts the (node id -> type) map of a canvas workflow JSON. Value-level
     * parameter edits keep this map identical; loading another workflow's JSON
     * replaces it almost entirely.
     */
    fun workflowNodeSignature(workflowJson: String): Map<String, String>? = runCatching {
        val root = JSONObject(workflowJson)
        val nodes = root.optJSONArray("nodes") ?: return null
        buildMap {
            repeat(nodes.length()) { index ->
                val node = nodes.getJSONObject(index)
                put(node.optString("id"), node.optString("type"))
            }
        }
    }.getOrNull()

    /**
     * Fraction of the server workflow's (node id -> type) pairs that still exist
     * in the draft. Legitimate edits keep it near 1.0; a draft that accidentally
     * contains another workflow's JSON drops it towards 0.
     */
    fun draftStructureCoverage(draftJson: String, serverJson: String): Double {
        val draftNodes = workflowNodeSignature(draftJson) ?: return 0.0
        val serverNodes = workflowNodeSignature(serverJson) ?: return 0.0
        if (serverNodes.isEmpty()) return 1.0
        val matched = serverNodes.count { (id, type) -> draftNodes[id] == type }
        return matched.toDouble() / serverNodes.size
    }

    fun draftStructureMismatched(draftJson: String, serverJson: String): Boolean =
        draftStructureCoverage(draftJson, serverJson) < DRAFT_STRUCTURE_MIN_COVERAGE

    fun writeMobileLayout(workflow: JSONObject, fields: List<ParameterField>): JSONObject {
        val extra = workflow.optJSONObject("extra") ?: JSONObject().also { workflow.put("extra", it) }
        val mobile = extra.optJSONObject("comfyMobile") ?: JSONObject().also { extra.put("comfyMobile", it) }
        val values = mobile.optJSONObject("fields") ?: JSONObject().also { mobile.put("fields", it) }
        fields.forEach { field ->
            values.put(
                field.key,
                JSONObject()
                    .put("label", field.label)
                    .put("visible", field.visible)
                    .put("section", if (field.section == ParameterSection.PRIMARY) "primary" else "more")
                    .put("order", field.order),
            )
        }
        mobile.put("schema", 1)
        return workflow
    }
}
