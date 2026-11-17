/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.ecs.World
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one more attached shader effect adds to a frame of [ShaderEffectSystem] (#1742): the slope
 * between a world of [FEW] effects and one of [MANY], so the frame's fixed cost drops out.
 *
 * The system looked for new effects by handing `World.queryEach` a function it kept in a field, to
 * keep the query from allocating. `queryEach` is inline, so that did the opposite: a lambda at the
 * call site is inlined into its loop, while a function object is called through its generic
 * `invoke`, which boxed every `Entity`, every frame.
 */
class ShaderEffectFrameAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    private val sky = ShaderDocuments.compile(ShaderEffectFixtures.SKY)

    @Test
    fun anotherAttachedEffectAddsNothingToAFrame() {
        val few = perFrame(FEW)
        val many = perFrame(MANY)
        val perEffect = (many - few) / (MANY - FEW)
        println("ShaderEffectFrameAllocation: $FEW effects $few B a frame, $MANY effects $many B a frame, $perEffect B for each further effect")
        assertTrue(perEffect <= PER_EFFECT_BUDGET, "each further attached effect allocated $perEffect B a frame")
    }

    /** Bytes allocated by a frame of [ShaderEffectSystem.update] over [effects] effects, all attached and drawing. */
    private fun perFrame(effects: Int): Long {
        val world = World()
        val scope = CoroutineScope(SupervisorJob())
        val system = ShaderEffectSystem(RecordingHost(), ShaderEffectAssets(documents = mapOf("sky.shader.json" to sky)), scope)
        try {
            repeat(effects) { world.add(world.create(), ShaderEffectSource(SceneShaderEffect("sky.shader.json"))) }
            repeat(WARM_UP) { system.update(world, DELTA) }
            val before = threads.currentThreadAllocatedBytes
            repeat(FRAMES) { system.update(world, DELTA) }
            return (threads.currentThreadAllocatedBytes - before) / FRAMES
        } finally {
            system.close()
            scope.cancel()
        }
    }

    private companion object {
        const val FEW = 50
        const val MANY = 200
        const val WARM_UP = 5
        const val FRAMES = 50
        const val DELTA = 1f / 60f

        /** 16 B an effect while each `Entity` was boxed to reach the stored function; nothing since. */
        const val PER_EFFECT_BUDGET = 4L
    }
}
