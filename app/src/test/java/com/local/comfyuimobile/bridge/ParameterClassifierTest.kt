package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection
import org.junit.Assert.assertEquals
import org.junit.Test

class ParameterClassifierTest {
    @Test fun classifiesImportantNativeControls() {
        assertEquals(ParameterKind.MULTILINE, ParameterClassifier.kind("CLIPTextEncode", "text", "customtext", "prompt", emptyList()))
        assertEquals(ParameterKind.IMAGE, ParameterClassifier.kind("LoadImage", "image", "combo", "a.png", emptyList()))
        assertEquals(ParameterKind.COMBO, ParameterClassifier.kind("KSampler", "sampler_name", "combo", "euler", listOf("euler")))
        assertEquals(ParameterKind.BOOLEAN, ParameterClassifier.kind("Node", "enabled", "toggle", true, emptyList()))
        assertEquals(ParameterKind.INTEGER, ParameterClassifier.kind("EmptyLatentImage", "width", "number", 1024, emptyList()))
    }

    @Test fun keepsFloatInputsDecimalEvenWhenCurrentValueIsWhole() {
        assertEquals(
            ParameterKind.DECIMAL,
            ParameterClassifier.kind(
                nodeType = "LoraLoaderModelOnly",
                name = "strength_model",
                widgetType = "number",
                value = 0,
                options = emptyList(),
                dataType = "FLOAT",
                minimum = -100.0,
                maximum = 100.0,
                step = 0.01,
            ),
        )
    }

    @Test fun recognizesCustomOrderedImageListUpload() {
        assertEquals(
            ParameterKind.IMAGE_LIST,
            ParameterClassifier.kind(
                nodeType = "QwenImage21MultiImageUpload",
                name = "images",
                widgetType = "string",
                value = "[]",
                options = emptyList(),
                dataType = "STRING",
                imageListUpload = true,
            ),
        )
        assertEquals(
            ParameterSection.PRIMARY,
            ParameterClassifier.section("QwenImage21MultiImageUpload", "images", ParameterKind.IMAGE_LIST),
        )
    }

    @Test fun doesNotTreatEveryFieldInAnImageUploadNodeAsMedia() {
        val nodeType = "QwenImage21MultiImageUpload"
        assertEquals(
            ParameterKind.MULTILINE,
            ParameterClassifier.kind(
                nodeType, "prompt", "customtext", "编辑 <image1>", emptyList(),
                dataType = "STRING", multiline = true,
            ),
        )
        assertEquals(
            ParameterKind.MULTILINE,
            ParameterClassifier.kind(
                nodeType, "negative_prompt", "customtext", "", emptyList(),
                dataType = "STRING", multiline = true,
            ),
        )
        assertEquals(
            ParameterKind.INTEGER,
            ParameterClassifier.kind(
                nodeType, "resolution", "number", 1024, emptyList(),
                dataType = "INT", minimum = 0.0, maximum = 4096.0, step = 32.0,
            ),
        )
    }

    @Test fun putsPromptMediaAndSamplerFieldsInPrimarySection() {
        assertEquals(ParameterSection.PRIMARY, ParameterClassifier.section("CLIPTextEncode", "text", ParameterKind.MULTILINE))
        assertEquals(ParameterSection.PRIMARY, ParameterClassifier.section("KSampler", "control_after_generate", ParameterKind.COMBO))
        assertEquals(ParameterSection.MORE, ParameterClassifier.section("AnyNode", "internal_gain", ParameterKind.DECIMAL))
    }

    @Test fun localizesCommonWebUiStyleLabels() {
        assertEquals("负向提示词", ParameterClassifier.label("Negative Prompt", "text", "text"))
        assertEquals("采样步数", ParameterClassifier.label("KSampler", "steps", "steps"))
        assertEquals("输入图片", ParameterClassifier.label("LoadImage", "image", "image"))
        assertEquals("负向提示词", ParameterClassifier.label("Qwen Image 2.1 多图上传编码", "negative_prompt", "negative_prompt"))
        assertEquals("参考图分辨率", ParameterClassifier.label("Qwen Image 2.1 多图上传编码", "resolution", "resolution"))
        assertEquals("custom gain", ParameterClassifier.label("Custom", "gain", "custom gain"))
    }
}
