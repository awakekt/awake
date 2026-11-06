/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/** Where one of an [AssetLoads]' assets is: still loading, loaded, or failed. */
sealed interface AssetLoad<out T> {
    /** Its load has started and not finished. */
    data object Loading : AssetLoad<Nothing>

    /** It is loaded: [value] is the decoded asset. */
    data class Loaded<T>(val value: T) : AssetLoad<T>

    /** It could not be loaded, for [cause]; it is not tried again until it is [AssetLoads.forget]ten. */
    data class Failed(val cause: Throwable) : AssetLoad<Nothing>
}

/**
 * Assets read and decoded by path, off the frame, each once however many ask for it: a sound's clip or
 * an image that a system and a prewarm both want share the one load.
 *
 * [request] starts a path's load the first time it is asked for, in the scope given or this one's own,
 * and answers [AssetLoad.Loading] until a later ask finds it finished. A load hands its result back
 * through a channel, so [request] and [loaded] only change on the thread that asks: the frame
 * thread. A load cancelled before it finished, by its scope ending or by [release], is forgotten, so the
 * next ask starts it again.
 *
 * An asset is kept once loaded. One that is [hold]ed, such as by a prewarm, is dropped again when its last
 * hold is [release]d, unless it was [put] in, which keeps it until [forget] or [close]. A hold released
 * while the asset still loads cancels the load.
 *
 * Made, asked and closed on the frame thread.
 *
 * @param context Where loads run when [request] names no scope; [Dispatchers.Default] by default. A
 *   [Job] in it is the parent of this one's own loads.
 * @param read Reads and decodes the asset at a path, throwing when it can't.
 */
class AssetLoads<T : Any>(
    context: CoroutineContext = Dispatchers.Default,
    read: suspend (path: String) -> T,
) : AutoCloseable {
    private val reader = read
    private val own = CoroutineScope(context + SupervisorJob(context[Job]))
    private val finished = Channel<Finished<T>>(Channel.UNLIMITED)
    private val states = HashMap<String, AssetLoad<T>>()
    private val values = HashMap<String, T>()
    private val jobs = HashMap<String, Job>()
    private val holds = HashMap<String, Int>()
    private val kept = HashSet<String>()
    private var closed = false

    /** The assets loaded so far, by path: a live view, for a system that looks them up each frame. */
    val loaded: Map<String, T>
        get() {
            receiveFinished()
            return values
        }

    /** Adds an asset already decoded, such as one read when the scene loaded; it is kept until [forget]. */
    fun put(path: String, value: T) {
        jobs.remove(path)?.cancel()
        states[path] = AssetLoad.Loaded(value)
        values[path] = value
        kept += path
    }

    /**
     * Where [path]'s asset is, starting its load in [scope] the first time it is asked for. After [close]
     * an unknown path answers [AssetLoad.Loading] and starts nothing.
     */
    fun request(path: String, scope: CoroutineScope = own): AssetLoad<T> {
        receiveFinished()
        if (path !in states && !closed) start(path, scope)
        return states[path] ?: AssetLoad.Loading
    }

    private fun start(path: String, scope: CoroutineScope) {
        states[path] = AssetLoad.Loading
        val job = scope.launch { finished.trySend(Finished(path, coroutineContext[Job], attempt(path))) }
        jobs[path] = job
        // A load cancelled before it finished, such as when its scope ends, is forgotten for the next ask.
        job.invokeOnCompletion { cause -> if (cause != null) finished.trySend(Finished(path, job, null)) }
        // A dispatcher that runs the load at once, as a test's may, has finished it already.
        receiveFinished()
    }

    /** Keeps [path]'s asset until a matching [release]; [request] it to start its load. */
    fun hold(path: String) {
        holds[path] = (holds[path] ?: 0) + 1
    }

    /**
     * Lets go of one [hold] on [path]. The last one drops the asset, or cancels its load, unless it was
     * [put] in. Releasing a path that isn't held is harmless.
     */
    fun release(path: String) {
        val count = holds[path] ?: return
        if (count > 1) {
            holds[path] = count - 1
            return
        }
        holds -= path
        if (path !in kept) forget(path)
    }

    /** How many [hold]s [path] has. */
    fun holdCount(path: String): Int = holds[path] ?: 0

    /** Drops [path]'s asset, or cancels its load, so the next [request] reads it again. */
    fun forget(path: String) {
        jobs.remove(path)?.cancel()
        states -= path
        values -= path
        kept -= path
    }

    /** Cancels every load and drops every asset. Closing twice is harmless. */
    override fun close() {
        closed = true
        own.cancel()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        states.clear()
        values.clear()
        holds.clear()
        kept.clear()
    }

    // TooGenericExceptionCaught: a reader reports an asset it can't decode in its own exception type.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun attempt(path: String): AssetLoad<T> = try {
        AssetLoad.Loaded(reader(path))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        AssetLoad.Failed(failure)
    }

    private fun receiveFinished() {
        while (true) {
            val done = finished.tryReceive().getOrNull() ?: return
            // A result from a load that was cancelled, forgotten or replaced since is dropped.
            if (jobs[done.path] !== done.job) continue
            jobs -= done.path
            when (val result = done.result) {
                null -> states -= done.path
                is AssetLoad.Loaded -> {
                    states[done.path] = result
                    values[done.path] = result.value
                }
                else -> states[done.path] = result
            }
        }
    }

    private class Finished<T>(val path: String, val job: Job?, val result: AssetLoad<T>?)
}
