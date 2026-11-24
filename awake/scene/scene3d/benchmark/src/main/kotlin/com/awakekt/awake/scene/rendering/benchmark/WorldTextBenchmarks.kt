/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.benchmark

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.rendering.RenderFeatureContext3D
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.worldtext.WorldTextRenderFeature
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextBatch
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Warmup

/**
 * Per-frame cost of [WorldTextRenderFeature] and [WorldTextBatch]:
 * - [emptyWorldCollect]: collecting with zero `WorldText` entities in the world (exercises the
 *   early return before `Frustum.planes`).
 * - [steadyHundredLabels]: collecting 100 unchanged labels (zero-allocation steady state).
 * - [churnDeferredRetire] vs [churnImmediateWaitIdle]: 10 labels updating per frame with deferred
 *   3-frame retirement (0 mid-frame `waitIdle`) vs immediate `retireFrames = 0` (`waitIdle` per edit).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class WorldTextBenchmarks {
    private class BenchRenderer : NoopRenderer() {
        var waitIdles = 0L

        override fun waitIdle() {
            waitIdles++
        }

        override fun createMesh(geometry: MeshGeometry): Mesh = object : Mesh {
            override val format = geometry.format
            override val sizeBytes: Long = 0L
            override fun destroy() = Unit
        }
    }

    private val emptyWorld = World()
    private val emptyFeature = WorldTextRenderFeature(BenchRenderer())
    private val lens = Lens(eye = Vec3f(0f, 2f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
    private val featureContext = RenderFeatureContext3D(camera = Camera(lens), viewportAspect = ASPECT, elapsedTimeSeconds = 0f)

    private val steadyBatch = WorldTextBatch(BenchRenderer())
    private val steadyTexts = List(STEADY_COUNT) { WorldText("Sign $it", billboard = it % 2 == 0) }
    private val steadyPlacements = List(STEADY_COUNT) { Mat4().translate(it.toFloat(), 0f, 0f) }

    private val deferredRenderer = BenchRenderer()
    private val deferredBatch = WorldTextBatch(deferredRenderer)
    private val immediateRenderer = BenchRenderer()
    private val immediateBatch = WorldTextBatch(immediateRenderer, retireFrames = 0)
    private val deferredTexts = List(CHURN_COUNT) { WorldText("HP 100") }
    private val immediateTexts = List(CHURN_COUNT) { WorldText("HP 100") }
    private val churnPlacements = List(CHURN_COUNT) { Mat4().translate(it.toFloat(), 1f, 0f) }
    private val draws = ArrayList<RenderDrawCommand>(STEADY_COUNT)
    private var tick = 0

    /** Warms steady-state font atlases and initial mesh uploads. */
    @Setup
    fun setup() {
        draws.clear()
        for (i in steadyTexts.indices) steadyBatch.collect(steadyTexts[i], steadyPlacements[i], lens, draws)
        steadyBatch.releaseUnseen()

        draws.clear()
        for (i in deferredTexts.indices) deferredBatch.collect(deferredTexts[i], churnPlacements[i], lens, draws)
        deferredBatch.releaseUnseen()

        draws.clear()
        for (i in immediateTexts.indices) immediateBatch.collect(immediateTexts[i], churnPlacements[i], lens, draws)
        immediateBatch.releaseUnseen()
    }

    /** Releases benchmark batches cleanly. */
    @TearDown
    fun tearDown() {
        emptyFeature.close()
        steadyBatch.close()
        deferredBatch.close()
        immediateBatch.close()
    }

    /** Empty-world collect: skips `Frustum.planes` construction when no `WorldText` entities exist. */
    @Benchmark
    fun emptyWorldCollect(): Int = emptyFeature.collect(emptyWorld, featureContext).draws.size

    /** Steady-state frame over 100 unchanged labels (half billboarded). */
    @Benchmark
    fun steadyHundredLabels(): Int {
        draws.clear()
        for (i in steadyTexts.indices) steadyBatch.collect(steadyTexts[i], steadyPlacements[i], lens, draws)
        steadyBatch.releaseUnseen()
        return draws.size
    }

    /** Churning 10 labels/frame with deferred 3-frame GPU retirement (0 mid-frame `waitIdle`). */
    @Benchmark
    fun churnDeferredRetire(): Int {
        tick++
        draws.clear()
        for (i in deferredTexts.indices) {
            deferredTexts[i].text = if ((tick + i) and 1 == 0) "HP 99" else "HP 100"
            deferredBatch.collect(deferredTexts[i], churnPlacements[i], lens, draws)
        }
        deferredBatch.releaseUnseen()
        return draws.size + deferredRenderer.waitIdles.toInt()
    }

    /** Churning 10 labels/frame with immediate `retireFrames = 0` (calls `waitIdle` on every edit). */
    @Benchmark
    fun churnImmediateWaitIdle(): Int {
        tick++
        draws.clear()
        for (i in immediateTexts.indices) {
            immediateTexts[i].text = if ((tick + i) and 1 == 0) "HP 99" else "HP 100"
            immediateBatch.collect(immediateTexts[i], churnPlacements[i], lens, draws)
        }
        immediateBatch.releaseUnseen()
        return draws.size + immediateRenderer.waitIdles.toInt()
    }

    private companion object {
        const val ASPECT = 16f / 9f
        const val STEADY_COUNT = 100
        const val CHURN_COUNT = 10
    }
}
