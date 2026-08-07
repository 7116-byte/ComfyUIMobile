package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary

data class ActiveJobSelection(
    val job: JobSummary?,
    val isTakeover: Boolean,
)

/** 区分本次刚提交的任务和应用重启后需要恢复跟踪的任务。 */
object ActiveJobRecovery {
    fun select(
        activeJobId: String?,
        jobs: List<JobSummary>,
        awaitingQueueJobIds: Set<String>,
        explicitlyTrackedIds: Set<String> = emptySet(),
    ): ActiveJobSelection {
        // 本 App 提交的任务自动跟踪；用户在任务页主动点接管的任务，无论是否本
        // App 提交，都保持跟踪直到结束。
        val activeJobs = jobs.filter {
            (it.submittedByApp || it.id in explicitlyTrackedIds) &&
                it.state in setOf(JobState.RUNNING, JobState.PENDING)
        }
        activeJobId?.let { currentId ->
            activeJobs.firstOrNull { it.id == currentId }?.let {
                return ActiveJobSelection(it, isTakeover = false)
            }
            // POST /prompt 已成功，但 /queue 可能还没来得及返回该任务。此时不能跳去旧任务。
            if (currentId in awaitingQueueJobIds) return ActiveJobSelection(null, isTakeover = false)
        }

        val recovered = activeJobs.firstOrNull { it.state == JobState.RUNNING }
            ?: activeJobs.firstOrNull()
        return ActiveJobSelection(
            job = recovered,
            isTakeover = recovered != null && recovered.id != activeJobId,
        )
    }

    /**
     * ComfyUI 重连时补发的 executing 消息只有 node、没有 prompt_id。
     * 仅允许把它关联到本 App 当前明确的运行任务。
     */
    fun resolveEventPromptId(
        eventPromptId: String?,
        activeJobId: String?,
        jobs: List<JobSummary>,
    ): String? {
        eventPromptId?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        activeJobId?.let { currentId ->
            jobs.firstOrNull {
                it.id == currentId && it.state == JobState.RUNNING
            }?.let { return it.id }
        }
        return jobs.firstOrNull {
            it.submittedByApp && it.state == JobState.RUNNING
        }?.id
    }

    /** 重连补发的当前节点比断线前留在界面上的旧节点更新。 */
    fun currentNodeId(reconnectNodeId: String?, trackedNodeId: String?): String? =
        reconnectNodeId?.trim()?.takeIf { it.isNotBlank() }
            ?: trackedNodeId?.trim()?.takeIf { it.isNotBlank() }
}
