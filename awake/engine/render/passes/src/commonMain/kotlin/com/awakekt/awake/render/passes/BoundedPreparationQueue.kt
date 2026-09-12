/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Owner-scoped bounded CPU preparation handoff.
 *
 * The queue carries caller-owned immutable snapshots and returns deferred CPU results. It never
 * records commands or owns GPU resources. [capacity] provides backpressure, [workers] bounds
 * parallel preparation, and [close] cancels outstanding work with the owning render session.
 */
class BoundedPreparationQueue<I, O>(
    scope: CoroutineScope,
    capacity: Int,
    workers: Int = 1,
    private val prepare: suspend (I) -> O,
) : AutoCloseable {
    private data class Request<I, O>(
        val input: I,
        val result: CompletableDeferred<O>,
    )

    private val requests = Channel<Request<I, O>>(
        capacity.also {
            require(it > 0) { "Preparation queue capacity must be positive." }
        },
    )
    private val workerJobs: List<Job> = List(
        workers.also {
            require(it > 0) { "Preparation queue worker count must be positive." }
        },
    ) {
        scope.launch {
            for (request in requests) {
                try {
                    request.result.complete(prepare(request.input))
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    request.result.cancel(cancelled)
                    throw cancelled
                } catch (failure: Throwable) {
                    request.result.completeExceptionally(failure)
                }
            }
        }
    }

    /** Suspends when [capacity] requests are already waiting. */
    suspend fun submit(input: I): Deferred<O> {
        val result = CompletableDeferred<O>()
        requests.send(Request(input, result))
        return result
    }

    override fun close() {
        requests.close()
        while (true) {
            val request = requests.tryReceive().getOrNull() ?: break
            request.result.cancel()
        }
        workerJobs.forEach(Job::cancel)
    }
}
