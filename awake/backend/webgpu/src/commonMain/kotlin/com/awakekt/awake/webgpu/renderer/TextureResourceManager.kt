/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.texture.TextureAsset

/** Owns renderer-created sampled textures and the small set of cached asset textures. */
internal class TextureResourceManager<T>(
    private val sizeOf: (T) -> Long = { 0L },
    private val destroyResource: (T) -> Unit,
) {
    // A neutral creator can delegate to the renderer's upload path, which registers the
    // texture before returning it. Both paths share one owner and must release it only once.
    private val textures = mutableSetOf<T>()
    private val neutralTextures = mutableMapOf<TextureAsset, T>()
    private var trackedBytes = 0L

    internal val count: Int get() = textures.size
    internal val totalBytes: Long get() = trackedBytes

    fun register(texture: T): T = texture.also {
        if (textures.add(it)) trackedBytes += sizeOf(it)
    }

    fun release(texture: T) {
        if (textures.remove(texture)) {
            trackedBytes -= sizeOf(texture)
            destroyResource(texture)
        }
    }

    fun neutral(asset: TextureAsset, create: () -> T): T =
        neutralTextures.getOrPut(asset) { register(create()) }

    fun destroy() {
        textures.forEach(destroyResource)
        textures.clear()
        neutralTextures.clear()
        trackedBytes = 0L
    }
}
