/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.texture.CompressedTextureFormat
import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * Optional capability: a device that samples textures in GPU block-compression formats, so they stay
 * compressed in GPU memory, at a quarter or less of an RGBA8 texture's size (#1211).
 *
 * A loader asks [formats] which formats this device takes, transcodes a Basis Universal texture to
 * one of them (or picks the cooked ASTC or ETC2 one), and uploads it through [createMaterial]. A
 * device without the capability, or one that takes none of a texture's formats, gets the texture
 * decoded to RGBA8 and uploaded as every texture is.
 */
interface GpuCompressedTextures : GpuCapability {
    /** The block formats this device samples. Empty when it takes none. */
    val formats: Set<CompressedTextureFormat>

    /**
     * Builds a material whose base-colour texture is [image], uploaded as it is, or returns null when
     * this device doesn't take [CompressedTextureImage.format].
     *
     * The levels given are the texture's whole mip chain: none is generated. A one-level image with
     * [TextureFiltering.Linear] is sampled without mips.
     */
    fun createMaterial(
        image: CompressedTextureImage,
        filtering: TextureFiltering,
        uniformFloatCount: Int,
    ): Material?

    /** The key [GpuDevice.capability] answers with this device's compressed textures, when it has them. */
    companion object : GpuCapabilityKind<GpuCompressedTextures>
}

/** The block formats this device samples: [GpuCompressedTextures.formats], or none without the capability. */
val GpuDevice.compressedTextureFormats: Set<CompressedTextureFormat>
    get() = capability(GpuCompressedTextures)?.formats ?: emptySet()
