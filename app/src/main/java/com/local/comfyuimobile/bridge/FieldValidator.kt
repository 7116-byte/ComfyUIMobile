package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.FieldProblem

object FieldValidator {
    fun detailedProblems(fields: List<ParameterField>): List<FieldProblem> = buildList {
        fields.filterNot { it.linked }.forEach { field ->
            val message = when (field.kind) {
                ParameterKind.INTEGER -> if (field.displayValue.toLongOrNull() == null) "${field.label} 不是有效整数" else null
                ParameterKind.DECIMAL -> if (field.displayValue.toDoubleOrNull()?.isFinite() != true) "${field.label} 不是有效数字" else null
                ParameterKind.COMBO -> if (field.options.isNotEmpty() && field.displayValue !in field.options) "${field.label} 的选项已经失效" else null
                ParameterKind.IMAGE, ParameterKind.VIDEO -> if (field.displayValue.isBlank()) "${field.label} 尚未选择文件" else null
                else -> null
            }
            if (message != null) add(FieldProblem(field.key, field.nodeId, message))
        }
    }

    fun problems(fields: List<ParameterField>): List<String> = detailedProblems(fields).map { it.message }
}
