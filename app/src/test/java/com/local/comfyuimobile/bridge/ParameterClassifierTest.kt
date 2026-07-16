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

    @Test fun putsPromptMediaAndSamplerFieldsInPrimarySection() {
        assertEquals(ParameterSection.PRIMARY, ParameterClassifier.section("CLIPTextEncode", "text", ParameterKind.MULTILINE))
        assertEquals(ParameterSection.PRIMARY, ParameterClassifier.section("KSampler", "control_after_generate", ParameterKind.COMBO))
        assertEquals(ParameterSection.MORE, ParameterClassifier.section("AnyNode", "internal_gain", ParameterKind.DECIMAL))
    }

    @Test fun localizesCommonWebUiStyleLabels() {
        assertEquals("负向提示词", ParameterClassifier.label("Negative Prompt", "text", "text"))
        assertEquals("采样步数", ParameterClassifier.label("KSampler", "steps", "steps"))
        assertEquals("输入图片", ParameterClassifier.label("LoadImage", "image", "image"))
        assertEquals("custom gain", ParameterClassifier.label("Custom", "gain", "custom gain"))
    }
}
