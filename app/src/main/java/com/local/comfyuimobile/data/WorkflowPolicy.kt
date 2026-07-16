package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterSection
import org.json.JSONObject
import kotlin.math.abs

object WorkflowPolicy {
    fun hasModifiedConflict(loadedModified: Double, serverModified: Double?): Boolean =
        serverModified != null && abs(serverModified - loadedModified) > 0.001

    fun writeMobileLayout(workflow: JSONObject, fields: List<ParameterField>): JSONObject {
        val extra = workflow.optJSONObject("extra") ?: JSONObject().also { workflow.put("extra", it) }
        val values = JSONObject()
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
        extra.put("comfyMobile", JSONObject().put("schema", 1).put("fields", values))
        return workflow
    }
}
