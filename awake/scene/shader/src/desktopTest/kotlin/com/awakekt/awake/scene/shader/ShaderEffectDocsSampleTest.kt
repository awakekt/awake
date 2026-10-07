/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.asset.shaderdocument.ShaderSurface
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * The "Render plans and shaders" guide ships a gradient sky as a project's data: a shader document
 * and the scene that names it. Both files are included from `website/docs/snippets/rendering`. This
 * compiles and decodes them, then runs the Kotlin the guide shows against them.
 */
class ShaderEffectDocsSampleTest {
    init {
        SceneComponentRegistry.registerGlobal(ShaderEffectBinding)
    }

    private val skyText = snippet("gradient-sky.shader.json")
    private val sceneText = snippet("shader-effect.scene.json")

    @Test
    fun theGradientSkyDocumentCompilesForBothBackends() {
        val compiled = ShaderDocuments.compile(skyText)

        assertEquals(ShaderSurface.Background, compiled.document.surface)
        assertEquals(listOf("top", "bottom"), compiled.document.parameters.map { it.name })
        assertTrue("fragmentMain" in compiled.emitWgsl(ClipSpace.Vulkan))
        assertTrue("fragmentMain" in compiled.emitWgsl(ClipSpace.WebGpu))
    }

    @Test
    fun theSceneDecodesAndItsEffectMatchesTheDocumentItNames() {
        val effect = shaderEffectOf(SceneLoader.decode(sceneText))
        val compiled = ShaderDocuments.compile(skyText)

        assertEquals("shaders/gradient-sky.shader.json", effect.shader)
        assertEquals(setOf("top", "bottom"), effect.parameters.keys)
        assertEquals(emptyList(), shaderEffectProblems(effect, compiled, ShaderEffectAssets(mapOf(effect.shader to compiled))))
    }

    /** The Kotlin the guide shows: load the project's documents, draw them, and show an edit by loading again. */
    @Test
    fun aProjectsDocumentsAreDrawnAndAnEditIsShownByLoadingAgain() = runTest {
        val scene = SceneLoader.decode(sceneText)
        val effect = shaderEffectOf(scene)
        val project = mutableMapOf(effect.shader to skyText)
        val files = ShaderEffectFixtures.Files(project)
        val recording = RecordingHost()
        val host: ContentFeatureHost? = recording
        val world = World().also { it.add(it.create(), ShaderEffectSource(effect)) }

        // --8<-- [start:attach]
        // When the project loads: read, check and compile every document the scene names, once each.
        val effects = loadShaderEffects(scene, files)
        // The system attaches each effect to the renderer's content-feature host, and runs its clock.
        val system = ShaderEffectSystem(host, effects)
        // --8<-- [end:attach]
        repeat(2) { system.update(world, FRAME) }
        val first = recording.live.single()
        assertTrue(first.drawFrame().drew)

        project[effect.shader] = skyText.replaced("0.9, 0.5, 0.3, 1", "0.2, 0.2, 0.2, 1")
        // --8<-- [start:preview]
        // A document changed: load again, and hand the system the result.
        system.assets = loadShaderEffects(scene, files)
        // --8<-- [end:preview]
        repeat(2) { system.update(world, FRAME) }
        val second = recording.live.single()
        assertNotSame(first, second)
        assertTrue(first.detached, "the old effect is detached once the new one is ready")
        assertTrue(second.drawFrame().drew)

        project[effect.shader] = skyText.replaced("\"surface\": \"background\"", "\"surface\": \"floor\"")
        system.assets = loadShaderEffects(scene, files)
        repeat(2) { system.update(world, FRAME) }
        assertEquals(2, recording.attachments.size, "a broken edit is not attached")
        assertFalse(second.detached, "and the effect that was drawing keeps drawing")
        assertTrue(second.drawFrame().drew)

        system.release()
        assertEquals(emptyList(), recording.live)
    }

    /** The `shader_effect` that is the snippet scene's one component. */
    private fun shaderEffectOf(scene: SceneDocument): SceneShaderEffect {
        val node = scene.nodes.single()
        return assertIs<SceneShaderEffect>(node.components.single())
    }

    private fun snippet(name: String): String = File("../../../website/docs/snippets/rendering/$name").readText()

    /** This text with [from] swapped for [to], which it must contain, so an edit cannot silently do nothing. */
    private fun String.replaced(from: String, to: String): String {
        assertTrue(from in this, "the snippet no longer contains '$from'")
        return replace(from, to)
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}
