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
}
