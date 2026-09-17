package com.local.comfyuimobile.network

import com.local.comfyuimobile.model.JobState
import com.local.comfyuimobile.model.JobSummary
import java.net.ServerSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TaskCancellationTest {
    private fun verify(status: Int, block: suspend (ComfyClient, List<String>) -> Unit) = runTest {
        val paths = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    it.soTimeout = 5_000
                    val input = it.getInputStream().bufferedReader()
                    val first = input.readLine().split(" ")
                    paths += "${first[0]} ${first[1]}"
                    while (!input.readLine().isNullOrEmpty()) { /* headers */ }
                    it.getOutputStream().write("HTTP/1.1 $status Test\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                }
            }
        }
        try { block(ComfyClient().apply { setServer("http://127.0.0.1:${server.localPort}") }, paths) }
        finally { server.close(); worker.join(1_000) }
    }

    @Test fun cancellationUsesActualTargetedPostEndpoint() = verify(200) { client, paths ->
        client.cancel(JobSummary("target", JobState.RUNNING))
        assertEquals(listOf("POST /api/jobs/target/cancel"), paths)
    }

    @Test fun unsupportedOrFailedCancellationNeverInterruptsAnotherJob() = verify(404) { client, paths ->
        assertTrue(runCatching { client.cancel(JobSummary("target", JobState.RUNNING)) }.isFailure)
        assertEquals(listOf("POST /api/jobs/target/cancel"), paths)
        assertFalse(paths.any { it.contains("/interrupt") })
    }
}
