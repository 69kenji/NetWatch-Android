package com.netwatch.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class PlaybackProgress(
    val positionSeconds: Double,
    val durationSeconds: Double,
    val sequence: Int,
)

internal class PlaybackProgressReporter(
    scope: CoroutineScope,
    private val send: suspend (PlaybackProgress) -> Unit,
    private val onResult: (PlaybackProgress, Throwable?) -> Unit = { _, _ -> },
    private val retryDelayMs: Long = 2_000,
) {
    private val updates = Channel<PlaybackProgress>(Channel.CONFLATED)
    private var nextSequence = 0
    private val worker: Job = scope.launch {
        for (received in updates) {
            var pending = received
            while (isActive) {
                val result = runCatching { send(pending) }
                onResult(pending, result.exceptionOrNull())
                if (result.isSuccess) break
                delay(retryDelayMs)
                while (true) {
                    val newer = updates.tryReceive().getOrNull() ?: break
                    pending = newer
                }
            }
        }
    }

    @Synchronized
    fun submit(positionSeconds: Double, durationSeconds: Double): Boolean {
        if (!positionSeconds.isFinite() || positionSeconds < 0 || !durationSeconds.isFinite() || durationSeconds <= 0) return false
        nextSequence = (nextSequence + 1).coerceAtMost(Int.MAX_VALUE)
        return updates.trySend(PlaybackProgress(positionSeconds.coerceAtMost(durationSeconds), durationSeconds, nextSequence)).isSuccess
    }

    suspend fun close(positionSeconds: Double?, durationSeconds: Double?) {
        if (positionSeconds != null && durationSeconds != null) submit(positionSeconds, durationSeconds)
        updates.close()
        withTimeoutOrNull(5_000) { worker.join() }
        worker.cancel()
    }

    fun cancel() {
        updates.close()
        worker.cancel()
    }
}
