package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.ResultMedia
import org.json.JSONObject

object CachePolicy {
    fun requestedForTask(jobId: String, allowedJobIds: Set<String>, rules: List<CacheOutputRule>,
                         serverUrl: String, history: JSONObject): Boolean {
        if (jobId !in allowedJobIds) return false
        val prompt = history.optJSONObject(jobId)?.optJSONArray("prompt")?.optJSONObject(2) ?: return false
        val types = prompt.keys().asSequence().mapNotNull { prompt.optJSONObject(it)?.optString("class_type") }.toSet()
        return hasConfiguredOutput(rules, serverUrl, types)
    }

    fun hasConfiguredOutput(
        rules: List<CacheOutputRule>,
        serverUrl: String?,
        outputNodeTypes: Set<String>,
    ): Boolean = !serverUrl.isNullOrBlank() && rules.any { rule ->
        rule.enabled &&
            rule.serverUrl == serverUrl &&
            rule.nodeType in outputNodeTypes
    }

    fun shouldCache(
        media: ResultMedia,
        submittedJobIds: Set<String>,
        rules: List<CacheOutputRule>,
        serverUrl: String,
        cacheClearedAt: Long = 0L,
    ): Boolean = media.jobId in submittedJobIds && media.createdAt >= cacheClearedAt && media.nodeType.isNotBlank() && rules.any { rule ->
        rule.enabled &&
            rule.serverUrl == serverUrl &&
            rule.nodeType == media.nodeType
    }
}
