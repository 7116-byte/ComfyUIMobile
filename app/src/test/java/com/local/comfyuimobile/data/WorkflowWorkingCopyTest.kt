package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WorkflowWorkingCopyTest {
    @Test fun executionSnapshotsNeverReloadFromServerFilename() {
        val snapshot = document("deleted.json").copy(sourceJobId = "job")
        assertNull(WorkflowWorkingCopy.frontendPath(snapshot))
        assertEquals("workflows/deleted.json", WorkflowWorkingCopy.frontendPath(snapshot.copy(sourceJobId = null)))
    }

    @Test fun importedTemporaryWorkflowDoesNotResolveToServerFile() {
        val temporary = document("from-image.json").copy(isTemporary = true, hasUnsavedChanges = true)
        assertNull(WorkflowWorkingCopy.frontendPath(temporary))
        assertEquals("workflows/from-image.json", WorkflowWorkingCopy.frontendPath(temporary.copy(isTemporary = false)))
    }
    private fun document(name: String, server: String = "http://a") = WorkflowDocument(
        WorkflowEntry(name, "workflows/$name", false), "graph-$name", emptyList(), serverUrl = server)

    @Test fun reopeningSamePreviewUsesCurrentEdits() {
        val old = document("a.json")
        val edited = old.copy(rawJson = "edited", hasUnsavedChanges = true)
        assertEquals(edited, WorkflowWorkingCopy.preview(old, edited, edited.fields))
    }

    @Test fun previewOfAnotherFileOrServerIsNotReplaced() {
        val preview = document("b.json")
        assertEquals(preview, WorkflowWorkingCopy.preview(preview, document("a.json"), emptyList()))
        assertEquals(preview, WorkflowWorkingCopy.preview(preview, document("b.json", "http://other"), emptyList()))
    }

    @Test fun copyingBWhileCanvasContainsAFirstLoadsB() = runTest {
        val b = document("b.json")
        var canvas = "graph-a.json"
        val calls = mutableListOf<String>()
        val copy = WorkflowWorkingCopy.serialize(b, load = { calls += "load"; canvas = it.rawJson },
            sync = { calls += "sync"; canvas })
        assertEquals("graph-b.json", copy)
        assertEquals(listOf("load", "sync"), calls)
    }
}
