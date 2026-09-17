package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterField
import com.local.comfyuimobile.model.ParameterKind

/** The initial manifest is not the live canvas: A -> B -> A must send A again. */
object ParameterSync {
    fun editable(fields: List<ParameterField>): List<ParameterField> =
        fields.filter { !it.linked && it.kind != ParameterKind.UNSUPPORTED }
}
