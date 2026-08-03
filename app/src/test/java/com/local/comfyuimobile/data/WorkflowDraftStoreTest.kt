package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkflowDraftStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun roundTripKeepsChineseWorkflowAndScopesIdentityByServerAndPath() {
        val store = WorkflowDraftStore(temporaryFolder.newFolder("drafts"))
        val draft = draft(
            serverUrl = "HTTP://100.64.0.10:18188/",
            workflowPath = "workflows/分类/女仆装.json",
            workflowJson = "{\"nodes\":[{\"id\":1}],\"links\":[]}",
        )

        store.saveNow(draft)

        assertEquals(draft.workflowPath, store.loadNow("http://100.64.0.10:18188", draft.workflowPath)?.workflowPath)
        assertEquals("你好，世界", store.loadNow(draft.serverUrl, draft.workflowPath)?.fields?.single()?.displayValue)
        assertNull(store.loadNow("http://192.168.10.109:8188", draft.workflowPath))
        assertNull(store.loadNow(draft.serverUrl, "workflows/其他.json"))
        assertNotEquals(
            WorkflowDraftStore.identity(draft.serverUrl, draft.workflowPath),
            WorkflowDraftStore.identity("http://192.168.10.109:8188", draft.workflowPath),
        )
    }

    @Test
    fun replacingAndDeletingDraftNeverLeavesDuplicateFiles() {
        val store = WorkflowDraftStore(temporaryFolder.newFolder("drafts"))
        val first = draft(workflowJson = "{\"nodes\":[1]}")
        val second = first.copy(workflowJson = "{\"nodes\":[2]}", updatedAt = first.updatedAt + 1)

        store.saveNow(first)
        store.saveNow(second)

        assertEquals(1, store.countNow())
        assertEquals(second.workflowJson, store.loadNow(second.serverUrl, second.workflowPath)?.workflowJson)
        store.deleteNow(second.serverUrl, second.workflowPath)
        assertEquals(0, store.countNow())
    }

    @Test
    fun fieldRestoreAppliesValueAndLayoutWithoutChangingWidgetDefinition() {
        val current = field().copy(
            valueJson = "\"服务器值\"",
            displayValue = "服务器值",
            label = "提示词",
            visible = true,
            section = ParameterSection.MORE,
            order = 9,
            options = listOf("保留的选项"),
        )
        val saved = WorkflowDraftFields.capture(
            listOf(
                current.copy(
                    valueJson = "\"手机草稿\"",
                    displayValue = "手机草稿",
                    label = "正向提示词",
                    visible = false,
                    section = ParameterSection.PRIMARY,
                    order = 2,
                ),
            ),
        )

        val restored = WorkflowDraftFields.restore(listOf(current), saved).single()

        assertEquals("手机草稿", restored.displayValue)
        assertEquals("\"手机草稿\"", restored.valueJson)
        assertEquals("正向提示词", restored.label)
        assertFalse(restored.visible)
        assertEquals(ParameterSection.PRIMARY, restored.section)
        assertEquals(2, restored.order)
        assertEquals(listOf("保留的选项"), restored.options)
    }

    @Test
    fun pruningKeepsAtMostFiftyPrivateDrafts() {
        val store = WorkflowDraftStore(temporaryFolder.newFolder("drafts"))
        repeat(WorkflowDraftStore.MAX_DRAFTS + 7) { index ->
            store.saveNow(draft(workflowPath = "workflows/$index.json", updatedAt = index.toLong()))
        }

        assertEquals(WorkflowDraftStore.MAX_DRAFTS, store.countNow())
        assertTrue(store.loadNow("http://100.64.0.10:18188", "workflows/56.json") != null)
    }

    @Test
    fun deltaDraftStoresOnlyChangesWithoutWholeWorkflow() {
        val store = WorkflowDraftStore(temporaryFolder.newFolder("drafts"))
        val delta = draft().copy(workflowJson = null, structural = false)

        store.saveNow(delta)

        val loaded = store.loadNow(delta.serverUrl, delta.workflowPath)
        assertTrue(loaded != null)
        assertNull(loaded!!.workflowJson)
        assertFalse(loaded.structural)
        assertEquals(delta.fields.single().key, loaded.fields.single().key)
    }

    @Test
    fun structuralDraftKeepsWorkflowJsonAndFlag() {
        val store = WorkflowDraftStore(temporaryFolder.newFolder("drafts"))
        val structural = draft().copy(workflowJson = "{\"nodes\":[{\"id\":9}]}", structural = true)

        store.saveNow(structural)

        val loaded = store.loadNow(structural.serverUrl, structural.workflowPath)
        assertTrue(loaded != null)
        assertTrue(loaded!!.structural)
        assertEquals(structural.workflowJson, loaded.workflowJson)
    }

    private fun draft(
        serverUrl: String = "http://100.64.0.10:18188",
        workflowPath: String = "workflows/KREA2/测试.json",
        workflowJson: String = "{\"nodes\":[],\"links\":[]}",
        updatedAt: Long = 123L,
    ) = WorkflowDraft(
        serverUrl = serverUrl,
        workflowPath = workflowPath,
        workflowName = workflowPath.substringAfterLast('/'),
        baseModified = 456.0,
        workflowJson = workflowJson,
        fields = WorkflowDraftFields.capture(listOf(field())),
        updatedAt = updatedAt,
    )

    private fun field() = ParameterField(
        key = "1/text",
        nodeId = "1",
        nodeTitle = "CLIP 文本编码",
        nodeType = "CLIPTextEncode",
        name = "text",
        label = "提示词",
        widgetType = "STRING",
        kind = ParameterKind.MULTILINE,
        valueJson = "\"你好，世界\"",
        displayValue = "你好，世界",
    )
}
