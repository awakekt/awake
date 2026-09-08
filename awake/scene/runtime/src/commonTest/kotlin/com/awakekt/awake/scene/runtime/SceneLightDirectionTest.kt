/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.Light
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An authored sun points where the scene says it does.
 *
 * `SceneLight` had no `direction` field at all, and `SceneJson` ignores unknown keys, so every
 * scene that authored one -- the cascaded-shadows showcase among them -- was silently lit from
 * the engine's default angle instead. Nothing failed: the light existed, the shadows fell, and
 * they fell the wrong way.
 */
class SceneLightDirectionTest {

    @Test
    fun anAuthoredDirectionReachesTheLightComponent() {
        val document = SceneLoader.decode(
            """
            {
              "version": 1,
              "name": "sun",
              "nodes": [
                {
                  "name": "sun",
                  "components": [
                    {
                      "component": "light",
                      "type": "Directional",
                      "direction": { "x": 0.55, "y": 1.0, "z": 0.35 }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val world = World()
        SceneLoader.instantiate(document, world)

        val light = world.query(Light::class).firstNotNullOf { world.get<Light>(it) }
        assertEquals(0.55f, light.direction.x, TOLERANCE, "The authored direction was dropped.")
        assertEquals(1f, light.direction.y, TOLERANCE)
        assertEquals(0.35f, light.direction.z, TOLERANCE)
    }

    @Test
    fun aSceneThatAuthorsNoDirectionKeepsTheEngineDefault() {
        val document = SceneLoader.decode(
            """
            {
              "version": 1,
              "name": "sun",
              "nodes": [
                { "name": "sun", "components": [ { "component": "light", "type": "Directional" } ] }
              ]
            }
            """.trimIndent(),
        )

        val world = World()
        SceneLoader.instantiate(document, world)

        val light = world.query(Light::class).firstNotNullOf { world.get<Light>(it) }
        assertEquals(Light().direction.x, light.direction.x, TOLERANCE)
        assertEquals(Light().direction.y, light.direction.y, TOLERANCE)
        assertEquals(Light().direction.z, light.direction.z, TOLERANCE)
    }

    private companion object {
        init {
            DefaultSceneComponentResolvers.install()
        }

        const val TOLERANCE = 0.0001f
    }
}
