/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.gltf

import com.awakekt.awake.scene.runtime.SceneMeshLoad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The model files [resolver] is loading for [GltfAssetResolver.loadModel]: one load a file, however many
 * callers wait for it. Each reads and decodes the file off the frame thread into a staging resolver that
 * nothing else reads, and the first poll on the frame thread after it finishes adopts it into [resolver].
 *
 * Read and written on the frame thread only; the staging is the one part that runs off it.
 */
internal class GltfModelLoads(
    private val resolver: GltfAssetResolver,
    private val newStaging: () -> GltfAssetResolver,
) {
    private val inFlight = mutableMapOf<String, Shared>()

    /** Starts loading [path] in [scope], or joins the load already under way, and waits for it. */
    fun join(path: String, scope: CoroutineScope): SceneMeshLoad {
        val shared = inFlight.getOrPut(path) { Shared(path, scope) }
        shared.waiters++
        return Waiter(shared)
    }

    /** One file's load, and how many [Waiter]s still wait for it. */
    @OptIn(ExperimentalAtomicApi::class)
    private inner class Shared(val path: String, scope: CoroutineScope) {
        // Made on the frame thread, where the resolver's asset source and image decoder are read.
        private val staging = newStaging()

        // Set off the frame thread before the job completes, so a completed job's outcome is final.
        private val staged = AtomicReference<Result<GltfAssetResolver>?>(null)
        private val job: Job = scope.launch { staged.store(read()) }
        var waiters = 0
        private var finished = false

        /** Why it failed, once [poll] says it finished. */
        var failure: Throwable? = null
            private set

        /** On the frame thread: whether it finished; the first poll that sees it loaded adopts the model. */
        fun poll(): Boolean {
            if (!finished && job.isCompleted) finish()
            return finished
        }

        private fun finish() {
            finished = true
            if (inFlight[path] === this) inFlight.remove(path)
            val result = staged.load() ?: Result.failure(CancellationException("The load of $path was cancelled."))
            result.fold(onSuccess = { model -> resolver.adopt(path, model) }, onFailure = { failure = it })
        }

        fun leave() {
            waiters--
            if (waiters > 0 || job.isCompleted) return
            job.cancel()
            if (inFlight[path] === this) inFlight.remove(path)
        }

        // TooGenericExceptionCaught: an asset source or the parser reports a bad model in its own exception type.
        @Suppress("TooGenericExceptionCaught")
        private suspend fun read(): Result<GltfAssetResolver> = try {
            staging.preload(path)
            Result.success(staging)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    private class Waiter(private val shared: Shared) : SceneMeshLoad {
        private var closed = false

        override val failure: Throwable? get() = shared.failure

        override fun poll(): Boolean = shared.poll()

        override fun close() {
            if (closed) return
            closed = true
            shared.leave()
        }
    }
}
