/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring.dsl

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.rendering.light.Light
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneDslExtensionsTest {

    @Test
    fun defaultOrbitCameraAndSunAndLampSpawnExpectedComponents() {
        val world = World()

        world.scene {
            val camera = defaultOrbitCamera()
            val sunLight = sun(
                direction = Vec3f(0f, 1f, 0f),
                color = Vec3f(0.9f, 0.9f, 0.9f),
                intensity = 1.5f,
            )
            val lampLight = lamp(
                position = Vec3f(2f, 3f, 1f),
                color = Vec3f(1f, 0.8f, 0.5f),
            )

            assertNotNull(camera)
            assertNotNull(sunLight)
            assertNotNull(lampLight)
        }

        val cameraEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "camera" }
        val cameraComp = world.get<CameraRig>(cameraEntity)
        assertNotNull(cameraComp)
        assertEquals(CameraMode.ThirdPerson, cameraComp.mode)

        val sunEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "sun" }
        val sunComp = world.get<Light>(sunEntity)
        assertNotNull(sunComp)
        assertEquals(Light.Type.Directional, sunComp.type)
        assertEquals(1.5f, sunComp.intensity)

        val lampEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "lamp" }
        val lampComp = world.get<Light>(lampEntity)
        assertNotNull(lampComp)
        assertEquals(Light.Type.Point, lampComp.type)
    }
}
