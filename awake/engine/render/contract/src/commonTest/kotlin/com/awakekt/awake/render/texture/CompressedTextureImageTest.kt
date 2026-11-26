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
import com.awakekt.awake.render.pipeline.createImageMaterial
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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

    @Test
    fun multiLayerCompressedArraysValidateTotalBytesPerLevel() {
        // 8x8 ETC2 has 4 blocks (64 B) at mip 0, 1 block (16 B) at mip 1, 1 block (16 B) at mip 2.
        val layers = 3
        val array = CompressedTextureImage(
            format = CompressedTextureFormat.Etc2Rgba8,
            width = 8,
            height = 8,
            srgb = true,
            levels = listOf(ByteArray(64 * layers), ByteArray(16 * layers), ByteArray(16 * layers)),
            layerCount = layers,
        )
        assertEquals(3, array.layerCount)
        assertEquals(64, array.layerByteSize(0))
        assertEquals(16, array.layerByteSize(1))

        assertFailsWith<IllegalArgumentException> {
            CompressedTextureImage(CompressedTextureFormat.Etc2Rgba8, 8, 8, srgb = true, listOf(ByteArray(64)), layerCount = 2)
        }
        assertFailsWith<IllegalArgumentException> {
            CompressedTextureImage(CompressedTextureFormat.Etc2Rgba8, 8, 8, srgb = true, listOf(ByteArray(64)), layerCount = 0)
        }
    }

    @Test
    fun createImageMaterialRoutesCompressedPbrChannelsToCapability() {
        val bc7 = CompressedTextureFormat.Bc7Rgba
        val baseCompressed = CompressedTextureImage(bc7, 4, 4, srgb = true, listOf(ByteArray(16)))
        val normalCompressed = CompressedTextureImage(bc7, 4, 4, srgb = false, listOf(ByteArray(16)))
        var capturedBase: CompressedTextureImage? = null
        var capturedPbr: PbrCompressedSet? = null
        val cap = object : GpuCompressedTextures {
            override val formats = setOf(bc7)
            override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): Material? =
                error("3-arg path should not run when PBR images are present")

            override fun createMaterial(
                baseColor: GpuImageSource,
                baseCompressed: CompressedTextureImage?,
                filtering: TextureFiltering,
                uniformFloatCount: Int,
                pbrImages: PbrImageSet,
                pbrCompressed: PbrCompressedSet,
            ): Material {
                capturedBase = baseCompressed
                capturedPbr = pbrCompressed
                return FakeMaterial
            }
        }
        val baseSource = object : GpuImageSource {
            override val width = 4
            override val height = 4
            override val externalImage: Any? = null
            override val compressed = baseCompressed
            override fun readRgba8(): ByteArray = error("compressed base should not decode RGBA8")
        }
        val normalSource = object : GpuImageSource {
            override val width = 4
            override val height = 4
            override val externalImage: Any? = null
            override val compressed = normalCompressed
            override fun readRgba8(): ByteArray = error("compressed normal should not decode RGBA8")
        }

        val made = Device(cap).createImageMaterial(baseSource, pbrImages = PbrImageSet(normal = normalSource))
        assertEquals(FakeMaterial, made)
        assertEquals(baseCompressed, capturedBase)
        assertEquals(normalCompressed, assertNotNull(capturedPbr).normal)
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
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
