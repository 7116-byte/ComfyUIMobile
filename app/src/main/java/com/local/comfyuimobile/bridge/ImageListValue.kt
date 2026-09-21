package com.local.comfyuimobile.bridge

import org.json.JSONArray

object ImageListValue {
    fun parse(value: String): List<String>? = runCatching {
        val array = JSONArray(value)
        buildList {
            repeat(array.length()) { index ->
                val item = array.opt(index)
                if (item !is String) error("第 ${index + 1} 项不是图片路径")
                item.takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrNull()

    fun encode(values: List<String>): String = JSONArray(values.filter(String::isNotBlank).distinct()).toString()

    fun append(current: String, added: List<String>): String = encode(
        (parse(current).orEmpty() + added).distinct(),
    )
}
