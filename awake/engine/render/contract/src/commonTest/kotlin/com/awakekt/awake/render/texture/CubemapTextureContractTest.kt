/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.render.pipeline.ResourceBinding
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.pipeline.ShaderStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CubemapTextureContractTest {

    @Test
    fun validCubemapAssetCreation() {
        val faceSize = 4
        val faceBytes = faceSize * faceSize * 4
        val faces = (0 until 6).map { i ->
            ByteArray(faceBytes) { i.toByte() }
        }

        val cubemap = createCubemapAsset(faces, faceSize)

        assertEquals(faceSize, cubemap.width)
        assertEquals(faceSize, cubemap.height)
        assertEquals(6, cubemap.layerCount)
        assertTrue(cubemap.isCubemap)
        assertEquals(faceBytes * 6, cubemap.data.size)

        for (i in 0 until 6) {
            val offset = cubemap.layerOffset(i)
            assertEquals(i * faceBytes, offset)
            assertEquals(i.toByte(), cubemap.data[offset])
        }
    }

    @Test
    fun cubemapRequiresSquareFaces() {
        assertFailsWith<IllegalArgumentException> {
            TextureAsset(
                data = ByteArray(4 * 8 * 4 * 6),
                width = 4,
                height = 8,
                layerCount = 6,
                isCubemap = true,
            )
        }
    }

    @Test
    fun cubemapRequiresExactly6Layers() {
        assertFailsWith<IllegalArgumentException> {
            TextureAsset(
                data = ByteArray(4 * 4 * 4 * 1),
                width = 4,
                height = 4,
                layerCount = 1,
                isCubemap = true,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            TextureAsset(
                data = ByteArray(4 * 4 * 4 * 5),
                width = 4,
                height = 4,
                layerCount = 5,
                isCubemap = true,
            )
        }
    }

    @Test
    fun createCubemapAssetRejectsWrongFaceCount() {
        val faceBytes = 4 * 4 * 4
        assertFailsWith<IllegalArgumentException> {
            createCubemapAsset(
                faces = (0 until 5).map { ByteArray(faceBytes) },
                faceSize = 4,
            )
        }
    }

    @Test
    fun createCubemapAssetRejectsMismatchedFaceSize() {
        val faceBytes = 4 * 4 * 4
        val faces = (0 until 6).map { i ->
            if (i == 3) ByteArray(faceBytes - 1) else ByteArray(faceBytes)
        }
        assertFailsWith<IllegalArgumentException> {
            createCubemapAsset(faces, faceSize = 4)
        }
    }

    @Test
    fun resourceBindingCubemapFlagValid() {
        val binding = ResourceBinding(
            binding = 1,
            kind = ResourceKind.SampledTexture,
            stages = setOf(ShaderStage.Fragment),
            cubemap = true,
        )
        assertTrue(binding.cubemap)
    }

    @Test
    fun resourceBindingCubemapOnNonSampledTextureFails() {
        assertFailsWith<IllegalArgumentException> {
            ResourceBinding(
                binding = 0,
                kind = ResourceKind.UniformBuffer,
                stages = setOf(ShaderStage.Fragment),
                cubemap = true,
            )
        }
    }

    @Test
    fun resourceBindingCannotBeBothArrayedAndCubemap() {
        assertFailsWith<IllegalArgumentException> {
            ResourceBinding(
                binding = 1,
                kind = ResourceKind.SampledTexture,
                stages = setOf(ShaderStage.Fragment),
                arrayed = true,
                cubemap = true,
            )
        }
    }
}
