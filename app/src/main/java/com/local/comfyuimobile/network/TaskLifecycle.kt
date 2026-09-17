package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import com.local.comfyuimobile.model.WorkflowDocument
import com.local.comfyuimobile.model.AppUiState
import org.json.JSONObject

/** Shared by queue recovery, the foreground service and its completion receiver. */
object TaskLifecycle {
    fun settle(ui: AppUiState, server: String, id: String, state: JobState, message: String): AppUiState {
        if (!isTerminal(state) || !matches(server, id, ui.activeServer?.baseUrl, ui.activeJobId)) return ui
        return ui.copy(currentExecutingNodeId = null, generationProgress = if (state == JobState.SUCCESS) 1f else null,
            generationMessage = message)
    }

    fun ownsDocument(job: JobSummary, document: WorkflowDocument?): Boolean = when {
        document == null -> false
        document.sourceJobId != null -> document.sourceJobId == job.id
        job.workflowPath.isNotBlank() -> document.entry.path == job.workflowPath
        else -> job.submittedByApp
    }

    fun key(server: String, id: String): String = "${server.trimEnd('/')}|$id"

    fun trackedIds(keys: Set<String>, server: String): Set<String> {
        val prefix = "${server.trimEnd('/')}|"
        return keys.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
    }

    fun isTerminal(state: JobState?) = state in setOf(JobState.SUCCESS, JobState.ERROR, JobState.CANCELLED)

    fun historyState(status: JSONObject?): JobState {
        if (status == null) return JobState.UNKNOWN
        val messages = status.optJSONArray("messages")
        if (messages != null && (0 until messages.length()).any {
                messages.optJSONArray(it)?.optString(0) == "execution_interrupted"
            }) return JobState.CANCELLED
        return when {
            status.optString("status_str").equals("error", true) -> JobState.ERROR
            status.optBoolean("completed") || status.optString("status_str") == "success" -> JobState.SUCCESS
            else -> JobState.UNKNOWN
        }
    }

    fun matches(server: String, id: String, activeServer: String?, activeId: String?): Boolean =
        id.isNotBlank() && id == activeId && server.trimEnd('/') == activeServer?.trimEnd('/')
}
