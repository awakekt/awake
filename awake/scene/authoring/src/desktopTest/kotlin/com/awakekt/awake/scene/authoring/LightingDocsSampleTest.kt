/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.authoring.dsl.directionalLight
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Lights and shadows" guide shows one sun as a scene document and in the scene DSL, side by
 * side. Both are included from here, so this test keeps them compiling, loading, and equal.
 */
class LightingDocsSampleTest {

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSameSun() {
        val world = World()
        // --8<-- [start:sun-dsl]
        world.scene {
            entity("sun") {
                directionalLight(
                    direction = Vec3f(0.55f, 1.0f, 0.35f),
                    color = Vec3f(1.0f, 0.97f, 0.9f),
                    intensity = 1.0f,
                    shadowDistance = 60f,
                )
            }
        }
        // --8<-- [end:sun-dsl]
        val fromDsl = world.lights().single()

        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "scene/sun.scene.json").readText())
        val fromDocument = SceneLoader.instantiate(document).world.lights().single()

        assertEquals(fromDocument, fromDsl)
        assertEquals(Light.Type.Directional, fromDsl.type)
        assertEquals(60f, fromDsl.shadowDistance)
    }

    private fun World.lights(): List<Light> = query(Light::class).mapNotNull { get<Light>(it) }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
