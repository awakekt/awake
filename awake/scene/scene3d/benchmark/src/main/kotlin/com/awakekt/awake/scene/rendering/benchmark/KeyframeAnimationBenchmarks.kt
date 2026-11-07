/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.benchmark

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.KeyframeAnimationSystem
import com.awakekt.awake.scene.rendering.animation.SceneKeyframeAnimation
import com.awakekt.awake.scene.rendering.animation.SceneVec3Key
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Warmup

/**
 * One headless frame of [KeyframeAnimationSystem] over [entityCount] keyframed nodes (#1428), each
 * with a position, rotation and scale track of [keys] keys, the nodes spread over the loop. A host
 * with no renderer still runs it, since colliders, sockets and the network read the moved nodes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
open class KeyframeAnimationBenchmarks {
    @Param("1244")
    var entityCount: Int = 0

    @Param("4", "32")
    var keys: Int = 0

    private val world = World()
    private val system = KeyframeAnimationSystem(hasRenderer = false)
    private var frames = 0

    /** Spawns the nodes, each started at its own point in the loop. */
    @Setup
    fun setup() {
        val track = List(keys) { index -> SceneVec3Key(index * DURATION / (keys - 1), SceneVec3(index.toFloat(), 0f, -index.toFloat())) }
        val tracks = SceneKeyframeAnimation(duration = DURATION, position = track, rotation = track, scale = track)
        repeat(entityCount) { index ->
            val entity = world.create()
            world.add(entity, Transform())
            world.add(entity, KeyframeAnimation(tracks))
            // Spread the nodes over the loop, so each samples a different key, as a real scene's do.
            system.update(world, DURATION * index / entityCount)
        }
    }

    /** One frame at 30 Hz. */
    @Benchmark
    fun headlessFrame(): Int {
        system.update(world, DELTA)
        return ++frames
    }

    private companion object {
        const val DURATION = 4f
        const val DELTA = 1f / 30f
    }
}
