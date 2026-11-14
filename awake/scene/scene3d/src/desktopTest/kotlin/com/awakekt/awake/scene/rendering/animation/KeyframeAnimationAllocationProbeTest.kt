/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [KeyframeAnimationSystem] moves every keyframed node in place, so a frame allocates nothing on a
 * host that draws or one that doesn't (#1428).
 *
 * Negative control: a frame that allocated anything would allocate it in every window, so the
 * quietest window would still read above zero.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class KeyframeAnimationAllocationProbeTest {
    @Test
    fun aHeadlessFrameAllocatesNothing() = assertNoAllocation(KeyframeAnimationSystem(hasRenderer = false))

    @Test
    fun aDrawnFrameAllocatesNothing() = assertNoAllocation(KeyframeAnimationSystem())

    private fun assertNoAllocation(system: KeyframeAnimationSystem) {
        val world = World()
        val track = List(KEYS) { SceneVec3Key(it * DURATION / (KEYS - 1), SceneVec3(it.toFloat(), 0f, 0f)) }
        val tracks = SceneKeyframeAnimation(duration = DURATION, position = track, rotation = track, scale = track)
        repeat(NODES) {
            val entity = world.create()
            world.add(entity, Transform())
            world.add(entity, KeyframeAnimation(tracks))
        }
        repeat(WARMUP_FRAMES) { system.update(world, STEP) }

        // The quietest of several windows: a real per-frame allocation shows in every window, while
        // the JVM's own one-off work (2,280 bytes once in one window of eight, at random, measured on
        // main for #1560) lands in one and made a single window read 3 bytes a frame.
        val quietest = (1..WINDOWS).minOf {
            val start = allocated()
            repeat(MEASURED_FRAMES) { system.update(world, STEP) }
            allocated() - start
        }
        assertEquals(0L, quietest / MEASURED_FRAMES, "bytes per frame")
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val KEYS = 16
        const val NODES = 64
        const val DURATION = 2f
        const val STEP = 1f / 60f
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600
        const val WINDOWS = 4
    }
}
