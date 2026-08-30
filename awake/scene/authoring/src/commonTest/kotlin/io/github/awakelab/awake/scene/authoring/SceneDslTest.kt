/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.authoring

import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.authoring.dsl.camera
import io.github.awakelab.awake.scene.authoring.dsl.scene
import io.github.awakelab.awake.scene.authoring.dsl.transform
import io.github.awakelab.awake.scene.controls.components.CameraRig
import io.github.awakelab.awake.scene.core.components.Name
import io.github.awakelab.awake.scene.core.components.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SceneDslTest {

    @Test
    fun worldSceneBuildsEntitiesWithNamesAndModifiers() {
        val world = World()

        world.scene {
            entity("camera") {
                camera()
            }
            entity("cube") {
                transform(y = 2f)
                entity("child") {
                    transform(x = 1f)
                }
            }
        }

        val cameraEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "camera" }
        assertNotNull(world.get<CameraRig>(cameraEntity))

        val cubeEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "cube" }
        val cubeTransform = world.get<Transform>(cubeEntity)
        assertNotNull(cubeTransform)
        assertEquals(2f, cubeTransform.position.y)

        val childEntity = world.query(Name::class).first { world.get<Name>(it)?.value == "child" }
        val childTransform = world.get<Transform>(childEntity)
        assertNotNull(childTransform)
        assertEquals(1f, childTransform.position.x)
        assertEquals(cubeEntity, childTransform.parent)
    }
}
