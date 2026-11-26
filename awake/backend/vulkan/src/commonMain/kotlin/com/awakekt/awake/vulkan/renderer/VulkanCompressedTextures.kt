/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.pipeline.GpuCompressedTextures
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.PbrCompressedSet
import com.awakekt.awake.render.texture.PbrImageSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.render.texture.toTextureAsset
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * The Vulkan [GpuCompressedTextures]: block-compressed images uploaded as they are, with the levels
 * they bring, and bound as a material's base colour and/or PBR channels like any texture. [formats]
 * are the families the device enabled; see
 * [com.awakekt.awake.vulkan.device.GraphicsDevice.compressedTextureFormats].
 */
internal class VulkanCompressedTextures(private val renderer: Renderer) : GpuCompressedTextures {
    override val formats: Set<CompressedTextureFormat> get() = renderer.graphicsDevice.compressedTextureFormats

    override fun createMaterial(image: CompressedTextureImage, filtering: TextureFiltering, uniformFloatCount: Int): RenderMaterial? {
        if (image.format !in formats) return null
        val texture = uploadCompressed(image, filtering)
        val material = Material.createSharedLayout(renderer.graphicsDevice, uniformFloatCount).apply {
            createResources(texture, renderer.pbrImageViews(null))
        }
        return renderer.trackMaterial(material) { renderer.textureResources.release(texture) }
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
        val supported = formats
        val usableBase = baseCompressed?.takeIf { it.format in supported }
        val usableMr = pbrCompressed.metallicRoughness?.takeIf { it.format in supported }
        val usableNormal = pbrCompressed.normal?.takeIf { it.format in supported }
        val usableOcclusion = pbrCompressed.occlusion?.takeIf { it.format in supported }
        val usableEmissive = pbrCompressed.emissive?.takeIf { it.format in supported }
        val anyCompressed = listOf(usableBase, usableMr, usableNormal, usableOcclusion, usableEmissive).any { it != null }
        if (!anyCompressed) return null

        val ownedTextures = ArrayList<Texture>(MAX_MATERIAL_TEXTURES)
        var committed = false
        return try {
            val baseTexture = if (usableBase != null) {
                uploadCompressed(usableBase, filtering)
            } else {
                renderer.uploadTexture(baseColor.toTextureAsset(filtering))
            }
            ownedTextures.add(baseTexture)

            val pbrViews = PbrImageViews(
                metallicRoughness = resolvePbrView(usableMr, pbrImages.metallicRoughness, Renderer.NEUTRAL_METALLIC_ROUGHNESS, ownedTextures),
                normal = resolvePbrView(usableNormal, pbrImages.normal, Renderer.NEUTRAL_NORMAL, ownedTextures),
                occlusion = resolvePbrView(usableOcclusion, pbrImages.occlusion, Renderer.NEUTRAL_OCCLUSION, ownedTextures),
                emissive = resolvePbrView(usableEmissive, pbrImages.emissive, Renderer.NEUTRAL_EMISSIVE, ownedTextures),
            )
            val material = Material.createSharedLayout(renderer.graphicsDevice, uniformFloatCount).apply {
                createResources(baseTexture, pbrViews)
            }
            val tracked = renderer.trackMaterial(material) { ownedTextures.forEach(renderer.textureResources::release) }
            committed = true
            tracked
        } finally {
            if (!committed) ownedTextures.forEach(renderer.textureResources::release)
        }
    }

    private fun uploadCompressed(image: CompressedTextureImage, filtering: TextureFiltering): Texture =
        Texture.compressed(renderer.graphicsDevice, renderer.transferContext::runOneTimeCommands, image, filtering)
            .also(renderer.textureResources::register)

    private fun resolvePbrView(
        compressed: CompressedTextureImage?,
        source: GpuImageSource?,
        neutral: TextureAsset,
        ownedTextures: MutableList<Texture>,
    ): Long = when {
        compressed != null -> uploadCompressed(compressed, TextureFiltering.Linear).also(ownedTextures::add).imageView.handle
        source != null -> renderer.uploadTexture(source.toTextureAsset(TextureFiltering.Linear)).also(ownedTextures::add).imageView.handle
        else -> renderer.pbrImageView(null, neutral)
    }

    private companion object {
        const val MAX_MATERIAL_TEXTURES = 5
    }
}
