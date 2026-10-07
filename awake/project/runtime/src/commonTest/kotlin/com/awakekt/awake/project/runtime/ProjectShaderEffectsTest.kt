/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.shader.ShaderEffectSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectShaderEffectsTest {
    private val input = Input()

    private fun services(renderer: Renderer, content: SceneContent = SceneContent.Empty) = SceneHostServices(
        input = { GameplayInput(input.currentSnapshot, InputOwnership()) },
        renderer = renderer,
        content = content,
    )

    @Test
    fun aSceneWithAShaderEffectGetsItsSystemAndOneWithoutDoesNot() {
        installProjectComponents()

        val with = sceneSystemsFor(SceneLoader.decode(SKY_SCENE), services(NoopRenderer()))
        val without = sceneSystemsFor(SceneLoader.decode(PLAIN_SCENE), services(NoopRenderer()))

        assertTrue(with.frame.any { it is ShaderEffectSystem })
        assertFalse(without.frame.any { it is ShaderEffectSystem })
    }

    /** One broken document loses its own effect; the project still loads, and the good one is ready. */
    @Test
    fun aProjectLoadsItsDocumentsAndABrokenOneDoesNotStopTheLoad() = runTest {
        val project = loadProject(files(TWO_EFFECTS_SCENE))

        assertEquals(setOf("shaders/sky.shader.json"), project.content[ShaderEffectsCapability.Effects]?.documents?.keys)
    }

    @Test
    fun anEffectAttachesThroughAHostRendererAndIsDetachedWhenTheSceneCloses() = runTest {
        val project = loadProject(files(SKY_SCENE))
        val renderer = HostRenderer()
        val systems = sceneSystemsFor(project.scene, services(renderer, project.content))
        val world = World().also { SceneLoader.instantiate(project.scene, it) }

        repeat(2) { systems.frame.forEach { system -> system.update(world, 1f / 60f) } }
        assertEquals(1, renderer.attached)

        systems.close()
        assertEquals(1, renderer.detached)
    }

    private class HostRenderer : NoopRenderer(), ContentFeatureHost {
        var attached = 0
        var detached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { detached++ }
        }
    }

    private fun files(scene: String) = AssetSource { path ->
        runCatching {
            mapOf(
                MANIFEST_PATH to MANIFEST,
                "scenes/main.scene.json" to scene,
                "shaders/sky.shader.json" to SKY_DOCUMENT,
                "shaders/broken.shader.json" to BROKEN_DOCUMENT,
            ).getValue(path.value).encodeToByteArray()
        }
    }

    private companion object {
        const val MANIFEST_PATH = "awake.project.json"
        const val MANIFEST = """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
        const val SKY_DOCUMENT = """{"name":"Sky","surface":"background","fragment":{"color":{"op":"const","value":[0.2,0.4,0.8,1]}}}"""
        const val BROKEN_DOCUMENT = """{"name":"Broken","surface":"overlay","fragment":{"color":{"op":"param","name":"missing"}}}"""
        const val PLAIN_SCENE = """{ "version": 1, "name": "still", "nodes": [ { "name": "Rock", "components": [] } ] }"""
        const val SKY_SCENE = """
{ "version": 1, "name": "sky", "nodes": [
  { "name": "Sky", "components": [ { "component": "shader_effect", "shader": "shaders/sky.shader.json" } ] } ] }
"""
        const val TWO_EFFECTS_SCENE = """
{ "version": 1, "name": "sky", "nodes": [
  { "name": "Sky", "components": [ { "component": "shader_effect", "shader": "shaders/sky.shader.json" } ] },
  { "name": "Glitch", "components": [ { "component": "shader_effect", "shader": "shaders/broken.shader.json" } ] } ] }
"""
    }
}
