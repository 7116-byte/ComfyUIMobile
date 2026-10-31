package com.local.comfyuimobile.update

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal object UpdateRace {
    suspend fun <T> firstSuccessful(loaders: List<suspend () -> T>): T = coroutineScope {
        require(loaders.isNotEmpty())
        val results = Channel<Result<T>>(Channel.UNLIMITED)
        val workers = loaders.map { load -> launch {
            val result = try { Result.success(load()) } catch (error: Exception) {
                if (error is CancellationException) throw error
                Result.failure(error)
            }
            results.send(result)
        } }
        try {
            var lastError: Throwable? = null
            repeat(loaders.size) {
                val result = results.receive()
                if (result.isSuccess) return@coroutineScope result.getOrThrow()
                lastError = result.exceptionOrNull()
            }
            throw IOException("所有更新来源均不可用，请稍后重试", lastError)
        } finally {
            workers.forEach { it.cancel() }
            results.cancel()
        }
    }
}
