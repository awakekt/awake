/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrCompressedSet
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.toTextureAsset
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.texture.canUploadCompressed
import com.awakekt.awake.webgpu.texture.uploadCompressed
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * The WebGPU [GpuCompressedTextures]: block-compressed images uploaded as they are, with the levels
 * they bring, and bound as a material's base colour and/or PBR channels like any texture. [formats]
 * are the families the device was granted; see
 * [com.awakekt.awake.webgpu.device.GraphicsDevice.compressedTextureFormats]. An image whose base
 * size isn't whole blocks, which WebGPU refuses, falls back per channel to GPU copy or RGBA8.
 */
internal class WebGpuCompressedTextures(private val renderer: Renderer) : GpuCompressedTextures {
    override val formats: Set<CompressedTextureFormat> get() = renderer.graphicsDevice.compressedTextureFormats

    override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): RenderMaterial? {
        if (!canUse(image)) return null
        val texture = uploadCompressedTexture(image, filtering)
        return renderer.createTexturedMaterial(texture, uniformFloatCount, List(PBR_MAPS) { null })
    }

    @Suppress("LongParameterList") // Implements GpuCompressedTextures PBR material creation contract.
    override fun createMaterial(
        baseColor: GpuImageSource,
        baseCompressed: CompressedTextureImage?,
        filtering: TextureFiltering,
        uniformFloatCount: Int,
        pbrImages: PbrImageSet,
        pbrCompressed: PbrCompressedSet,
    ): RenderMaterial? {
        val usableBase = baseCompressed?.takeIf(::canUse)
        val usableMaps = pbrCompressed.maps.map { it?.takeIf(::canUse) }
        if (usableBase == null && usableMaps.all { it == null }) return null

        val uploaded = ArrayList<Texture>(PBR_MAPS + 1)
        var committed = false
        return try {
            val base = (usableBase?.let { uploadCompressedTexture(it, filtering) } ?: uploadUncompressed(baseColor, filtering))
                .also(uploaded::add)
            val maps = pbrImages.maps.zip(usableMaps) { source, compressed ->
                when {
                    compressed != null -> uploadCompressedTexture(compressed, TextureFiltering.Linear).also(uploaded::add)
                    source != null -> uploadUncompressed(source, TextureFiltering.Linear).also(uploaded::add)
                    else -> null
                }
            }
            val material = renderer.createTexturedMaterial(base, uniformFloatCount, maps)
            committed = true
            material
        } finally {
            if (!committed) uploaded.forEach(renderer.textureResources::release)
        }
    }

    private fun canUse(image: CompressedTextureImage): Boolean =
        image.format in formats && canUploadCompressed(image)

    private fun uploadCompressedTexture(image: CompressedTextureImage, filtering: TextureFiltering): Texture {
        val device = renderer.graphicsDevice
        return renderer.textureResources.register(Texture.fromFilled(device, device.uploadCompressed(image, filtering), filtering))
    }

    private fun uploadUncompressed(source: GpuImageSource, filtering: TextureFiltering): Texture {
        val external = source.externalImage
        val queue = renderer.graphicsDevice.wgpuContext.device.queue
        val copied = if (external != null && queue.canCopyExternalImage(external)) {
            renderer.imageUpload.upload(external, source.width, source.height, filtering)
        } else {
            null
        }
        return copied ?: renderer.uploadTexture(source.toTextureAsset(filtering))
    }

    private companion object {
        const val PBR_MAPS = 4
    }
}
