package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkflowBrowserTest {
    private val entries = listOf(
        WorkflowEntry("KREA2", "workflows/KREA2", true),
        WorkflowEntry("SDXL", "workflows/SDXL", true),
        WorkflowEntry("root.json", "workflows/root.json", false),
        WorkflowEntry("krea.json", "workflows/KREA2/krea.json", false),
        WorkflowEntry("nested", "workflows/KREA2/nested", true),
        WorkflowEntry("deep.json", "workflows/KREA2/nested/deep.json", false),
    )

    @Test fun rootShowsOnlyRootFoldersAndFiles() {
        assertEquals(
            setOf("workflows/KREA2", "workflows/SDXL", "workflows/root.json"),
            WorkflowBrowser.entries(entries, WorkflowBrowser.ROOT, "").map { it.path }.toSet(),
        )
    }

    @Test fun folderShowsOnlyDirectChildren() {
        assertEquals(
            setOf("workflows/KREA2/krea.json", "workflows/KREA2/nested"),
            WorkflowBrowser.entries(entries, "workflows/KREA2", "").map { it.path }.toSet(),
        )
    }

    @Test fun searchFindsAcrossAllFoldersAndUpHandlesNestedFolders() {
        assertEquals(listOf("workflows/KREA2/nested/deep.json"), WorkflowBrowser.entries(entries, WorkflowBrowser.ROOT, "deep").map { it.path })
        assertEquals("workflows/KREA2", WorkflowBrowser.up("workflows/KREA2/nested"))
        assertEquals(WorkflowBrowser.ROOT, WorkflowBrowser.up("workflows/KREA2"))
    }
}
