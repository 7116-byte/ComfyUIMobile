package com.local.comfyuimobile.data

import com.local.comfyuimobile.bridge.ImageGenerationMetadata
import com.local.comfyuimobile.bridge.ParameterClassifier
import org.json.JSONArray
import org.json.JSONObject

data class RecordedParameter(val label: String, val value: String)
data class RecordedParameterGroup(val title: String, val values: List<RecordedParameter>)
data class ResultParameterReport(
    val source: String,
    val groups: List<RecordedParameterGroup>,
    val note: String = "",
    val isRuntimeRecord: Boolean = false,
) {
    fun copyText(): String = buildString {
        append(source)
        if (note.isNotBlank()) append("\n$note")
        groups.forEach { group ->
            append("\n\n${group.title}")
            group.values.forEach { append("\n${it.label}：${it.value}") }
        }
    }
}

/** Read recorded values only; never instantiate a graph or execute node callbacks. */
object ResultParameters {
    fun parse(metadata: ImageGenerationMetadata, outputNodeId: String, source: String): ResultParameterReport? {
        val workflow = metadata.workflowJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val titles = mutableMapOf<String, String>()
        val nodes = workflow?.optJSONArray("nodes") ?: JSONArray()
        repeat(nodes.length()) { index ->
            val node = nodes.optJSONObject(index) ?: return@repeat
            titles[node.optString("id")] = node.optString("title").ifBlank { node.optString("type") }
        }
        val prompt = metadata.promptJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (prompt != null && prompt.length() > 0) {
            val selected = linkedSetOf<String>()
            val pending = java.util.ArrayDeque<String>()
            if (prompt.has(outputNodeId)) pending.add(outputNodeId) else selected.addAll(prompt.keys().asSequence().toList())
            while (pending.isNotEmpty()) {
                val id = pending.removeFirst()
                if (!selected.add(id)) continue
                val inputs = prompt.optJSONObject(id)?.optJSONObject("inputs") ?: continue
                inputs.keys().forEach { name -> link(inputs.opt(name), prompt)?.let(pending::addLast) }
            }
            val groups = selected.sortedWith(compareBy<String> { it.toLongOrNull() ?: Long.MAX_VALUE }.thenBy { it })
                .mapNotNull { id ->
                    val node = prompt.optJSONObject(id) ?: return@mapNotNull null
                    val type = node.optString("class_type")
                    val title = titles[id].orEmpty().ifBlank { node.optJSONObject("_meta")?.optString("title").orEmpty() }.ifBlank { type }
                    val inputs = node.optJSONObject("inputs") ?: return@mapNotNull null
                    val fields = inputs.keys().asSequence().mapNotNull { name ->
                        val value = inputs.opt(name)
                        if (link(value, prompt) != null || value == null || value == JSONObject.NULL) null
                        else RecordedParameter(ParameterClassifier.label(title, name, name), value.toString())
                    }.toList()
                    fields.takeIf { it.isNotEmpty() }?.let { RecordedParameterGroup("$title · #$id", it) }
                }
            if (groups.isNotEmpty()) return ResultParameterReport(source, groups,
                if (prompt.has(outputNodeId)) "" else "记录中未找到该输出节点，以下为整个任务的生成参数。", isRuntimeRecord = true)
        }
        metadata.parameters?.takeIf { it.isNotBlank() }?.let {
            return ResultParameterReport(source, listOf(RecordedParameterGroup("图片内嵌参数", listOf(RecordedParameter("生成参数", it)))), isRuntimeRecord = true)
        }
        val groups = (0 until nodes.length()).mapNotNull { index ->
            val node = nodes.optJSONObject(index) ?: return@mapNotNull null
            val id = node.optString("id")
            val values = node.opt("widgets_values")
            val fields = when (values) {
                is JSONArray -> (0 until values.length()).map { RecordedParameter("控件 ${it + 1}", values.opt(it)?.toString().orEmpty()) }
                is JSONObject -> values.keys().asSequence().map { RecordedParameter(it, values.opt(it)?.toString().orEmpty()) }.toList()
                else -> emptyList()
            }
            fields.takeIf { it.isNotEmpty() }?.let { RecordedParameterGroup("${titles[id]} · #$id", it) }
        }
        return groups.takeIf { it.isNotEmpty() }?.let {
            ResultParameterReport(source, it, "仅有工作流控件快照；随机种子等可能是生成前设置，未记录实际运行值。")
        }
    }

    private fun link(value: Any?, prompt: JSONObject): String? {
        if (value !is JSONArray || value.length() != 2 || value.opt(1) !is Number) return null
        val id = value.optString(0)
        return id.takeIf { prompt.optJSONObject(it) != null }
    }
}
