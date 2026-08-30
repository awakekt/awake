/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.renderer

import io.github.awakelab.awake.render.texture.TextureAsset

/** Owns renderer-created sampled textures and the small set of cached asset textures. */
internal class TextureResourceManager<T>(private val destroyResource: (T) -> Unit) {
    private val textures = mutableListOf<T>()
    private val neutralTextures = mutableMapOf<TextureAsset, T>()

    fun register(texture: T): T = texture.also { textures += it }

    fun release(texture: T) {
        if (textures.remove(texture)) destroyResource(texture)
    }

    fun neutral(asset: TextureAsset, create: () -> T): T =
        neutralTextures.getOrPut(asset) { register(create()) }

    fun destroy() {
        textures.forEach(destroyResource)
        textures.clear()
        neutralTextures.clear()
    }
}
