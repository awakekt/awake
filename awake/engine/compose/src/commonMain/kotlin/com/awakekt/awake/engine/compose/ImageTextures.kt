/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.compose

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.graphics2d.FilterQuality
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering

/**
 * GPU textures for the [ImageBitmap]s a UI draws, uploaded the first frame each one appears.
 *
 * Kept for the host's lifetime: a texture uploaded through `createMaterial` is owned by the
 * renderer and freed only at its teardown, so evicting an image and uploading it again would grow
 * GPU memory rather than save it. Memory is therefore every distinct bitmap drawn so far; reuse a
 * decoded bitmap instead of decoding the same file again.
 */
internal class ImageTextures {
    // One texture per image and filter: an image drawn crisp in one place and smooth in another
    // samples two textures, since the filter is part of a texture's sampler. So is the mip chain:
    // a smooth image drawn both with and without its mips is uploaded twice.
    private val materials = HashMap<Triple<ImageBitmap, FilterQuality, Boolean>, Material>()

    /**
     * The material that samples [image] with [filterQuality]. [baseLevelOnly] leaves a smooth
     * image without its mip chain, sampling only its full-size level the way a render target is
     * sampled; a crisp image has no mips either way.
     */
    fun materialFor(
        renderer: Renderer,
        image: ImageBitmap,
        filterQuality: FilterQuality = FilterQuality.Low,
        baseLevelOnly: Boolean = false,
    ): Material {
        val filtering = when {
            filterQuality == FilterQuality.None -> TextureFiltering.Nearest
            baseLevelOnly -> TextureFiltering.BaseLevelLinear
            else -> TextureFiltering.Linear
        }
        return materials.getOrPut(Triple(image, filterQuality, filtering == TextureFiltering.BaseLevelLinear)) {
            renderer.createMaterial(texture = TextureAsset(image.pixels, image.width, image.height, filtering = filtering))
        }
    }

    fun dispose() {
        materials.values.forEach(Material::destroy)
        materials.clear()
    }
}
