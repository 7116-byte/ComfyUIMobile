package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.MediaKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultParserTest {
    @Test fun recursivelyFindsImageAndVideoAndEncodesNames() {
        val history = JSONObject(
            """{"job-1":{"outputs":{"9":{"images":[{"filename":"测试 图.png","subfolder":"日期 1","type":"output"}],"nested":{"files":[{"filename":"clip.mp4","subfolder":"video","type":"temp"},{"filename":"sound.wav"}]}}}}}""",
        )
        val result = ResultParser.parse("http://192.168.1.2:8188", history)
        assertEquals(2, result.size)
        assertEquals(setOf(MediaKind.IMAGE, MediaKind.VIDEO), result.map { it.kind }.toSet())
        val image = result.first { it.kind == MediaKind.IMAGE }
        assertTrue(image.url.contains("%E6%B5%8B%E8%AF%95%20%E5%9B%BE.png"))
        assertTrue(image.url.contains("subfolder=%E6%97%A5%E6%9C%9F%201"))
    }

    @Test fun sortsNewestTasksFirstAndReadsMobileWorkflowMetadata() {
        val history = JSONObject(
            """{
              "old":{"prompt":[1,"old",{}, {"create_time":100,"comfy_mobile":{"workflow_path":"workflows/a.json","workflow_name":"A"}}],"outputs":{"9":{"images":[{"filename":"old.png"}]}}},
              "new":{"prompt":[2,"new",{}, {"create_time":200,"comfy_mobile":{"workflow_path":"workflows/b.json","workflow_name":"B"}}],"outputs":{"8":{"images":[{"filename":"new.png"}]}}}
            }""",
        )
        val result = ResultParser.parse("http://192.168.1.2:8188", history)
        assertEquals("new.png", result.first().filename)
        assertEquals("workflows/b.json", result.first().workflowPath)
        assertEquals("B", result.first().workflowName)
    }

    @Test fun keepsEveryImageFromOneBatchAndOneOutputNode() {
        val history = JSONObject(
            """{"batch-job":{"prompt":[3,"batch-job",{}, {"create_time":300,"comfy_mobile":{"workflow_path":"workflows/a.json"},"extra_pnginfo":{"workflow":{"nodes":[{"id":305,"type":"SaveImage","title":"保存图像"}]}}}],"outputs":{"305":{"images":[
              {"filename":"batch_00001.png","type":"output"},
              {"filename":"batch_00002.png","type":"output"},
              {"filename":"batch_00003.png","type":"output"},
              {"filename":"batch_00004.png","type":"output"}
            ]}}}}""",
        )

        val result = ResultParser.parse("http://192.168.10.109:8188", history)

        assertEquals(4, result.size)
        assertEquals(setOf("SaveImage"), result.map { it.nodeType }.toSet())
        assertEquals(setOf("保存图像"), result.map { it.nodeTitle }.toSet())
        assertEquals(setOf("batch_00001.png", "batch_00002.png", "batch_00003.png", "batch_00004.png"), result.map { it.filename }.toSet())
    }

    @Test fun sameCloudFileAddressFromDifferentJobsHasDistinctUiKeys() {
        val history = JSONObject(
            """{
              "job-a":{"outputs":{"350":{"images":[{"filename":"ComfyUI_temp_00001_.png","type":"temp"}]}}},
              "job-b":{"outputs":{"350":{"images":[{"filename":"ComfyUI_temp_00001_.png","type":"temp"}]}}}
            }""",
        )

        val result = ResultParser.parse("http://192.168.10.109:8188", history)

        assertEquals(2, result.size)
        assertEquals(2, result.map { it.stableKey() }.toSet().size)
        assertEquals(1, result.map { it.url }.toSet().size)
    }
}
