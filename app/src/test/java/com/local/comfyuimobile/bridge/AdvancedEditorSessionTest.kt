package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.WorkflowManifest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEditorSessionTest {
    @After fun tearDown() {
        AdvancedEditorSession.clear()
    }

    @Test fun keepsWorkflowPathUntilEditorResultIsConsumed() {
        AdvancedEditorSession.begin("{\"nodes\":[]}", "workflows/KREA2/中文工作流.json")

        assertEquals("{\"nodes\":[]}", AdvancedEditorSession.input())
        assertEquals("workflows/KREA2/中文工作流.json", AdvancedEditorSession.inputPath())

        val manifest = WorkflowManifest(emptyList(), emptyList())
        AdvancedEditorSession.complete("{\"nodes\":[1]}", manifest)
        val result = AdvancedEditorSession.consumeOutput()
        assertEquals("{\"nodes\":[1]}", result?.workflowJson)
        assertEquals(manifest, result?.manifest)
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

    @Test fun waitsForAttachedFinishedPageBeforeRunningBridgeScripts() {
        val ready = ComfyBridge.isPageReadyForScripts(
            currentUrl = "http://192.168.10.109:8188/",
            allowedOrigin = "http://192.168.10.109:8188",
            progress = 100,
            pageEpoch = 4,
            finishedPageEpoch = 4,
            attached = true,
        )
        assertTrue(ready)
        assertFalse(
            ComfyBridge.isPageReadyForScripts(
                currentUrl = "http://192.168.10.109:8188/",
                allowedOrigin = "http://192.168.10.109:8188",
                progress = 100,
                pageEpoch = 4,
                finishedPageEpoch = 3,
                attached = true,
            ),
        )
        assertFalse(
            ComfyBridge.isPageReadyForScripts(
                currentUrl = "http://192.168.10.109:8188/",
                allowedOrigin = "http://192.168.10.109:8188",
                progress = 100,
                pageEpoch = 4,
                finishedPageEpoch = 4,
                attached = false,
            ),
        )
    }

}
