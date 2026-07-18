package com.local.comfyuimobile.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ParameterFieldTest {
    @Test
    fun originalValueSurvivesFormEdits() {
        val original = ParameterField(
            key = "1/text",
            nodeId = "1",
            nodeTitle = "文本编码",
            nodeType = "CLIPTextEncode",
            name = "text",
            label = "提示词",
            widgetType = "text",
            kind = ParameterKind.MULTILINE,
            valueJson = "\"原始值\"",
            displayValue = "原始值",
        )

        val edited = original.copy(valueJson = "\"修改值\"", displayValue = "修改值")

        assertEquals("\"原始值\"", edited.originalValueJson)
        assertEquals("\"修改值\"", edited.valueJson)
    }
}
