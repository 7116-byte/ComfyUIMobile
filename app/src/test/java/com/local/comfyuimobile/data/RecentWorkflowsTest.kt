package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RecentWorkflowsTest {
    @Test fun movesOpenedWorkflowToFrontAndKeepsTen() {
        val current = (1..10).map { "workflows/$it.json" }

        val updated = RecentWorkflows.add(current, "workflows/5.json")
        val withNew = RecentWorkflows.add(updated, "workflows/new.json")

        assertEquals("workflows/new.json", withNew.first())
        assertEquals(10, withNew.size)
        assertEquals(1, withNew.count { it == "workflows/5.json" })
    }

    @Test fun replacesRenamedPathAndRemovesDeletedPath() {
        val renamed = RecentWorkflows.add(
            listOf("workflows/old.json", "workflows/keep.json"),
            path = "workflows/new.json",
            replacedPath = "workflows/old.json",
        )

        assertEquals(listOf("workflows/new.json", "workflows/keep.json"), renamed)
        assertFalse("workflows/new.json" in RecentWorkflows.remove(renamed, "workflows/new.json"))
    }

    @Test fun keepsRecentEntriesVisibleBeforeServerWorkflowListArrives() {
        val resolved = RecentWorkflows.resolveEntries(
            paths = listOf("workflows/KREA2/人物.json", "workflows/测试.json"),
            available = emptyList(),
        )

        assertEquals(listOf("人物.json", "测试.json"), resolved.map { it.name })
        assertEquals(
            listOf("workflows/KREA2/人物.json", "workflows/测试.json"),
            resolved.map { it.path },
        )
    }

    @Test fun prefersServerMetadataWhenWorkflowListIsAvailable() {
        val serverEntry = WorkflowEntry(
            name = "服务器名称.json",
            path = "workflows/KREA2/人物.json",
            isDirectory = false,
            size = 123L,
            modified = 456.0,
        )

        assertEquals(
            serverEntry,
            RecentWorkflows.resolveEntries(listOf(serverEntry.path), listOf(serverEntry)).single(),
        )
    }
}
