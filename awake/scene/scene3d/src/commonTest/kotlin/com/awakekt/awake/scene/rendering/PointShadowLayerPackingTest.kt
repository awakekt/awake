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
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A casting point light's six shadow faces follow the layers the directional light draws (#1709):
 * three cascades and a lamp hold nine layers, not the ten a block reserved for four cascades took,
 * two cascades hold eight, and a scene with no casting sun starts its lamps at layer 0.
 */
class PointShadowLayerPackingTest {
    private val camera = Camera(Lens(eye = Vec3f(0f, 2f, 10f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f))

    private fun world(sun: Boolean, lamps: Int): World = World().also { world ->
        if (sun) world.add(world.create(), Light(type = Light.Type.Directional, shadowDistance = 60f))
        repeat(lamps) { index ->
            val lamp = world.create()
            world.add(lamp, Transform(position = Vec3f(index * 2f, 1f, 0f)))
            world.add(lamp, Light(type = Light.Type.Point, range = 5f, shadowsEnabled = true))
        }
    }

    private fun baseLayers(world: World, cascades: Int): List<Int> =
        SceneLightingCompiler(ClipSpace.Vulkan) { cascades }.sceneLight(world, camera, viewportAspect = 1f).pointShadows.map { it.baseLayer }

    @Test
    fun aLampsFacesFollowTheCascadesDrawn() {
        assertEquals(listOf(3, 9), baseLayers(world(sun = true, lamps = 2), cascades = 3), "after three cascades")
        assertEquals(listOf(2), baseLayers(world(sun = true, lamps = 1), cascades = 2), "after a reduced budget's two")
        assertEquals(listOf(0, 6), baseLayers(world(sun = false, lamps = 2), cascades = 3), "no sun, no cascade layers")
    }

    @Test
    fun aSunThatCastsNoShadowLeavesNoLayersBeforeTheLamps() {
        val world = world(sun = false, lamps = 1)
        world.add(world.create(), Light(type = Light.Type.Directional, shadowsEnabled = false))
        assertEquals(listOf(0), baseLayers(world, cascades = 3))
    }
}
