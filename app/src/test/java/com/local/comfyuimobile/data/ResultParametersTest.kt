package com.local.comfyuimobile.data

import com.local.comfyuimobile.bridge.ImageGenerationMetadata
import org.junit.Assert.*
import org.junit.Test

class ResultParametersTest {
    @Test fun actualSeedAndOnlyThisOutputChainAreShown() {
        val metadata = ImageGenerationMetadata(
            promptJson = """{
              "1":{"class_type":"CLIPTextEncode","inputs":{"text":"this image"}},
              "2":{"class_type":"KSampler","inputs":{"positive":["1",0],"seed":9007199254740991,"steps":20}},
              "3":{"class_type":"SaveImage","inputs":{"images":["2",0],"filename_prefix":"out"}},
              "4":{"class_type":"CLIPTextEncode","inputs":{"text":"other image"}}
            }""",
            workflowJson = """{"nodes":[{"id":2,"title":"采样","widgets_values":[-1]}]}""",
        )
        val report = ResultParameters.parse(metadata, "3", "embedded")!!
        val text = report.copyText()
        assertTrue(report.isRuntimeRecord)
        assertTrue(text.contains("9007199254740991"))
        assertTrue(text.contains("this image"))
        assertFalse(text.contains("other image"))
        assertFalse(text.contains("-1"))
        assertFalse(text.contains("[\"1\",0]"))
    }

    @Test fun literalImageListsAreNotMisinterpretedAsConnections() {
        val metadata = ImageGenerationMetadata(promptJson = """{"9":{"class_type":"MultiImage","inputs":{"images":["a.png","b.png"],"strength":0.5}}}""")
        val report = ResultParameters.parse(metadata, "9", "embedded")!!
        assertTrue(report.copyText().contains("a.png"))
        assertTrue(report.copyText().contains("0.5"))
    }

    @Test fun workflowOnlySnapshotIsClearlyDistinguishedFromRuntimeRecord() {
        val metadata = ImageGenerationMetadata(workflowJson = """{"nodes":[{"id":7,"type":"Seed","widgets_values":[-1]}]}""")
        val report = ResultParameters.parse(metadata, "8", "embedded")!!
        assertFalse(report.isRuntimeRecord)
        assertTrue(report.note.contains("生成前"))
        assertTrue(report.copyText().contains("-1"))
        assertNull(ResultParameters.parse(ImageGenerationMetadata(), "8", "embedded"))
    }
}
