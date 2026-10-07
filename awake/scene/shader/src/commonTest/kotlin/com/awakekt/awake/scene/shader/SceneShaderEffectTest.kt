/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SceneShaderEffectTest {
    init {
        SceneComponentRegistry.registerGlobal(ShaderEffectBinding)
    }

    @Test
    fun theFileShapeDecodesToTheSettingsItSays() {
        val effect = SceneLoader.decode(
            """
            {"version": 1, "nodes": [{"components": [
              {"component": "shader_effect", "shader": "shaders/pool.shader.json",
               "parameters": {"depth": [2.5]}, "textures": {"ripples": "textures/ripples.png"}, "enabled": false}
            ]}]}
            """,
        ).nodes.single().components.single()

        assertEquals(
            SceneShaderEffect(
                shader = "shaders/pool.shader.json",
                parameters = mapOf("depth" to listOf(2.5f)),
                textures = mapOf("ripples" to "textures/ripples.png"),
                enabled = false,
            ),
            effect,
        )
    }

    @Test
    fun anEffectRoundTripsThroughTheWorld() {
        val effect = SceneShaderEffect("sky.shader.json", parameters = mapOf("tint" to listOf(1f, 0f, 0f, 1f)))
        val world = World()
        val entity = world.create()
        val context = object : SceneResolutionContext {
            override val world = world

            override fun deferNodeLink(targetNodeName: String, onResolved: (target: Entity) -> Unit) = Unit

            override fun recordRequest(request: Any) = Unit
        }

        ShaderEffectBinding.attachTyped(world, entity, effect, context)

        val source = assertNotNull(world.get<ShaderEffectSource>(entity))
        assertEquals(effect, source.settings)
        assertEquals(effect, ShaderEffectBinding.export(world, entity, source))
    }

    @Test
    fun validationNamesWhatTheFileGotWrong() {
        val issues = SceneShaderEffect(
            shader = " ",
            parameters = mapOf("tint" to emptyList(), "glow" to listOf(Float.NaN)),
            textures = mapOf("ripples" to ""),
        ).validate("nodes[0].components[0]").map { it.message }

        assertTrue("shader_effect.shader names no shader document" in issues, issues.toString())
        assertTrue("shader_effect.parameters.tint has no value" in issues, issues.toString())
        assertTrue("shader_effect.parameters.glow has a value that is not a finite number" in issues, issues.toString())
        assertTrue("shader_effect.textures.ripples names no image" in issues, issues.toString())
        assertEquals(emptyList(), SceneShaderEffect("sky.shader.json").validate("x"))
    }
}
