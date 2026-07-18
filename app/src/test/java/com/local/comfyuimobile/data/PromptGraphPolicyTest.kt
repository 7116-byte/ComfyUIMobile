package com.local.comfyuimobile.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptGraphPolicyTest {
    @Test fun keepsExpandedSubgraphAncestorsForIndependentOutputBranches() {
        val prompt = JSONObject(
            """
            {
              "69":{"class_type":"SaveImage","inputs":{"images":["68:out",0]}},
              "74":{"class_type":"rgthree_comparer","inputs":{"image_a":["56",0],"image_b":["68:out",0]}},
              "68:out":{"class_type":"VAEDecode","inputs":{"samples":["68:inner",0]}},
              "68:inner":{"class_type":"KSampler","inputs":{"model":["10",0]}},
              "10":{"class_type":"UNETLoader","inputs":{}},
              "56":{"class_type":"LoadImage","inputs":{}},
              "999":{"class_type":"SaveImage","inputs":{"images":["998",0]}},
              "998":{"class_type":"LoadImage","inputs":{}}
            }
            """.trimIndent(),
        )

        val result = PromptGraphPolicy.retainExecutableAncestors(
            prompt,
            setOf("10", "56", "68", "69", "74"),
        )

        assertFalse(result.keptFullPrompt)
        assertEquals(4, result.matchedRootCount)
        assertEquals(6, result.retainedCount)
        assertEquals(
            setOf("10", "56", "68:inner", "68:out", "69", "74"),
            result.prompt.keys().asSequence().toSet(),
        )
    }

    @Test fun removesDisconnectedCanvasOutputWithoutTouchingSharedAncestors() {
        val prompt = JSONObject(
            """{
              "1":{"class_type":"Source","inputs":{}},
              "2":{"class_type":"SaveImage","inputs":{"images":["1",0]}},
              "3":{"class_type":"BrokenOutput","inputs":{}}
            }""",
        )

        val result = PromptGraphPolicy.retainExecutableAncestors(prompt, setOf("1", "2"))

        assertEquals(setOf("1", "2"), result.prompt.keys().asSequence().toSet())
    }

    @Test fun keepsOfficialPromptWhenFrontendRewritesEveryCanvasId() {
        val prompt = JSONObject(
            """{
              "subgraph:1":{"class_type":"Source","inputs":{}},
              "subgraph:2":{"class_type":"SaveImage","inputs":{"images":["subgraph:1",0]}}
            }""",
        )

        val result = PromptGraphPolicy.retainExecutableAncestors(prompt, setOf("68", "69"))

        assertTrue(result.keptFullPrompt)
        assertEquals(2, result.prompt.length())
    }
}
