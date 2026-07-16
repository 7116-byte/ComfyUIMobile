package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind

object FieldValidator {
    fun problems(fields: List<ParameterField>): List<String> = buildList {
        fields.filterNot { it.linked }.forEach { field ->
            when (field.kind) {
                ParameterKind.INTEGER -> if (field.displayValue.toLongOrNull() == null) add("${field.label} 不是有效整数")
                ParameterKind.DECIMAL -> if (field.displayValue.toDoubleOrNull()?.isFinite() != true) add("${field.label} 不是有效数字")
                ParameterKind.COMBO -> if (field.options.isNotEmpty() && field.displayValue !in field.options) add("${field.label} 的选项已经失效")
                ParameterKind.IMAGE, ParameterKind.VIDEO -> if (field.displayValue.isBlank()) add("${field.label} 尚未选择文件")
                else -> Unit
            }
        }
    }
}
