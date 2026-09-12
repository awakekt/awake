/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.render.passes.BoundedPreparationQueue
import kotlinx.coroutines.awaitAll

/**
 * Shared source-to-prepared-draw loop.
 *
 * Backends provide only resource resolution for one source draw. Partitioning, pipeline grouping,
 * batch clustering and transparent ordering stay identical across drivers.
 */
fun <S, P : PreparedDraw> compileDrawCalls(
    drawCalls: List<S>,
    resolve: (drawCall: S, sourceIndex: Int) -> P?,
): SortedDraws<P> = sortForRecording(resolveDrawCalls(drawCalls, resolve))

/** Resolves the shared source list while preserving source order for backend-specific sorting. */
fun <S, P : PreparedDraw> resolveDrawCalls(
    drawCalls: List<S>,
    resolve: (drawCall: S, sourceIndex: Int) -> P?,
): List<P> {
    val prepared = ArrayList<P>(drawCalls.size)
    drawCalls.forEachIndexed { index, drawCall ->
        resolve(drawCall, index)?.let(prepared::add)
    }
    return prepared
}

/**
 * CPU-worker variant of [compileDrawCalls].
 *
 * The queue owns only immutable source indices and prepared CPU results. Resource resolution is
 * supplied by the caller, so this API cannot accidentally move command recording or native GPU
 * handles onto a worker. Results are gathered in source order before the shared sorter runs,
 * keeping transparent ordering and batch grouping identical to the serial path.
 */
suspend fun <S, P : PreparedDraw> compileDrawCallsAsync(
    drawCalls: List<S>,
    queue: BoundedPreparationQueue<Int, P?>,
): SortedDraws<P> {
    val results = drawCalls.indices
        .map { queue.submit(it) }
        .awaitAll()
        .filterNotNull()
    return sortForRecording(results)
}

/**
 * Async compiler variant that publishes its result through the command-packet lifetime guard.
 *
 * The returned lease is already sealed because all worker preparation has completed. The render
 * owner must still call [GpuCommandLease.submit] when the backend accepts the packet and
 * [GpuCommandLease.retire] after the corresponding GPU completion point.
 */
suspend fun <S, P : PreparedDraw> compileDrawCallsAsyncLease(
    drawCalls: List<S>,
    queue: BoundedPreparationQueue<Int, P?>,
): GpuCommandLease<SortedDraws<P>> = GpuCommandLease(
    compileDrawCallsAsync(drawCalls, queue),
).also { it.seal() }
