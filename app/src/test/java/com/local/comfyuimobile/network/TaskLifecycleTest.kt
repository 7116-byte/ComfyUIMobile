package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskLifecycleTest {
    private val running = AppUiState(activeServer = ServerProfile("a", "a", "http://a"), activeJobId = "A",
        currentExecutingNodeId = "12", generationProgress = 0.4f, generationMessage = "正在生成")

    @Test fun historyCompletionClearsGreenFrameWithoutWebSocketEvent() {
        val settled = TaskLifecycle.settle(running, "http://a", "A", JobState.SUCCESS, "完成")
        assertNull(settled.currentExecutingNodeId)
        assertEquals(1f, settled.generationProgress)
        assertEquals("完成", settled.generationMessage)
    }

    @Test fun completionOfAnotherTaskCannotOverwriteCurrentTask() {
        assertEquals(running, TaskLifecycle.settle(running, "http://a", "B", JobState.SUCCESS, "B保存完成"))
        assertEquals(running, TaskLifecycle.settle(running, "http://b", "A", JobState.SUCCESS, "另一服务器完成"))
    }

    @Test fun errorClearsOldProgressRatherThanShowingOneHundredPercent() {
        val settled = TaskLifecycle.settle(running, "http://a", "A", JobState.ERROR, "失败")
        assertNull(settled.currentExecutingNodeId)
        assertNull(settled.generationProgress)
    }

    @Test fun errorWithCompletedFalseIsTerminal() {
        val state = TaskLifecycle.historyState(JSONObject("""{"completed":false,"status_str":"error"}"""))
        assertEquals(JobState.ERROR, state)
        assertTrue(TaskLifecycle.isTerminal(state))
    }

    @Test fun interruptionIsCancellationNotSuccess() {
        assertEquals(JobState.CANCELLED, TaskLifecycle.historyState(JSONObject(
            """{"completed":false,"status_str":"error","messages":[["execution_interrupted",{}]]}""")))
    }

    @Test fun absentHistoryDoesNotMeanSuccess() {
        assertFalse(TaskLifecycle.isTerminal(TaskLifecycle.historyState(null)))
        assertFalse(TaskLifecycle.isTerminal(TaskLifecycle.historyState(JSONObject())))
    }

    @Test fun successfulHistoryIsTerminal() {
        assertEquals(JobState.SUCCESS, TaskLifecycle.historyState(JSONObject("""{"completed":true,"status_str":"success"}""")))
    }

    @Test fun completionMustMatchBothTaskAndServer() {
        assertTrue(TaskLifecycle.matches("http://a/", "A", "http://a", "A"))
        assertFalse(TaskLifecycle.matches("http://a", "A", "http://a", "B"))
        assertFalse(TaskLifecycle.matches("http://a", "A", "http://b", "A"))
        assertFalse(TaskLifecycle.matches("http://a", "", "http://a", ""))
    }

    @Test fun persistedTakeoversAreServerScopedAndRecoverable() {
        val persisted = setOf(TaskLifecycle.key("http://a/", "foreign"), TaskLifecycle.key("http://b", "other"))
        val restored = TaskLifecycle.trackedIds(persisted, "http://a")
        assertEquals(setOf("foreign"), restored)
        val selection = ActiveJobRecovery.select(null, listOf(JobSummary("foreign", JobState.RUNNING)), emptySet(), restored)
        assertEquals("foreign", selection.job?.id)
    }

    @Test fun sameNodeIdsInAnotherWorkflowDoNotReceiveGreenFrame() {
        val doc = WorkflowDocument(WorkflowEntry("b", "workflows/b.json", false), "{}", emptyList())
        val job = JobSummary("A", JobState.RUNNING, workflowPath = "workflows/a.json", submittedByApp = true)
        assertFalse(TaskLifecycle.ownsDocument(job, doc))
        assertTrue(TaskLifecycle.ownsDocument(job, doc.copy(sourceJobId = "A")))
        assertFalse(TaskLifecycle.ownsDocument(job, doc.copy(sourceJobId = "B")))
    }
}
