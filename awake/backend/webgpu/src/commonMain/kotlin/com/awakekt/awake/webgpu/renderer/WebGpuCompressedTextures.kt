/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.texture.canUploadCompressed
import com.awakekt.awake.webgpu.texture.uploadCompressed
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * The WebGPU [GpuCompressedTextures]: a block-compressed image uploaded as it is, with the levels it
 * brings, and bound as a material's base colour like any texture. [formats] are the families the
 * device was granted; see [com.awakekt.awake.webgpu.device.GraphicsDevice.compressedTextureFormats].
 * An image whose base size isn't whole blocks, which WebGPU refuses, gets null, so the caller
 * decodes it to RGBA8 instead.
 */
internal class WebGpuCompressedTextures(private val renderer: Renderer) : GpuCompressedTextures {
    override val formats: Set<CompressedTextureFormat> get() = renderer.graphicsDevice.compressedTextureFormats

    override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): RenderMaterial? {
        if (image.format !in formats || !canUploadCompressed(image)) return null
        val device = renderer.graphicsDevice
        val texture = renderer.textureResources.register(Texture.fromFilled(device, device.uploadCompressed(image), filtering))
        return renderer.createTexturedMaterial(texture, uniformFloatCount, List(PBR_MAPS) { null })
    }

    private companion object {
        const val PBR_MAPS = 4
    }
}
