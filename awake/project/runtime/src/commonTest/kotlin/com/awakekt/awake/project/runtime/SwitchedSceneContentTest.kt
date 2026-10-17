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
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.shader.ShaderEffectAssets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * An editor whose Play switches to a scene it loaded nothing for reads Core's content for it with
 * [loadCoreSceneContent], every kind, and lays what it holds itself over it with [SceneContent.plus].
 */
class SwitchedSceneContentTest {
    private val input = Input()

    @Test
    fun coreContentHoldsEveryKindLoadSceneContentLoadsForTheScene() = runTest {
        installProjectComponents()
        val level = decode(LEVEL)

        val core = loadCoreSceneContent(level, files())
        val whole = loadSceneContent(level, files())

        assertEquals(whole.values.keys, core.values.keys)
        assertTrue(
            core.values.keys.containsAll(listOf(CoreSceneContent.ShaderEffects, CoreSceneContent.ParticleSprites, CoreSceneContent.AudioClips)),
            core.values.keys.toString(),
        )
        assertEquals(setOf(GLOW), core[CoreSceneContent.ShaderEffects]?.documents?.keys)
    }

    /** What happened to an editor that drew only the documents it had opened, for another scene. */
    @Test
    fun withOnlyTheEditorsOwnDocumentsTheSwitchedToScenesEffectDoesNotDraw() = runTest {
        installProjectComponents()

        val attached = attachedFor(SceneContent.build { this[CoreSceneContent.ShaderEffects] = ShaderEffectAssets.Empty })

        assertEquals(0, attached)
    }

    @Test
    fun withCoresContentUnderTheEditorsTheSwitchedToScenesEffectDraws() = runTest {
        installProjectComponents()
        val core = loadCoreSceneContent(decode(LEVEL), files())
        val editing = ShaderEffectAssets.Empty

        val content = core + SceneContent.build { this[CoreSceneContent.ShaderEffects] = core[CoreSceneContent.ShaderEffects]!! + editing }

        assertEquals(1, attachedFor(content))
    }

    @Test
    fun contentLaidOverOtherContentReplacesOnlyTheKeysItHolds() {
        val dust = TextureAsset(ByteArray(4), 1, 1)
        val glow = ShaderEffectAssets()
        val edited = ShaderEffectAssets()
        val read = SceneContent.build {
            this[CoreSceneContent.ParticleSprites] = mapOf("dust.png" to dust)
            this[CoreSceneContent.ShaderEffects] = glow
        }

        val laid = read + SceneContent.build { this[CoreSceneContent.ShaderEffects] = edited }

        assertSame(edited, laid[CoreSceneContent.ShaderEffects])
        assertSame(dust, laid[CoreSceneContent.ParticleSprites]?.get("dust.png"))
        assertSame(glow, read[CoreSceneContent.ShaderEffects], "the content laid over is left as it was")
        assertSame(read, read + SceneContent.Empty)
        assertSame(read, SceneContent.Empty + read)
    }

    /** How many effects the level's systems attach in two frames, given [content]. */
    private fun attachedFor(content: SceneContent): Int {
        val level = decode(LEVEL)
        val renderer = HostRenderer()
        val services = SceneHostServices(input = { GameplayInput(input.currentSnapshot, InputOwnership()) }, renderer = renderer, content = content)
        val world = World().also { SceneLoader.instantiate(level, it) }
        sceneSystemsFor(level, services).use { systems -> repeat(2) { systems.frame.forEach { system -> system.update(world, STEP) } } }
        return renderer.attached
    }

    private fun decode(scene: String): SceneDocument = SceneLoader.decode(scene)

    private fun files() = AssetSource { path ->
        runCatching { mapOf(GLOW to GLOW_DOCUMENT).getValue(path.value).encodeToByteArray() }
    }

    private class HostRenderer :
        NoopRenderer(),
        ContentFeatureHost {
        var attached = 0

        override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
            attached++
            return AttachedContentFeature { }
        }
    }

    private companion object {
        const val STEP = 1f / 60f
        const val GLOW = "shaders/coin-glow.shader.json"
        const val GLOW_DOCUMENT = """{"name":"Glow","surface":"background","fragment":{"color":{"op":"const","value":[1,0.8,0.2,1]}}}"""

        // The level a menu's Play button switches to: a glow, sparks and a sound, none of which the
        // menu names. The sprite and the sound are missing, which loses only them.
        const val LEVEL = """
{ "version": 1, "name": "level", "nodes": [
  { "name": "Coin", "components": [ { "component": "shader_effect", "shader": "shaders/coin-glow.shader.json" } ] },
  { "name": "Sparks", "components": [ { "component": "particle_emitter", "texture": "spark.png" } ] },
  { "name": "Chime", "components": [ { "component": "audio_source", "clip": "chime.wav" } ] }
] }
"""
    }
}
