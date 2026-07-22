package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.WorkflowEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowPathTest {
    @Test fun normalizesNamesAndFolders() {
        assertEquals("我的工作流.json", WorkflowPath.fileName("我的工作流"))
        assertEquals("already.JSON", WorkflowPath.fileName("already.JSON"))
        assertEquals("workflows/Krea2/测试", WorkflowPath.folder("workflows\\Krea2\\测试"))
        assertEquals("workflows", WorkflowPath.folder(""))
        assertEquals("workflows/workflows2", WorkflowPath.folder("workflows2"))
    }

    @Test fun rejectsTraversalAndPathSeparatorsInNames() {
        listOf("../x", "folder/x", "folder\\x").forEach { value ->
            assertTrue(runCatching { WorkflowPath.fileName(value) }.isFailure)
        }
        assertTrue(runCatching { WorkflowPath.folder("../outside") }.isFailure)
    }

    @Test fun listsRootAndNestedFoldersForSaveAs() {
        val entries = listOf(
            WorkflowEntry("Krea2", "workflows/Krea2", isDirectory = true),
            WorkflowEntry("角色", "workflows/Krea2/角色", isDirectory = true),
            WorkflowEntry("a.json", "workflows/视频/短片/a.json", isDirectory = false),
        )

        assertEquals(
            listOf("workflows", "workflows/Krea2", "workflows/Krea2/角色", "workflows/视频", "workflows/视频/短片"),
            WorkflowPath.availableFolders(entries, "workflows/Krea2/当前.json"),
        )
    }
}
