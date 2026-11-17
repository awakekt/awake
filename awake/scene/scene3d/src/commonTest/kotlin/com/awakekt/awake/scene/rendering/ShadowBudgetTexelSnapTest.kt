/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A frame planned under a shadow budget snaps each cascade to whole texels of the budget's own map
 * (#1709): a static point moves across a 1024 map by whole texels as the camera moves, not by the
 * half texels a 2048 grid would give it, which shimmer.
 */
class ShadowBudgetTexelSnapTest {
    @Test
    fun aReducedBudgetSnapsAtWholeTexelsOfItsMap() {
        val world = World()
        world.add(world.create(), Light(type = Light.Type.Directional, shadowDistance = 60f))
        val compiler = SceneLightingCompiler(ClipSpace.Vulkan) { ShadowBudget.Reduced }
        val point = Vec4(2f, 1f, -3f, 1f)
        val texel = 2f / ShadowBudget.Reduced.mapSize
        var worst = 0f
        val first = cascades(compiler, world, 0f)
        for (step in 1..STEPS) {
            val moved = cascades(compiler, world, step * STEP_METRES)
            first.zip(moved).forEach { (a, b) ->
                val clipA = point * a
                val clipB = point * b
                for (delta in floatArrayOf((clipA.x - clipB.x) / texel, (clipA.y - clipB.y) / texel)) {
                    worst = maxOf(worst, abs(delta - round(delta)))
                }
            }
        }
        assertTrue(worst < TOLERANCE, "a static point moved by a fraction of a ${ShadowBudget.Reduced.mapSize} texel: $worst")
    }

    private fun cascades(compiler: SceneLightingCompiler, world: World, shift: Float) = requireNotNull(
        compiler.sceneLight(world, Camera(lens(shift)), viewportAspect = ASPECT).cascades,
    ).viewProjections

    private fun lens(shift: Float) = Lens(
        eye = Vec3f(shift, 5f + shift * 0.3f, 20f + shift),
        center = Vec3f(shift, shift * 0.3f, shift),
        fovYRadians = 1f,
        near = 0.5f,
        far = 200f,
    )

    private companion object {
        const val ASPECT = 16f / 9f
        const val STEPS = 24
        const val STEP_METRES = 0.0371f
        const val TOLERANCE = 0.05f
    }
}
