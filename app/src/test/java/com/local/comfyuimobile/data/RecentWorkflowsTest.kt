package com.local.comfyuimobile.data

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
}
