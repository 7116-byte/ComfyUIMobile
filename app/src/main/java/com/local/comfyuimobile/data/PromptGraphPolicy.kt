package com.local.comfyuimobile.data

import org.json.JSONArray
import org.json.JSONObject

data class PromptFilterResult(
    val prompt: JSONObject,
    val originalCount: Int,
    val retainedCount: Int,
    val matchedRootCount: Int,
    val keptFullPrompt: Boolean,
)

/**
 * 只保留手机参数页所对应执行链，同时保留原生组合子图展开后的内部节点。
 *
 * ComfyUI 的 graphToPrompt() 会把原生组合子图展开成新的 Prompt 节点 ID。不能再用
 * 画布根节点 ID 直接删除 Prompt 节点，否则组合子图下游的 SaveImage 等节点会引用到
 * 已被删除的内部节点，服务器会统一返回 “Exception when validating node”。
 */
object PromptGraphPolicy {
    fun retainExecutableAncestors(
        prompt: JSONObject,
        relevantCanvasNodeIds: Set<String>,
    ): PromptFilterResult {
        val promptIds = prompt.keys().asSequence().toSet()
        val matchedRoots = promptIds.intersect(relevantCanvasNodeIds)

        // 如果当前前端版本把所有根节点 ID 都改写了，宁可完整保留官方 Prompt，
        // 也不能猜测并删除组合子图节点，造成原本可运行的工作流断线。
        if (promptIds.isNotEmpty() && matchedRoots.isEmpty()) {
            return PromptFilterResult(
                prompt = prompt,
                originalCount = promptIds.size,
                retainedCount = promptIds.size,
                matchedRootCount = 0,
                keptFullPrompt = true,
            )
        }

        val retained = mutableSetOf<String>()
        val pending = ArrayDeque<String>().apply { addAll(matchedRoots) }
        while (pending.isNotEmpty()) {
            val nodeId = pending.removeLast()
            if (!retained.add(nodeId)) continue
            val inputs = prompt.optJSONObject(nodeId)?.optJSONObject("inputs") ?: continue
            inputs.keys().forEach { inputName ->
                collectConnectedNodeIds(inputs.opt(inputName), promptIds).forEach { sourceId ->
                    if (sourceId !in retained) pending.add(sourceId)
                }
            }
        }

        promptIds.filterNot(retained::contains).forEach(prompt::remove)
        return PromptFilterResult(
            prompt = prompt,
            originalCount = promptIds.size,
            retainedCount = retained.size,
            matchedRootCount = matchedRoots.size,
            keptFullPrompt = false,
        )
    }

    private fun collectConnectedNodeIds(value: Any?, promptIds: Set<String>): Set<String> {
        if (value !is JSONArray) return emptySet()
        val directId = value.opt(0)?.toString()
        val directSlot = value.opt(1)
        if (value.length() == 2 && directId in promptIds && directSlot is Number) {
            return setOf(directId!!)
        }
        return buildSet {
            repeat(value.length()) { index -> addAll(collectConnectedNodeIds(value.opt(index), promptIds)) }
        }
    }
}
