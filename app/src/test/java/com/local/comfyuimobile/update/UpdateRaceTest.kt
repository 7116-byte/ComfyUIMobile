package com.local.comfyuimobile.update

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UpdateRaceTest {
    @Test fun validNoUpdateResultReturnsWithoutWaitingForHangingMirror() = runBlocking {
        var cancelled = false
        val result = withTimeout(1_000) {
            UpdateRace.firstSuccessful<String?>(listOf(
                { try { awaitCancellation() } finally { cancelled = true } },
                { throw IOException("HTTP 403") },
                { null },
            ))
        }
        assertNull(result)
        assertTrue(cancelled)
    }

    @Test fun allFailedSourcesProduceError() = runBlocking {
        val result = runCatching { UpdateRace.firstSuccessful<String>(listOf(
            { throw IOException("HTTP 403") }, { throw IOException("offline") },
        )) }
        assertTrue(result.exceptionOrNull() is IOException)
    }
}
