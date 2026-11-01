/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.asGpuImageSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** [createImageMaterial] takes the GPU path when the device copies the image, and the CPU path otherwise. */
class GpuImageUploadTest {
    @Test
    fun aDeviceWithoutTheCapabilityUploadsTheCpuPixels() {
        val device = RecordingDevice(upload = null)
        val image = Image()
        val material = device.createImageMaterial(image, TextureFiltering.Nearest, uniformFloatCount = 8)
        assertSame(device.cpuMaterial, material)
        val asset = device.uploaded.single()
        assertContentEquals(image.pixels, asset.data)
        assertEquals(Triple(2, 1, TextureFiltering.Nearest), Triple(asset.width, asset.height, asset.filtering))
        assertEquals(listOf(8), device.uniformFloatCounts)
        assertEquals(1, image.reads)
    }

    @Test
    fun aDeviceThatCopiesTheImageNeverReadsItsPixels() {
        val gpuMaterial = NoMaterial()
        val upload = RecordingUpload(answer = gpuMaterial)
        val device = RecordingDevice(upload)
        val image = Image()
        assertSame(gpuMaterial, device.createImageMaterial(image))
        assertEquals(listOf(TextureFiltering.Linear to GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT), upload.asked)
        assertEquals(0, image.reads)
        assertEquals(emptyList(), device.uploaded)
    }

    @Test
    fun anImageTheDeviceCannotCopyFallsBackToTheCpuPixels() {
        val device = RecordingDevice(RecordingUpload(answer = null))
        val image = Image()
        assertSame(device.cpuMaterial, device.createImageMaterial(image))
        assertEquals(1, image.reads)
        assertEquals(1, device.uploaded.size)
    }

    // The CPU path reads every map into the PbrTextureSet the plain upload takes, as glTF materials had it.
    @Test
    fun theCpuPathReadsEveryPbrMap() {
        val device = RecordingDevice(upload = null)
        val normal = TextureAsset(byteArrayOf(9, 9, 9, 9), 1, 1)
        device.createImageMaterial(Image(), pbrImages = PbrImageSet(normal = normal.asGpuImageSource()))
        val pbr = assertNotNull(device.pbrTextures.single())
        assertEquals(normal, pbr.normal)
        assertNull(pbr.metallicRoughness)
        assertNull(pbr.emissive)
    }

    @Test
    fun aMultiLayerTextureIsNoImageSource() {
        assertFailsWith<IllegalArgumentException> { TextureAsset(ByteArray(8), 1, 1, layerCount = 2).asGpuImageSource() }
    }

    private class Image : GpuImageSource {
        val pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var reads = 0
        override val width = 2
        override val height = 1
        override val externalImage: Any? = null
        override fun readRgba8(): ByteArray = pixels.also { reads++ }
    }

    private class RecordingUpload(private val answer: Material?) : GpuImageUpload {
        val asked = mutableListOf<Pair<TextureFiltering, Int>>()
        override fun createMaterial(image: GpuImageSource, filtering: TextureFiltering, uniformFloatCount: Int, pbrImages: PbrImageSet?): Material? {
            asked += filtering to uniformFloatCount
            return answer
        }
    }

    private class RecordingDevice(private val upload: GpuImageUpload?) : GpuDevice {
        val cpuMaterial = NoMaterial()
        val uploaded = mutableListOf<TextureAsset>()
        val uniformFloatCounts = mutableListOf<Int>()
        val pbrTextures = mutableListOf<PbrTextureSet?>()
        override val clipSpace: ClipSpace = ClipSpace.Vulkan
        override fun createMesh(geometry: MeshGeometry): Mesh = error("unused")
        override fun createMaterial(texture: TextureAsset?, renderTarget: RenderTarget?, uniformFloatCount: Int, pbrTextures: PbrTextureSet?): Material {
            uploaded += checkNotNull(texture)
            uniformFloatCounts += uniformFloatCount
            this.pbrTextures += pbrTextures
            return cpuMaterial
        }
        override fun createRenderTarget(width: Int, height: Int): RenderTarget = error("unused")
        override fun destroy() = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <T : GpuCapability> capability(kind: GpuCapabilityKind<T>): T? = if (kind == GpuImageUpload) upload as T? else null
    }

    private class NoMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
