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

    @Test fun twoFrameRenderBarrierHasBalancedJavaScriptDelimiters() {
        assertEquals(
            "await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));",
            ComfyBridge.TWO_FRAME_RENDER_BARRIER,
        )
        assertBalancedJavaScriptDelimiters(ComfyBridge.TWO_FRAME_RENDER_BARRIER)
    }

    private fun assertBalancedJavaScriptDelimiters(script: String) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        var regex = false
        var regexClass = false
        var previousSignificant: Char? = null
        var index = 0
        while (index < script.length) {
            val char = script[index]
            val next = script.getOrNull(index + 1)
            when {
                lineComment -> if (char == '\n') lineComment = false
                blockComment -> if (char == '*' && next == '/') {
                    blockComment = false
                    index += 1
                }
                quote != null -> when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quote -> quote = null
                }
                regex -> when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '[' -> regexClass = true
                    char == ']' -> regexClass = false
                    char == '/' && !regexClass -> regex = false
                }
                char == '/' && next == '/' -> {
                    lineComment = true
                    index += 1
                }
                char == '/' && next == '*' -> {
                    blockComment = true
                    index += 1
                }
                char == '/' && (previousSignificant == null || previousSignificant in "([{:;,=!?&|>") -> regex = true
                char == '\'' || char == '"' || char == '`' -> quote = char
                char == '(' || char == '[' || char == '{' -> stack.addLast(char to index)
                char == ')' || char == ']' || char == '}' -> {
                    val expected = when (char) {
                        ')' -> '('
                        ']' -> '['
                        else -> '{'
                    }
                    val opening = stack.removeLastOrNull()
                        ?: throw AssertionError("JavaScript 在第 $index 个字符多了关闭符号 $char")
                    if (opening.first != expected) {
                        throw AssertionError("JavaScript 第 ${opening.second} 个字符 ${opening.first} 与第 $index 个字符 $char 不匹配")
                    }
                }
            }
            if (!char.isWhitespace() && quote == null && !lineComment && !blockComment && !regex) {
                previousSignificant = char
            }
            index += 1
        }
        if (quote != null || blockComment || regex) {
            throw AssertionError("JavaScript 字符串、注释或正则表达式没有结束")
        }
        if (stack.isNotEmpty()) {
            val opening = stack.last()
            throw AssertionError("JavaScript 第 ${opening.second} 个字符 ${opening.first} 没有对应关闭符号")
        }
    }
}
