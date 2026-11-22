/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowLayersNeeded
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that a directional light with [Light.shadowsEnabled] true but zero shadow reach
 * (`shadowDistance <= 0` or `<= camera.lens.near`) schedules no shadow passes and reports shadows
 * inactive in environment uniforms (#1709).
 */
class ZeroReachShadowTest {

    @Test
    fun zeroShadowDistanceDisablesShadowPassesAndEnvironmentFlag() {
        val world = World()
        val camera = Camera(lens = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.5f, far = 200f), isPrimary = true)
        world.add(world.create(), camera)
        world.add(world.create(), Light(type = Light.Type.Directional, shadowsEnabled = true, shadowDistance = 0f))

        val compiler = SceneLightingCompiler(ClipSpace.Vulkan)
        val light = compiler.sceneLight(world, camera, 16f / 9f)
        val env = compiler.environmentUniforms(world)

        assertNull(light.cascades, "zero shadowDistance must plan no shadow cascades")
        assertNull(light.viewProjection, "zero shadowDistance must produce no directional shadow matrix")
        assertFalse(env.shadowsEnabled, "zero shadowDistance must mark environment shadows inactive")

        val passInput = ScenePassCompiler.compile(
            lens = camera.lens,
            drawCalls = emptyList(),
            light = light,
            environment = env,
            clipSpace = ClipSpace.Vulkan,
            aspect = 16f / 9f,
        )
        assertTrue(passInput.prePasses.isEmpty(), "zero shadowDistance must schedule zero shadow pre-passes")
        assertEquals(0, shadowLayersNeeded(passInput.prePasses), "dormant 1-texel shadow array needs 0 layers")
    }

    @Test
    fun shadowDistanceBelowNearPlanePlansNoCascadesOrPasses() {
        val world = World()
        val camera = Camera(lens = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f.ZERO, fovYRadians = 1f, near = 1.0f, far = 200f), isPrimary = true)
        world.add(world.create(), camera)
        world.add(world.create(), Light(type = Light.Type.Directional, shadowsEnabled = true, shadowDistance = 0.5f))

        val compiler = SceneLightingCompiler(ClipSpace.Vulkan)
        val light = compiler.sceneLight(world, camera, 16f / 9f)
        val env = compiler.environmentUniforms(world)

        assertNull(light.cascades, "shadowDistance <= camera.near must plan no cascades")
        assertNull(light.viewProjection)

        val passInput = ScenePassCompiler.compile(
            lens = camera.lens,
            drawCalls = emptyList(),
            light = light,
            environment = env,
            clipSpace = ClipSpace.Vulkan,
            aspect = 16f / 9f,
        )
        assertTrue(passInput.prePasses.isEmpty(), "reach <= camera.near must schedule zero shadow pre-passes")
        assertEquals(0, shadowLayersNeeded(passInput.prePasses))
    }
}
