/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.SceneColor
import com.awakekt.awake.scene.rendering.fog.Fog
import com.awakekt.awake.scene.rendering.sky.Skybox
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [DayCycleSystem] writes the light in place and replaces a sky or fog colour or the ambient share
 * only when it has visibly moved, so a held day allocates nothing and a ten-minute day allocates on
 * few frames.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class DayCycleAllocationProbeTest {

    @Test
    fun aHeldDayAllocatesNothingPerFrame() {
        val scene = scene(dayLengthSeconds = 0f)
        repeat(WARMUP_FRAMES) { scene.frame() }

        val start = allocated()
        repeat(MEASURED_FRAMES) { scene.frame() }

        assertEquals(0L, (allocated() - start) / MEASURED_FRAMES, "bytes per frame")
    }

    @Test
    fun aTenMinuteDayAllocatesOnFewFrames() {
        val scene = scene(dayLengthSeconds = 600f)
        repeat(WARMUP_FRAMES) { scene.frame() }

        var allocating = 0
        repeat(MEASURED_FRAMES) {
            val before = allocated()
            scene.frame()
            if (allocated() > before) allocating++
        }

        assertTrue(allocating * 4 < MEASURED_FRAMES, "$allocating of $MEASURED_FRAMES frames allocated")
    }

    private class Scene(val world: World, val system: DayCycleSystem) {
        fun frame() = system.update(world, 1f / 60f)
    }

    private fun scene(dayLengthSeconds: Float): Scene {
        val world = World()
        val sun = world.create()
        world.add(sun, Light(type = Light.Type.Directional))
        world.add(sun, DayCycle(SceneDayCycle(dayLengthSeconds = dayLengthSeconds, time = 0.3f, stops = STOPS)))
        val environment = world.create()
        world.add(environment, Skybox())
        world.add(environment, Fog())
        return Scene(world, DayCycleSystem())
    }

    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean
    private fun allocated(): Long = threadBean.currentThreadAllocatedBytes

    private companion object {
        const val WARMUP_FRAMES = 300
        const val MEASURED_FRAMES = 600

        /** Every field set at sunrise and noon, so each one is blending at 0.3. */
        val STOPS = listOf(
            SceneDayStop(
                0.25f,
                horizonColor = SceneColor(0.95f, 0.65f, 0.35f),
                zenithColor = SceneColor(0.23f, 0.31f, 0.48f),
                lightColor = SceneColor(1f, 0.69f, 0.44f),
                lightIntensity = 0.6f,
                ambient = 0.2f,
                fogColor = SceneColor(0.8f, 0.6f, 0.5f),
            ),
            SceneDayStop(
                0.5f,
                horizonColor = SceneColor(0.72f, 0.8f, 0.88f),
                zenithColor = SceneColor(0.2f, 0.38f, 0.68f),
                lightColor = SceneColor(1f, 0.96f, 0.88f),
                lightIntensity = 1f,
                ambient = 0.3f,
                fogColor = SceneColor(0.55f, 0.62f, 0.7f),
            ),
        )
    }
}
