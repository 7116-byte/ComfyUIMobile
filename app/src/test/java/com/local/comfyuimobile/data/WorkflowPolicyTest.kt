package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowPolicyTest {
    @Test fun detectsRealModifiedTimestampConflicts() {
        assertFalse(WorkflowPolicy.hasModifiedConflict(100.0, null))
        assertFalse(WorkflowPolicy.hasModifiedConflict(100.0, 100.0005))
        assertTrue(WorkflowPolicy.hasModifiedConflict(100.0, 101.0))
    }

    @Test fun writesVersionedFieldLayoutWithoutRemovingExistingExtraData() {
        val workflow = JSONObject("""{"extra":{"keep":"yes","comfyMobile":{"schema":1,"fields":{"8/seed":{"label":"旧分支种子"}}}}}""")
        val field = ParameterField(
            key = "7/text", nodeId = "7", nodeTitle = "Positive", nodeType = "CLIPTextEncode",
            name = "text", label = "正向提示词", widgetType = "customtext", kind = ParameterKind.MULTILINE,
            valueJson = "\"cat\"", displayValue = "cat", visible = false,
            section = ParameterSection.PRIMARY, order = 3,
        )
        WorkflowPolicy.writeMobileLayout(workflow, listOf(field))
        val extra = workflow.getJSONObject("extra")
        assertEquals("yes", extra.getString("keep"))
        val mobile = extra.getJSONObject("comfyMobile")
        assertEquals(1, mobile.getInt("schema"))
        val stored = mobile.getJSONObject("fields").getJSONObject("7/text")
        assertEquals("正向提示词", stored.getString("label"))
        assertFalse(stored.getBoolean("visible"))
        assertEquals("primary", stored.getString("section"))
        assertEquals(3, stored.getInt("order"))
        assertEquals("旧分支种子", mobile.getJSONObject("fields").getJSONObject("8/seed").getString("label"))
    }
}
