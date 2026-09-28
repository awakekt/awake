/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.document.SceneValidator
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A directional light's shadow distance is authored in the scene document like its direction. */
class SceneLightShadowDistanceTest {

    @Test
    fun anAuthoredShadowDistanceSurvivesSaveAndLoad() {
        val world = World()
        val sun = world.create()
        world.add(sun, Transform())
        world.add(sun, Light(type = Light.Type.Directional, shadowDistance = 2000f))

        val saved = SceneLoader.encode(SceneLoader.fromWorld(world, name = "region"))
        val loaded = SceneLoader.instantiate(SceneLoader.decode(saved)).world

        assertEquals(2000f, loaded.sun().shadowDistance)
    }

    @Test
    fun aSceneWithoutTheFieldKeepsTheOldReach() {
        val document = SceneLoader.decode(
            """
            {
              "version": 1,
              "nodes": [
                { "name": "sun", "components": [ { "component": "light", "type": "Directional" } ] }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(100f, SceneLoader.instantiate(document).world.sun().shadowDistance)
    }

    @Test
    fun onlyAFinitePositiveDistanceValidates() {
        fun issues(distance: Float) = SceneValidator.validate(
            SceneDocument(
                nodes = listOf(
                    SceneNode(
                        name = "sun",
                        components = listOf(SceneLight(type = SceneLight.Type.Directional, shadowDistance = distance)),
                    ),
                ),
            ),
        )

        assertTrue(issues(2000f).isEmpty())
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { distance ->
            assertEquals(1, issues(distance).size, "shadowDistance $distance must be rejected.")
        }
    }

    private fun World.sun(): Light = query(Light::class).firstNotNullOf { get<Light>(it) }

    private companion object {
        init {
            DefaultSceneComponentResolvers.install()
        }
    }
}
