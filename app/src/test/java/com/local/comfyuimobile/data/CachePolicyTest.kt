package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.MediaKind
import com.local.comfyuimobile.model.ResultMedia
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachePolicyTest {
    private val rule = CacheOutputRule(
        serverUrl = "http://192.168.10.109:8188",
        workflowPath = "workflows/a.json",
        workflowName = "a.json",
        nodeId = "9",
        nodeTitle = "保存图像",
        nodeType = "SaveImage",
    )
    private val media = ResultMedia(
        jobId = "app-job",
        nodeId = "9",
        filename = "a.png",
        subfolder = "",
        type = "output",
        kind = MediaKind.IMAGE,
        url = "http://server/view",
        workflowPath = "workflows/a.json",
    )

    @Test fun acceptsOnlyAppSubmittedMatchingOutput() {
        assertTrue(CachePolicy.shouldCache(media, setOf("app-job"), listOf(rule), rule.serverUrl))
        assertFalse(CachePolicy.shouldCache(media, emptySet(), listOf(rule), rule.serverUrl))
    }

    @Test fun rejectsOtherWorkflowNodeOrServer() {
        assertFalse(CachePolicy.shouldCache(media.copy(nodeId = "10"), setOf("app-job"), listOf(rule), rule.serverUrl))
        assertFalse(CachePolicy.shouldCache(media.copy(workflowPath = "workflows/b.json"), setOf("app-job"), listOf(rule), rule.serverUrl))
        assertFalse(CachePolicy.shouldCache(media, setOf("app-job"), listOf(rule), "http://192.168.10.110:8188"))
    }
}
