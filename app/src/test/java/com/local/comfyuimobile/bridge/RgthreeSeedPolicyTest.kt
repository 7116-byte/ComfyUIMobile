package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RgthreeSeedPolicyTest {
    private fun field(nodeId: String, type: String, value: String) = ParameterField(
        key = "$nodeId/seed", nodeId = nodeId, nodeTitle = type, nodeType = type,
        name = "seed", label = "种子", widgetType = "number", kind = ParameterKind.INTEGER,
        valueJson = value, displayValue = value, widgetIndex = 0,
    )

    @Test fun queuedSeedIsConcreteButEditableWorkflowKeepsRandomMode() {
        val queued = """{"nodes":[{"id":477,"type":"Seed (rgthree)","widgets_values":[123456]},
            {"id":9,"type":"KSampler","widgets_values":[42]}]}"""
        val working = JSONObject(RgthreeSeedPolicy.preserveModes(queued, listOf(
            field("477", "Seed (rgthree)", "-1"), field("9", "KSampler", "42"),
        )))
        assertEquals(-1L, working.getJSONArray("nodes").getJSONObject(0).getJSONArray("widgets_values").getLong(0))
        assertEquals(42L, working.getJSONArray("nodes").getJSONObject(1).getJSONArray("widgets_values").getLong(0))
        assertEquals(123456L, JSONObject(queued).getJSONArray("nodes").getJSONObject(0).getJSONArray("widgets_values").getLong(0))
    }

    @Test fun fixedSeedIsNotRewritten() {
        val queued = """{"nodes":[{"id":477,"type":"Seed (rgthree)","widgets_values":[987]}]}"""
        assertEquals(queued, RgthreeSeedPolicy.preserveModes(queued, listOf(field("477", "Seed (rgthree)", "987"))))
    }
}
