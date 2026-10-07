/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoadShaderEffectsTest {
    private fun scene(vararg effects: SceneShaderEffect) = SceneDocument(
        nodes = effects.mapIndexed { i, effect -> SceneNode("node$i", components = listOf(effect)) },
    )

    @Test
    fun eachDistinctDocumentIsReadAndCompiledOnce() = runTest {
        val files = ShaderEffectFixtures.Files(mapOf("sky.shader.json" to ShaderEffectFixtures.SKY))

        val loaded = loadShaderEffects(scene(SceneShaderEffect("sky.shader.json"), SceneShaderEffect("sky.shader.json")), files)

        assertEquals(setOf("sky.shader.json"), loaded.documents.keys)
        assertEquals(mapOf("sky.shader.json" to 1), files.reads)
    }

    /** One bad file loses its own effects; the scene plays on, and loading never throws. */
    @Test
    fun aDocumentThatIsMissingOrRejectedIsLeftOutAndTheRestLoad() = runTest {
        val files = ShaderEffectFixtures.Files(
            mapOf("sky.shader.json" to ShaderEffectFixtures.SKY, "broken.shader.json" to ShaderEffectFixtures.BROKEN),
        )

        val loaded = loadShaderEffects(
            scene(SceneShaderEffect("sky.shader.json"), SceneShaderEffect("broken.shader.json"), SceneShaderEffect("absent.shader.json")),
            files,
        )

        assertEquals(setOf("sky.shader.json"), loaded.documents.keys)
    }

    @Test
    fun aSceneWithNoEffectsReadsNothing() = runTest {
        val files = ShaderEffectFixtures.Files(emptyMap())

        assertSame(ShaderEffectAssets.Empty, loadShaderEffects(SceneDocument(nodes = listOf(SceneNode("empty"))), files))
        assertEquals(emptyMap(), files.reads)
    }

    @Test
    fun theProblemsNameTheParameterOrTextureAndAMatchingEffectHasNone() {
        val pool = ShaderDocuments.compile(ShaderEffectFixtures.POOL)
        val pixel = TextureAsset(ByteArray(4), width = 1, height = 1)
        val assets = ShaderEffectAssets(mapOf("pool.shader.json" to pool), mapOf("ripples.png" to pixel))
        fun problems(effect: SceneShaderEffect) = shaderEffectProblems(effect, pool, assets)

        assertEquals(emptyList(), problems(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png"))))
        val wrong = problems(
            SceneShaderEffect(
                "pool.shader.json",
                parameters = mapOf("depth" to listOf(1f, 2f), "colour" to listOf(1f)),
                textures = mapOf("foam" to "foam.png"),
            ),
        )
        assertTrue(wrong.any { "'depth' takes 1 number(s), got 2" in it }, wrong.toString())
        assertTrue(wrong.any { "'colour' is not a parameter" in it }, wrong.toString())
        assertTrue(wrong.any { "texture 'ripples' is declared by the document but given no image" in it }, wrong.toString())
        assertTrue(wrong.any { "texture 'foam' is not declared by the document" in it }, wrong.toString())
        val unloaded = problems(SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "gone.png")))
        assertTrue(unloaded.single().contains("'gone.png', which did not load"), unloaded.toString())
    }
}
