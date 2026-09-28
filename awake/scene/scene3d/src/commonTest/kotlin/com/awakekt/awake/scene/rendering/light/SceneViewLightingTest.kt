/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.light

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.rendering.Camera
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SceneViewLightingTest {

    /** Cascades follow the view asked about, not whichever camera the scene calls primary. */
    @Test
    fun cascadesAreFittedToTheViewsOwnCamera() {
        val world = World()
        world.add(world.create(), Light(type = Light.Type.Directional))
        val lighting = SceneViewLighting(ClipSpace.Vulkan)

        val near = assertNotNull(lighting.light(world, camera(eyeZ = 10f), 1f).cascades)
        val far = assertNotNull(lighting.light(world, camera(eyeZ = 60f), 1f).cascades)

        assertNotEquals(near.viewProjections.first(), far.viewProjections.first())
    }

    private fun camera(eyeZ: Float) = Camera(
        Lens(eye = Vec3f(0f, 5f, eyeZ), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f),
    )
}
