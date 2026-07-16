package com.local.comfyuimobile.data

import com.local.comfyuimobile.model.CacheOutputRule
import com.local.comfyuimobile.model.ResultMedia

object CachePolicy {
    fun shouldCache(
        media: ResultMedia,
        submittedJobIds: Set<String>,
        rules: List<CacheOutputRule>,
        serverUrl: String,
    ): Boolean = media.jobId in submittedJobIds && media.workflowPath.isNotBlank() && rules.any { rule ->
        rule.enabled &&
            rule.serverUrl == serverUrl &&
            rule.workflowPath == media.workflowPath &&
            rule.nodeId == media.nodeId
    }
}
