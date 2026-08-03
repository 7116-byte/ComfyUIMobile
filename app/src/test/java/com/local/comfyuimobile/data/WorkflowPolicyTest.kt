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

    @Test fun draftStructureKeepsSameTemplateDespiteValueEdits() {
        val server = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"}],"links":[]}"""
        val draftSameValues = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"}],"links":[]}"""
        val draftEditedValues = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"}],"links":[]}"""
        assertEquals(1.0, WorkflowPolicy.draftStructureCoverage(draftSameValues, server), 0.0001)
        assertEquals(1.0, WorkflowPolicy.draftStructureCoverage(draftEditedValues, server), 0.0001)
        assertFalse(WorkflowPolicy.draftStructureMismatched(draftEditedValues, server))
    }

    @Test fun draftStructureKeepsAddedOrRemovedNodes() {
        val server = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"}],"links":[]}"""
        val draftAdded = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"},{"id":4,"type":"PreviewImage"}],"links":[]}"""
        val draftRemoved = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"}],"links":[]}"""
        assertFalse(WorkflowPolicy.draftStructureMismatched(draftAdded, server))
        assertFalse(WorkflowPolicy.draftStructureMismatched(draftRemoved, server))
    }

    @Test fun draftStructureFlagsAnotherWorkflowsJson() {
        val server = """{"nodes":[{"id":1,"type":"LoadImage"},{"id":2,"type":"KSampler"},{"id":3,"type":"SaveImage"}],"links":[]}"""
        val otherWorkflow = """{"nodes":[{"id":11,"type":"CheckpointLoaderSimple"},{"id":12,"type":"CLIPTextEncode"},{"id":13,"type":"VAEDecode"},{"id":14,"type":"EmptyLatentImage"}],"links":[]}"""
        assertTrue(WorkflowPolicy.draftStructureMismatched(otherWorkflow, server))
    }
}
