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
    // samples two textures, since the filter is part of a texture's sampler.
    private val materials = HashMap<Pair<ImageBitmap, FilterQuality>, Material>()

    fun materialFor(renderer: Renderer, image: ImageBitmap, filterQuality: FilterQuality = FilterQuality.Low): Material =
        materials.getOrPut(image to filterQuality) {
            val filtering = if (filterQuality == FilterQuality.None) TextureFiltering.Nearest else TextureFiltering.Linear
            renderer.createMaterial(texture = TextureAsset(image.pixels, image.width, image.height, filtering = filtering))
        }

    fun dispose() {
        materials.values.forEach(Material::destroy)
        materials.clear()
    }
}
