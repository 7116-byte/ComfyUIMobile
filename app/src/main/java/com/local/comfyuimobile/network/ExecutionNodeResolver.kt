package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.WorkflowNode

/** 把 ComfyUI 运行时节点编号还原成参数页显示的外层节点编号。 */
object ExecutionNodeResolver {
    fun resolve(runtimeNodeId: String?, visibleNodes: List<WorkflowNode>): String? {
        val runtimeId = runtimeNodeId?.trim().orEmpty()
        if (runtimeId.isBlank()) return null
        if (visibleNodes.any { it.id == runtimeId }) return runtimeId

        // 原生组合子图会把内部节点 75 展开为 68:75；参数页只显示外层节点 68。
        return visibleNodes
            .asSequence()
            .map { it.id }
            .filter { outerId ->
                runtimeId.startsWith("$outerId:") ||
                    runtimeId.startsWith("$outerId/") ||
                    runtimeId.startsWith("$outerId.")
            }
            .maxByOrNull { it.length }
            ?: runtimeId
    }
}
