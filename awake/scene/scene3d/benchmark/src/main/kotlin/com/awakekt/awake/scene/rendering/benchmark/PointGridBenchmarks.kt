/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.benchmark

import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.spatial.PointGrid2D
import com.awakekt.awake.core.spatial.SpatialGrid
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Warmup

/**
 * One gameplay tick of neighbourhood questions, through the point grid and through [SpatialGrid].
 *
 * The tick is the shape `PointGrid2D` is for: every unit's ground position indexed afresh, then
 * one "who is within [RADIUS] of me" per unit. [pointGridTick] rebuilds a [PointGrid2D] and asks
 * with `forEachNear`; [spatialGridTick] asks the same of a [SpatialGrid] holding a zero-size box
 * per unit, re-inserting every unit as a moving world would. The units shift every tick so the
 * box grid pays for real moves rather than its same-column fast path.
 *
 * [pointGridQueriesOnly] and [spatialGridQueriesOnly] leave the index alone and time the queries,
 * so the two halves of the trade can be read apart.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class PointGridBenchmarks {

    private val xs = FloatArray(UNITS) { ((it * PRIME_X) % SPREAD).toFloat() - SPREAD / 2 }
    private val zs = FloatArray(UNITS) { ((it * PRIME_Z) % SPREAD).toFloat() - SPREAD / 2 }
    private val boxes = Array(UNITS) { Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f)) }
    private val points = PointGrid2D(cellSize = RADIUS)
    private val spatial = SpatialGrid(cellSize = RADIUS)
    private val hits = ArrayList<Int>(UNITS)
    private val probe = Vec3f(0f, 0f, 0f)
    private var tick = 0

    /** Fills both indexes once, so the query-only benchmarks start from a built index. */
    @Setup
    fun setup() {
        rebuildPoints()
        rebuildSpatial()
    }

    /** Rebuilds the point grid and runs one query per unit. */
    @Benchmark
    fun pointGridTick(): Int {
        shift()
        rebuildPoints()
        return pointQueries()
    }

    /** Re-inserts every unit into the box grid and runs one query per unit. */
    @Benchmark
    fun spatialGridTick(): Int {
        shift()
        rebuildSpatial()
        return spatialQueries()
    }

    /** One query per unit against the built point grid. */
    @Benchmark
    fun pointGridQueriesOnly(): Int = pointQueries()

    /** One query per unit against the built box grid. */
    @Benchmark
    fun spatialGridQueriesOnly(): Int = spatialQueries()

    private fun shift() {
        tick += 1
        val step = if (tick % 2 == 0) STEP else -STEP
        for (unit in xs.indices) xs[unit] += step
    }

    private fun rebuildPoints() {
        points.clear()
        for (unit in xs.indices) points.add(unit, xs[unit], zs[unit])
    }

    private fun rebuildSpatial() {
        for (unit in xs.indices) {
            val box = boxes[unit]
            box.min.set(xs[unit], 0f, zs[unit])
            box.max.set(xs[unit], 0f, zs[unit])
            spatial.insert(unit, box)
        }
    }

    private fun pointQueries(): Int {
        var found = 0
        for (unit in xs.indices) points.forEachNear(xs[unit], zs[unit], RADIUS) { found++ }
        return found
    }

    private fun spatialQueries(): Int {
        var found = 0
        for (unit in xs.indices) {
            hits.clear()
            probe.set(xs[unit], 0f, zs[unit])
            found += spatial.queryRange(probe, RADIUS, hits).size
        }
        return found
    }

    private companion object {
        const val UNITS = 2_000
        const val SPREAD = 400
        const val PRIME_X = 7_919
        const val PRIME_Z = 104_729
        const val RADIUS = 8f
        const val STEP = 3f
    }
}
