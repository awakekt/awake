/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

import com.awakekt.awake.render.texture.CompressedTextureImage
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * The one definition of how [Renderer.maxAnisotropy] and [Renderer.textureMipSkip] apply to a
 * texture, shared by every backend so they agree on which textures a setting reaches (#1660).
 */
object TextureSampling {
    /** The most top mip levels [Renderer.textureMipSkip] can leave out: quarter size. */
    const val MAX_MIP_SKIP: Int = 2

    /** [requested] anisotropic samples, at least 1 and at most [supported]. */
    fun anisotropy(requested: Int, supported: Int): Int = requested.coerceIn(1, maxOf(1, supported))

    /** [requested] top mip levels to leave out, within `0..MAX_MIP_SKIP`. */
    fun mipSkip(requested: Int): Int = requested.coerceIn(0, MAX_MIP_SKIP)

    /**
     * The anisotropic samples a texture with [filtering] takes at [maxAnisotropy]: only a fully linear,
     * mipmapped texture filters anisotropically, as WebGPU requires of a sampler with `maxAnisotropy`
     * above 1; `Nearest` (pixel art) and `BaseLevelLinear` stay at 1.
     */
    fun anisotropyFor(filtering: TextureFiltering, maxAnisotropy: Int): Int =
        if (filtering == TextureFiltering.Linear) maxOf(1, maxAnisotropy) else 1

    /**
     * Mip levels to leave out of a [levels]-level chain at [mipSkip]: none for a `Nearest` texture or
     * a single level, and never so many that fewer than one level remains.
     */
    fun levelsToSkip(filtering: TextureFiltering, levels: Int, mipSkip: Int): Int =
        if (filtering == TextureFiltering.Nearest || levels <= 1) 0 else mipSkip(mipSkip).coerceAtMost(levels - 1)

    /**
     * Top levels to leave out of a block-compressed [image]'s own chain at [mipSkip], as
     * [levelsToSkip] for its level count, and fewer where the level that would become the base is not
     * whole blocks: a GPU sizes a block-compressed image in whole blocks. One level skips nothing.
     */
    fun levelsToSkip(image: CompressedTextureImage, filtering: TextureFiltering, mipSkip: Int): Int {
        var skip = levelsToSkip(filtering, image.levels.size, mipSkip)
        while (skip > 0 && !image.wholeBlocksAt(skip)) skip--
        return skip
    }

    private fun CompressedTextureImage.wholeBlocksAt(level: Int): Boolean =
        levelWidth(level) % format.blockWidth == 0 && levelHeight(level) % format.blockHeight == 0
}
