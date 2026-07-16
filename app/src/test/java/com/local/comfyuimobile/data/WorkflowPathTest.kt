package com.local.comfyuimobile.data

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
}
