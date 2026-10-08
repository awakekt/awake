/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.terrain

import com.awakekt.awake.asset.terrain.TerrainPageCoord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.floor

/** Bounded asynchronous reads. Call [update] and [close] only from the terrain owner thread. */
class TerrainPageStreamer(
    /** Streaming residency controller owned by the caller. */
    val terrain: PagedTerrain,
    private val scope: CoroutineScope,
    private val read: suspend (TerrainPageCoord) -> TerrainPage?,
    /** Requested cell radius around the observer, limited by capacity. */
    val radius: Int = 2,
    /** Maximum live asset readers, including cancelled readers still retiring. */
    val maxConcurrentReads: Int = 2,
    private val onFailure: (TerrainPageCoord, Throwable) -> Unit = { _, _ -> },
) {
    private data class Completion(val coord: TerrainPageCoord, val generation: Long, val result: Result<TerrainPage?>)
    private data class Loading(val generation: Long, val job: Job)
    private val retiring = mutableListOf<Job>()
    private val loading = mutableMapOf<TerrainPageCoord, Loading>()
    private val completed = Channel<Completion>(maxConcurrentReads)
    private val missing = mutableSetOf<TerrainPageCoord>()
    private val failed = mutableSetOf<TerrainPageCoord>()
    private var generation = 0L
    private var closed = false
    init {
        require(radius in 0..64 && maxConcurrentReads in 1..terrain.capacity)
    }

    /** Number of live or retiring asset readers. */
    val pendingReads: Int get() = loading.size + retiring.size

    /** Failed cells awaiting an explicit retry. */
    val failedCoords: Set<TerrainPageCoord> get() = failed.toSet()

    /** Cells reported absent, using fallback until retried. */
    val absentCoords: Set<TerrainPageCoord> get() = missing.toSet()

    /** Absolute observer position; distant clean pages are evicted before starting more reads. */
    fun update(x: Double, z: Double) {
        check(!closed)
        require(x.isFinite() && z.isFinite())
        retiring.removeAll { it.isCompleted }
        val demand = demand(x, z)
        val wanted = demand.toSet()
        retireUnwanted(wanted)
        applyCompleted(wanted)
        launchMissing(demand)
    }

    private fun demand(x: Double, z: Double): List<TerrainPageCoord> {
        val layout = terrain.layout
        val cx = floor(x / layout.cellSize).toLong()
        val cz = floor(z / layout.cellSize).toLong()
        return buildList {
            for (dz in -radius..radius) {
                for (dx in -radius..radius) {
                    val px = cx + dx
                    val pz = cz + dz
                    if (px !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() || pz !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) continue
                    val coord = TerrainPageCoord(px.toInt(), pz.toInt())
                    if (layout.contains(coord)) add(coord)
                }
            }
        }.sortedBy { (it.x.toDouble() - cx) * (it.x.toDouble() - cx) + (it.z.toDouble() - cz) * (it.z.toDouble() - cz) }.take(terrain.capacity)
    }

    private fun retireUnwanted(wanted: Set<TerrainPageCoord>) {
        for (coord in loading.keys.toList()) {
            if (coord !in wanted) {
                loading.remove(coord)?.job?.let {
                    it.cancel()
                    retiring += it
                }
            }
        }
        for (coord in terrain.residentCoords) if (coord !in wanted) terrain.evict(coord)
        missing.retainAll(wanted)
        failed.retainAll(wanted)
    }

    private fun applyCompleted(wanted: Set<TerrainPageCoord>) {
        repeat(maxConcurrentReads) {
            val item = completed.tryReceive().getOrNull() ?: return@repeat
            if (loading[item.coord]?.generation != item.generation) return@repeat
            loading.remove(item.coord)
            if (item.coord !in wanted) return@repeat
            item.result.fold(
                onSuccess = { page ->
                    if (page == null) {
                        missing += item.coord
                    } else {
                        try {
                            terrain.put(item.coord, page)
                        } catch (error: IllegalArgumentException) {
                            failed += item.coord
                            onFailure(item.coord, error)
                        }
                    }
                },
                onFailure = { error ->
                    failed += item.coord
                    onFailure(item.coord, error)
                },
            )
        }
    }

    @Suppress("TooGenericExceptionCaught") // Asset providers define their own I/O failures; report them at the worker boundary.
    private fun launchMissing(demand: List<TerrainPageCoord>) {
        val available = minOf(maxConcurrentReads - pendingReads, terrain.capacity - terrain.residentCoords.size - loading.size)
        val unavailable = terrain.residentCoords + loading.keys + missing + failed
        for (coord in demand.filter { it !in unavailable }.take(available.coerceAtLeast(0))) {
            val token = ++generation
            val job = scope.launch {
                val result = try {
                    Result.success(read(coord))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
                completed.send(Completion(coord, token, result))
            }
            loading[coord] = Loading(token, job)
        }
    }

    /** Explicit retry after a read/validation error, without a hot retry loop every frame. */
    fun retry(coord: TerrainPageCoord) {
        failed.remove(coord)
        missing.remove(coord)
    }

    /** Cancels readers and evicts clean cells; unsaved edits remain pinned. */
    fun close() {
        if (closed) return
        closed = true
        loading.values.forEach { it.job.cancel() }
        loading.clear()
        retiring.forEach { it.cancel() }
        retiring.clear()
        completed.cancel()
        for (coord in terrain.residentCoords) terrain.evict(coord)
    }
}
