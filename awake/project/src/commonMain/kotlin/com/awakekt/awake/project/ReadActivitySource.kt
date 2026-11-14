/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.time.Duration

/**
 * [files], counting the reads in flight, so an app can tell when they have gone quiet: a scene that
 * keeps streaming terrain and cells after its first frame has nothing new left to read. A loading
 * screen that lifts at the first frame waits in [awaitQuiet] instead, for a view that has filled in.
 *
 * It passes [PrefetchableAssetSource] and [GatedAssetSource] on to [files], as other wrapping sources
 * do, so wrapping a source keeps its progress and its gate. A prefetch counts as one read in flight.
 *
 * @param files The project's content source.
 */
class ReadActivitySource(private val files: AssetSource) :
    PrefetchableAssetSource,
    GatedAssetSource {
    private val activity = MutableStateFlow(Activity(inFlight = 0, started = 0L))

    /** How many reads are in flight now. */
    val readsInFlight: Int get() = activity.value.inFlight

    /** How many reads have started through this source, ever. */
    val readsStarted: Long get() = activity.value.started

    /**
     * Suspends until no read has been in flight for [quietFor]: none in flight now, and none started
     * during the last [quietFor]. Returns at once when [quietFor] is zero and nothing is in flight.
     * Cancelling the caller cancels the wait.
     */
    suspend fun awaitQuiet(quietFor: Duration) {
        require(!quietFor.isNegative()) { "quietFor must not be negative: $quietFor" }
        while (true) {
            val idle = activity.first { it.inFlight == 0 }
            if (quietFor > Duration.ZERO) delay(quietFor)
            if (activity.value.started == idle.started && activity.value.inFlight == 0) return
        }
    }

    override suspend fun read(path: AssetPath): Result<ByteArray> = tracked { files.read(path) }

    override suspend fun exists(path: AssetPath): Boolean = files.exists(path)

    override suspend fun isGated(path: AssetPath): Boolean = (files as? GatedAssetSource)?.isGated(path) == true

    override suspend fun awaitOpen(path: AssetPath) {
        (files as? GatedAssetSource)?.awaitOpen(path)
    }

    override val canPrefetch: Boolean get() = (files as? PrefetchableAssetSource)?.canPrefetch == true

    override suspend fun prefetchWithProgress(paths: List<String>, onProgress: (done: Long, total: Long) -> Unit) {
        val prefetching = files as? PrefetchableAssetSource ?: return
        tracked { prefetching.prefetchWithProgress(paths, onProgress) }
    }

    private suspend fun <T> tracked(block: suspend () -> T): T {
        activity.update { Activity(it.inFlight + 1, it.started + 1) }
        try {
            return block()
        } finally {
            activity.update { it.copy(inFlight = it.inFlight - 1) }
        }
    }

    private data class Activity(val inFlight: Int, val started: Long)
}
