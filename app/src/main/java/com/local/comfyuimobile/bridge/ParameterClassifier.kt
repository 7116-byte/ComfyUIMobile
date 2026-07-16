package com.local.comfyuimobile.bridge

import com.local.comfyuimobile.model.ParameterKind
import com.local.comfyuimobile.model.ParameterSection

object ParameterClassifier {
    private val primaryNames = setOf(
        "text", "prompt", "positive", "negative", "seed", "noise_seed", "width", "height",
        "batch_size", "steps", "cfg", "denoise", "sampler_name", "scheduler", "image", "video",
    )

    fun kind(nodeType: String, name: String, widgetType: String, value: Any?, options: List<String>): ParameterKind {
        val token = "$nodeType $name $widgetType".lowercase()
        if (token.contains("image") && (token.contains("upload") || name.equals("image", true))) return ParameterKind.IMAGE
        if (token.contains("video") && (token.contains("upload") || name.equals("video", true))) return ParameterKind.VIDEO
        if (options.isNotEmpty() || widgetType.contains("combo", true)) return ParameterKind.COMBO
        if (value is Boolean || widgetType.contains("toggle", true)) return ParameterKind.BOOLEAN
        if (value is Int || value is Long || widgetType.equals("number", true) && !value.toString().contains('.')) return ParameterKind.INTEGER
        if (value is Number || widgetType.contains("slider", true)) return ParameterKind.DECIMAL
        if (value is String) {
            return if (token.contains("multiline") || token.contains("cliptextencode") || name.equals("text", true)) {
                ParameterKind.MULTILINE
            } else {
                ParameterKind.TEXT
            }
        }
        return ParameterKind.UNSUPPORTED
    }

    fun section(nodeType: String, name: String, kind: ParameterKind): ParameterSection {
        val normalized = name.lowercase()
        return if (
            normalized in primaryNames || kind in setOf(ParameterKind.MULTILINE, ParameterKind.IMAGE, ParameterKind.VIDEO) ||
            nodeType.contains("KSampler", true)
        ) ParameterSection.PRIMARY else ParameterSection.MORE
    }

    fun label(nodeTitle: String, name: String, original: String): String {
        val normalized = name.lowercase()
        return when (normalized) {
            "text", "prompt" -> when {
                nodeTitle.contains("negative", true) || nodeTitle.contains("负", true) -> "负向提示词"
                nodeTitle.contains("positive", true) || nodeTitle.contains("正", true) -> "正向提示词"
                else -> "提示词"
            }
            "seed", "noise_seed" -> "种子"
            "control_after_generate" -> "生成后种子策略"
            "width" -> "宽度"
            "height" -> "高度"
            "batch_size" -> "批量数量"
            "steps" -> "采样步数"
            "cfg" -> "CFG 引导系数"
            "denoise" -> "降噪强度"
            "sampler_name" -> "采样器"
            "scheduler" -> "调度器"
            "image" -> "输入图片"
            "video" -> "输入视频"
            "filename_prefix" -> "文件名前缀"
            else -> original.ifBlank { name }
        }
    }
}
