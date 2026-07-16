package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldValidatorTest {
    @Test fun rejectsInvalidNativeValuesButKeepsUnsupportedControls() {
        val fields = listOf(
            field("steps", ParameterKind.INTEGER, "x"),
            field("sampler", ParameterKind.COMBO, "missing", listOf("euler")),
            field("image", ParameterKind.IMAGE, ""),
            field("mask", ParameterKind.UNSUPPORTED, ""),
        )
        val problems = FieldValidator.problems(fields)
        assertEquals(3, problems.size)
        assertTrue(problems.any { it.contains("steps") })
    }

    @Test fun ignoresValuesControlledByLinks() {
        assertTrue(FieldValidator.problems(listOf(field("steps", ParameterKind.INTEGER, "x", linked = true))).isEmpty())
    }

    private fun field(
        name: String,
        kind: ParameterKind,
        value: String,
        options: List<String> = emptyList(),
        linked: Boolean = false,
    ) = ParameterField(
        key = "1/$name", nodeId = "1", nodeTitle = "Node", nodeType = "Node", name = name,
        label = name, widgetType = "widget", kind = kind, valueJson = "null", displayValue = value,
        options = options, linked = linked,
    )
}
