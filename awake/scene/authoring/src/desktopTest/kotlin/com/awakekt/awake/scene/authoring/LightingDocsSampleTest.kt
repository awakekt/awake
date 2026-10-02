/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.authoring

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.authoring.dsl.directionalLight
import com.awakekt.awake.scene.authoring.dsl.pointLight
import com.awakekt.awake.scene.authoring.dsl.scene
import com.awakekt.awake.scene.authoring.dsl.transform
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.tonemapping.ToneMapping
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Lights and shadows" guide shows each light as a scene document and in the scene DSL, side
 * by side. Both are included from here, so this test keeps them compiling, loading, and equal.
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
        val fromDocument = load("sun.scene.json").lights().single()

        assertEquals(fromDocument, fromDsl)
        assertEquals(Light.Type.Directional, fromDsl.type)
        assertEquals(60f, fromDsl.shadowDistance)
    }

    @Test
    fun theSceneDocumentAndTheSceneDslDescribeTheSamePointLight() {
        val world = World()
        // --8<-- [start:lamp-dsl]
        world.scene {
            entity("lamp") {
                transform(x = 0f, y = 2.5f, z = 1f)
                pointLight(
                    color = Vec3f(1.0f, 0.85f, 0.6f),
                    intensity = 2f,
                    range = 8f,
                )
            }
        }
        // --8<-- [end:lamp-dsl]
        val document = load("lamp.scene.json")

        val fromDsl = world.lights().single()
        assertEquals(document.lights().single(), fromDsl)
        assertEquals(Light.Type.Point, fromDsl.type)
        assertEquals(true, fromDsl.shadowsEnabled, "a point light casts shadows by default")
        assertEquals(world.lightPosition(), document.lightPosition())
        assertEquals(Vec3f(0f, 2.5f, 1f), world.lightPosition())
    }

    @Test
    fun theSceneDocumentAndTheSceneDslSetTheSameAmbient() {
        val world = World()
        // --8<-- [start:ambient-dsl]
        world.scene {
            entity("sun") {
                // directionalLight() has no ambient parameter, so attach the Light itself.
                with(Light(type = Light.Type.Directional, ambient = 0.35f))
            }
        }
        // --8<-- [end:ambient-dsl]
        val fromDsl = world.lights().single()

        assertEquals(load("ambient.scene.json").lights().single(), fromDsl)
        assertEquals(0.35f, fromDsl.ambient)
    }

    @Test
    fun theSceneDocumentAndTheSceneDslSetTheSameExposure() {
        val world = World()
        // --8<-- [start:exposure-dsl]
        world.scene {
            entity("environment") {
                with(ToneMapping(exposure = 1.5f))
            }
        }
        // --8<-- [end:exposure-dsl]
        val fromDsl = world.toneMappings().single()

        assertEquals(load("exposure.scene.json").toneMappings().single(), fromDsl)
        assertEquals(1.5f, fromDsl.exposure)
    }

    private fun load(snippet: String): World {
        DefaultSceneComponentResolvers.install()
        val document = SceneLoader.decode(File(DOCS_SNIPPETS, "rendering/$snippet").readText())
        return SceneLoader.instantiate(document).world
    }

    private fun World.lights(): List<Light> = query(Light::class).mapNotNull { get<Light>(it) }

    private fun World.toneMappings(): List<ToneMapping> = query(ToneMapping::class).mapNotNull { get<ToneMapping>(it) }

    private fun World.lightPosition(): Vec3f {
        val position = get<Transform>(query(Light::class).single())!!.position
        return Vec3f(position.x, position.y, position.z)
    }

    private companion object {
        /** Tests run from the module directory; the snippets live with the docs. */
        val DOCS_SNIPPETS = File("../../../website/docs/snippets")
    }
}
