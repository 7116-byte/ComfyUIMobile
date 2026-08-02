package com.local.comfyuimobile.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdvancedEditorSessionTest {
    @After fun tearDown() {
        AdvancedEditorSession.clear()
    }

    @Test fun keepsWorkflowPathUntilEditorResultIsConsumed() {
        AdvancedEditorSession.begin("{\"nodes\":[]}", "workflows/KREA2/中文工作流.json")

        assertEquals("{\"nodes\":[]}", AdvancedEditorSession.input())
        assertEquals("workflows/KREA2/中文工作流.json", AdvancedEditorSession.inputPath())

        AdvancedEditorSession.complete("{\"nodes\":[1]}")
        assertEquals("{\"nodes\":[1]}", AdvancedEditorSession.consumeOutput())
        assertNull(AdvancedEditorSession.input())
        assertNull(AdvancedEditorSession.inputPath())
    }

    @Test fun normalizesServerWorkflowPathWithoutLosingChineseFolders() {
        assertEquals(
            "KREA2/中文工作流.json",
            ComfyBridge.normalizeServerWorkflowPath("/workflows\\KREA2\\中文工作流.json"),
        )
        assertNull(ComfyBridge.normalizeServerWorkflowPath("  "))
    }

    @Test fun buildsExactPersistedWorkflowStorePath() {
        assertEquals(
            "workflows/KREA2/example.json",
            ComfyBridge.frontendWorkflowStorePath("/workflows\\KREA2\\example.json"),
        )
        assertEquals(
            "workflows/KREA2/example.json",
            ComfyBridge.frontendWorkflowStorePath("KREA2/example"),
        )
        assertNull(ComfyBridge.frontendWorkflowStorePath("  "))
    }
}
