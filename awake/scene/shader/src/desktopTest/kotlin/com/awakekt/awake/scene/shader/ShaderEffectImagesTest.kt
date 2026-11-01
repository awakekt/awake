/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.shader

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentException
import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.core.image.createBitmap
import com.awakekt.awake.core.image.toRgba8Bytes
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * [loadShaderEffects] decodes a `shader_effect`'s images for the GPU (awakekt/awake#1184): they land in
 * [ShaderEffectAssets.images], whose pixels off the browser are exactly what `createBitmap` decodes, and
 * an effect drawing from them has no problem to report. `ShaderEffectImageBrowserTest` covers the GPU copy.
 */
class ShaderEffectImagesTest {
    private val pool = SceneShaderEffect("pool.shader.json", textures = mapOf("ripples" to "ripples.png"))

    @Test
    fun anEffectsImageIsDecodedForTheGpuWithItsOwnPixels() = runTest {
        val files = AssetSource { path ->
            when (path.value) {
                "pool.shader.json" -> Result.success(ShaderEffectFixtures.POOL.encodeToByteArray())
                "ripples.png" -> Result.success(PNG)
                else -> Result.failure(NoSuchElementException(path.value))
            }
        }

        val loaded = loadShaderEffects(SceneDocument(nodes = listOf(SceneNode("pool", components = listOf(pool)))), files)

        val decoded = createBitmap(PNG)
        val image = loaded.images.getValue("ripples.png")
        assertContentEquals(decoded.toRgba8Bytes(), image.readRgba8())
        assertEquals(emptyMap(), loaded.texturesOf(pool), "nothing is decoded to CPU pixels")
        assertEquals(setOf("ripples"), loaded.imagesOf(pool).keys)
        assertEquals(emptyList(), shaderEffectProblems(pool, loaded.documents.getValue("pool.shader.json"), loaded))
    }

    // A host's own CPU texture still draws; an image for the same path is taken in its place.
    @Test
    fun anImageIsTakenBeforeAHostsTextureForThePath() {
        val compiled = ShaderDocuments.compile(ShaderEffectFixtures.POOL)
        val texture = TextureAsset(ByteArray(4), 1, 1)
        val image = TextureAsset(byteArrayOf(1, 2, 3, 4), 1, 1).asGpuImageSource()
        val assets = ShaderEffectAssets(mapOf("pool.shader.json" to compiled), mapOf("ripples.png" to texture), emptySet(), mapOf("ripples.png" to image))

        assertEquals(emptyMap(), assets.texturesOf(pool))
        assertEquals(mapOf("ripples" to image), assets.imagesOf(pool))
        assertEquals(mapOf("ripples" to texture), ShaderEffectAssets(textures = mapOf("ripples.png" to texture)).texturesOf(pool))
    }

    @Test
    fun aTextureGivenBothWaysIsRefused() {
        val compiled = ShaderDocuments.compile(ShaderEffectFixtures.POOL)
        val texture = TextureAsset(ByteArray(4), 1, 1)

        assertFailsWith<ShaderDocumentException> {
            compiled.contentFeature(compiled.newInputs(), mapOf("ripples" to texture), mapOf("ripples" to texture.asGpuImageSource()))
        }
    }

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, 0xFF2080C0.toInt())
            image.setRGB(1, 1, 0x80FF0010.toInt())
            ImageIO.write(image, "png", it)
        }.toByteArray()
    }
}
