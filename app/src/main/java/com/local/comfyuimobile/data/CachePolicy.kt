package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.ResultMedia

object CachePolicy {
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
