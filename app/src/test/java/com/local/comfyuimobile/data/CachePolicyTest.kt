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
        nodeType = "SaveImage",
        nodeTitle = "保存图像",
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

    @Test fun appliesSameOutputTypeAcrossWorkflowsButRejectsOtherTypeOrServer() {
        assertTrue(CachePolicy.shouldCache(media.copy(nodeId = "10"), setOf("app-job"), listOf(rule), rule.serverUrl))
        assertTrue(CachePolicy.shouldCache(media.copy(workflowPath = "workflows/b.json"), setOf("app-job"), listOf(rule), rule.serverUrl))
        assertFalse(CachePolicy.shouldCache(media.copy(nodeType = "PreviewImage"), setOf("app-job"), listOf(rule), rule.serverUrl))
        assertFalse(CachePolicy.shouldCache(media, setOf("app-job"), listOf(rule), "http://192.168.10.110:8188"))
    }

    @Test fun clearedCacheDoesNotDownloadOldHistoryAgain() {
        val old = media.copy(createdAt = 1_000L)
        val new = media.copy(jobId = "new-job", createdAt = 3_000L)

        assertFalse(CachePolicy.shouldCache(old, setOf("app-job"), listOf(rule), rule.serverUrl, cacheClearedAt = 2_000L))
        assertTrue(CachePolicy.shouldCache(new, setOf("new-job"), listOf(rule), rule.serverUrl, cacheClearedAt = 2_000L))
    }

    @Test fun keepsEveryEligibleImageFromOneBatch() {
        val batch = (1..4).map { index -> media.copy(filename = "batch_$index.png", createdAt = 3_000L) }

        val eligible = batch.filter {
            CachePolicy.shouldCache(it, setOf("app-job"), listOf(rule), rule.serverUrl, cacheClearedAt = 2_000L)
        }

        assertTrue(eligible.size == 4)
    }
}
