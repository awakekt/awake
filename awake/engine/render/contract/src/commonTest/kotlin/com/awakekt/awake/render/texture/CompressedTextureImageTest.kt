/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.pipeline.GpuCapability
import com.awakekt.awake.render.pipeline.GpuCapabilityKind
import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.pipeline.GpuDevice
import com.awakekt.awake.render.pipeline.compressedTextureFormats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompressedTextureImageTest {

    @Test
    fun aLevelIsWholeBlocksWithAPartialOneRoundedUp() {
        assertEquals(8, CompressedTextureFormat.Bc1Rgba.byteSize(4, 4))
        assertEquals(16, CompressedTextureFormat.Bc7Rgba.byteSize(1, 1), "a 1 x 1 level still takes a block")
        assertEquals(4 * 16, CompressedTextureFormat.Astc4x4.byteSize(5, 5), "5 x 5 rounds up to 2 x 2 blocks")
    }

    @Test
    fun aFullMipChainIsAccepted() {
        // 16 x 8: levels 16x8, 8x4, 4x2, 2x1, 1x1, each at least one 16-byte block.
        val sizes = listOf(8 * 16, 2 * 16, 16, 16, 16)
        val image = CompressedTextureImage(CompressedTextureFormat.Bc7Rgba, 16, 8, srgb = true, sizes.map { ByteArray(it) })

        assertEquals(5, image.levels.size)
        assertEquals(2, image.levelWidth(3))
        assertEquals(1, image.levelHeight(3))
    }

    @Test
    fun aLevelOfTheWrongSizeOrTooManyLevelsIsRefused() {
        val wrongSize = assertFailsWith<IllegalArgumentException> {
            CompressedTextureImage(CompressedTextureFormat.Etc2Rgba8, 8, 8, srgb = false, listOf(ByteArray(63)))
        }
        assertTrue("64 bytes" in wrongSize.message.orEmpty(), wrongSize.message)
        assertFailsWith<IllegalArgumentException>("a 4 x 4 texture has 3 levels at most") {
            CompressedTextureImage(CompressedTextureFormat.Bc1Rgba, 4, 4, srgb = false, List(4) { ByteArray(8) })
        }
        assertFailsWith<IllegalArgumentException>("the base level is required") {
            CompressedTextureImage(CompressedTextureFormat.Bc1Rgba, 4, 4, srgb = false, emptyList())
        }
    }

    @Test
    fun aDeviceWithoutTheCapabilityTakesNoCompressedFormats() {
        assertEquals(emptySet(), Device(null).compressedTextureFormats)
        val bc = setOf(CompressedTextureFormat.Bc7Rgba, CompressedTextureFormat.Bc3Rgba)
        assertEquals(bc, Device(Compressed(bc)).compressedTextureFormats)
    }

    private class Compressed(override val formats: Set<CompressedTextureFormat>) : GpuCompressedTextures {
        override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material? = null
    }

    private class Device(private val compressed: GpuCompressedTextures?) : GpuDevice {
        override val clipSpace: ClipSpace = ClipSpace.Vulkan
        override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
        override fun createMaterial(
            texture: TextureAsset?,
            renderTarget: RenderTarget?,
            uniformFloatCount: Int,
            pbrTextures: PbrTextureSet?,
        ): Material = error("unused")
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
        override fun destroy() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? =
            if (kind == GpuCompressedTextures) compressed as T? else null
    }
}
