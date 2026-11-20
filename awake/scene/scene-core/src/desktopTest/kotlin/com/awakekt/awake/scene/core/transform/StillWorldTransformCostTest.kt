/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core.transform

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A town of [StaticTransform] props with a few things moving: every frame visited each static to
 * check it was built, and built the moving entities' family anew, 688 B a frame. The statics are now
 * visited only when one changed, so a frame costs the same at 1,000 props as at 10,000 and
 * allocates nothing. Run with escape analysis off, so allocation the JIT would hide still counts.
 */
class StillWorldTransformCostTest {

    private class Town(props: Int) {
        val world = World()
        val system = TransformSystem()
        val movers = Array(MOVERS) { Transform() }

        init {
            repeat(props) { i ->
                val prop = world.create()
                world.add(prop, Transform(position = Vec3f(i.toFloat(), 0f, 0f)))
                world.add(prop, StaticTransform)
            }
            for (mover in movers) world.add(world.create(), mover)
        }

        fun frame(tick: Int) {
            for (mover in movers) mover.position.x = tick.toFloat()
            system.update(world, 0f)
        }
    }

    @Test
    fun aStillTownCostsTheSameAtAnySizeAndAllocatesNothing() {
        val costs = listOf(SMALL, LARGE).map { props ->
            val town = Town(props)
            repeat(WARMUP) { town.frame(it) }
            val walks = town.system.staticWalks
            val start = System.nanoTime()
            repeat(FRAMES) { town.frame(it) }
            val nanos = (System.nanoTime() - start) / FRAMES
            val allocation = readAllocation(FRAMES) { repeat(FRAMES) { town.frame(it) } }
            println("PERF still town of $props props, $MOVERS moving: $nanos ns and $allocation")
            assertEquals(walks, town.system.staticWalks, "a still frame visited the statics")
            assertAllocatesNothing(allocation, "a still frame")
            nanos
        }
        println("PERF still town: ${costs[1].toDouble() / costs[0]}x the time at ${LARGE / SMALL}x the props")
    }

    private companion object {
        const val SMALL = 1_000
        const val LARGE = 10_000
        const val MOVERS = 10
        const val WARMUP = 2_000
        const val FRAMES = 1_000
    }
}
