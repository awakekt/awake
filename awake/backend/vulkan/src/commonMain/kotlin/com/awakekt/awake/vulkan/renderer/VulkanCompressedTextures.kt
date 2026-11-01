/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * The Vulkan [GpuCompressedTextures]: a block-compressed image uploaded as it is, with the levels it
 * brings, and bound as a material's base colour like any texture. [formats] are the families the
 * device enabled; see [com.awakekt.awake.vulkan.device.GraphicsDevice.compressedTextureFormats].
 */
internal class VulkanCompressedTextures(private val renderer: Renderer) : GpuCompressedTextures {
    override val formats: Set<CompressedTextureFormat> get() = renderer.graphicsDevice.compressedTextureFormats

    override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): RenderMaterial? {
        if (image.format !in formats) return null
        val texture = Texture.compressed(renderer.graphicsDevice, renderer.transferContext::runOneTimeCommands, image, filtering)
        renderer.textureResources.register(texture)
        return Material(renderer.graphicsDevice, uniformFloatCount).apply { createResources(texture, renderer.pbrImageViews(null)) }
    }
}
