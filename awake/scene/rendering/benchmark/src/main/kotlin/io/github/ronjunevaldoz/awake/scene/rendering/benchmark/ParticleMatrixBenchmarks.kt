// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.scene.rendering.benchmark

import io.github.ronjunevaldoz.awake.core.math.Mat4
import io.github.ronjunevaldoz.awake.core.math.Vec3f
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
 * `RenderSystem`'s particle path rebuilds one model matrix per visible particle per frame.
 * It used to do that with `Mat4().translate(...).scale(...)`, which allocates five matrices per
 * call; it now writes a pooled matrix in place via `setTranslationScale`.
 *
 * These measure that swap at a realistic per-frame particle count, and the sort-key change from
 * `(position - eye).length3()` (a `Vec3` allocation plus a sqrt per comparison) to
 * `squaredDistanceTo`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class ParticleMatrixBenchmarks {

    private lateinit var positions: List<Vec3f>
    private lateinit var pool: List<Mat4>
    private val eye = Vec3f(0f, 4f, 12f)

    @Setup
    fun setup() {
        // A single emitter's default ceiling; a scene runs several of these per frame.
        positions = List(PARTICLE_COUNT) { i ->
            val f = i.toFloat()
            Vec3f(f % 17f - 8f, f % 11f, -(f % 23f))
        }
        pool = List(PARTICLE_COUNT) { Mat4() }
    }

    /** The old shape: a fresh matrix per particle, five allocations deep. */
    @Benchmark
    fun allocatingModelMatrices(): Int {
        var touched = 0
        for (p in positions) {
            val m = Mat4().translate(p.x, p.y, p.z)
                .scale(PARTICLE_SCALE, PARTICLE_SCALE, PARTICLE_SCALE)
            if (m.m03 != Float.MAX_VALUE) touched += 1
        }
        return touched
    }

    /** The current shape: pooled matrices written in place. */
    @Benchmark
    fun pooledModelMatrices(): Int {
        var touched = 0
        for (i in positions.indices) {
            val m = pool[i].setTranslationScale(
                positions[i].x,
                positions[i].y,
                positions[i].z,
                PARTICLE_SCALE
            )
            if (m.m03 != Float.MAX_VALUE) touched += 1
        }
        return touched
    }

    /** The old depth-sort key: allocates a `Vec3` and takes a sqrt per comparison. */
    @Benchmark
    fun allocatingSortKey(): Float {
        var total = 0f
        for (p in positions) total += (p - eye).length3()
        return total
    }

    /** The current depth-sort key: same ordering, no allocation, no sqrt. */
    @Benchmark
    fun squaredSortKey(): Float {
        var total = 0f
        for (p in positions) total += p.squaredDistanceTo(eye)
        return total
    }

    private companion object {
        const val PARTICLE_COUNT = 200
        const val PARTICLE_SCALE = 0.25f
    }
}
