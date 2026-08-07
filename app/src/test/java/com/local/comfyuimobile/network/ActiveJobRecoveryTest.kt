package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveJobRecoveryTest {
    @Test fun keepsCurrentFreshTaskWithoutCallingItTakeover() {
        val selection = ActiveJobRecovery.select("new", listOf(job("new", JobState.PENDING)), emptySet())

        assertEquals("new", selection.job?.id)
        assertFalse(selection.isTakeover)
    }

    @Test fun currentFreshTaskWinsOverOlderRunningTask() {
        val selection = ActiveJobRecovery.select(
            "new",
            listOf(job("old", JobState.RUNNING), job("new", JobState.PENDING)),
            emptySet(),
        )

        assertEquals("new", selection.job?.id)
        assertFalse(selection.isTakeover)
    }

    @Test fun doesNotFallBackToOldTaskWhileNewTaskIsNotVisibleInQueue() {
        val selection = ActiveJobRecovery.select(
            "new",
            listOf(job("old", JobState.RUNNING)),
            setOf("new"),
        )

        assertNull(selection.job)
        assertFalse(selection.isTakeover)
    }

    @Test fun recoversRunningTaskAfterColdStart() {
        val selection = ActiveJobRecovery.select(
            null,
            listOf(job("pending", JobState.PENDING), job("running", JobState.RUNNING)),
            emptySet(),
        )

        assertEquals("running", selection.job?.id)
        assertTrue(selection.isTakeover)
    }

    @Test fun explicitlyTrackedForeignTaskStaysSelected() {
        val foreign = job("foreign", JobState.RUNNING).copy(submittedByApp = false)

        val selection = ActiveJobRecovery.select("foreign", listOf(foreign), emptySet(), setOf("foreign"))

        assertEquals("foreign", selection.job?.id)
        assertFalse(selection.isTakeover)
    }

    @Test fun foreignTaskNotTrackedWithoutExplicitTakeover() {
        val foreign = job("foreign", JobState.RUNNING).copy(submittedByApp = false)

        val selection = ActiveJobRecovery.select(null, listOf(foreign), emptySet())

        assertNull(selection.job)
    }

    @Test fun reconnectExecutingWithoutPromptIdUsesCurrentRunningTask() {
        val running = job("running", JobState.RUNNING)

        assertEquals("running", ActiveJobRecovery.resolveEventPromptId(null, "running", listOf(running)))
    }

    @Test fun reconnectExecutingWithoutPromptIdUsesTrackedForeignTask() {
        val running = job("running", JobState.RUNNING).copy(submittedByApp = false)

        assertEquals("running", ActiveJobRecovery.resolveEventPromptId(null, "running", listOf(running)))
    }

    @Test fun reconnectExecutingWithoutPromptIdNeverUsesPendingTask() {
        val pending = job("pending", JobState.PENDING)

        assertNull(ActiveJobRecovery.resolveEventPromptId(null, "pending", listOf(pending)))
    }

    @Test fun reconnectExecutingWithoutPromptIdFindsOnlyRunningSubmittedTask() {
        val jobs = listOf(job("pending", JobState.PENDING), job("running", JobState.RUNNING))

        assertEquals("running", ActiveJobRecovery.resolveEventPromptId(null, null, jobs))
    }

    @Test fun explicitEventPromptIdAlwaysWins() {
        val running = job("running", JobState.RUNNING)

        assertEquals("event-job", ActiveJobRecovery.resolveEventPromptId("event-job", "running", listOf(running)))
    }

    @Test fun reconnectNodeWinsOverNodeTrackedBeforeDisconnect() {
        assertEquals("new-node", ActiveJobRecovery.currentNodeId("new-node", "old-node"))
    }

    @Test fun trackedNodeRemainsWhenReconnectDidNotSendOne() {
        assertEquals("old-node", ActiveJobRecovery.currentNodeId(null, "old-node"))
    }

    private fun job(id: String, state: JobState) = JobSummary(
        id = id,
        state = state,
        submittedByApp = true,
    )
}
