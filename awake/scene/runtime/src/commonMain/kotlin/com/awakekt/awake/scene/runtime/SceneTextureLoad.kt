/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.texture.GpuImageSource
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.asGpuImageSource
import com.awakekt.awake.render.texture.toTextureAsset

/**
 * Where a sprite or tilemap texture a [SceneAssetResolver] supplies stands: loaded, still loading,
 * or failed. [SceneAssetLibrary.texture] asks on every frame a sprite or tilemap using it is drawn
 * until it has loaded, so a resolver starts loading on the first ask and answers [Loading] until then.
 */
sealed interface SceneTextureLoad {
    /**
     * The decoded texture, which the frame uploads on first use and keeps: either CPU pixels, or a
     * [GpuImageSource] such as a browser's decoded image, which WebGPU copies without a readback
     * (awakekt/awake#1184).
     */
    class Loaded private constructor(private val asset: TextureAsset?, private val decoded: GpuImageSource?) : SceneTextureLoad {
        /** Decoded pixels on the CPU. */
        constructor(image: TextureAsset) : this(image, null)

        /** A decoded image the frame uploads with `createImageMaterial`, on the GPU where it can. */
        constructor(source: GpuImageSource) : this(null, source)

        /** The texture's pixels on the CPU: read back from [source] on first use when it was loaded as one. */
        val image: TextureAsset by lazy { asset ?: requireNotNull(decoded).toTextureAsset() }

        /** The texture as a [GpuImageSource], what the frame uploads. */
        val source: GpuImageSource get() = decoded ?: requireNotNull(asset).asGpuImageSource()
    }

    /** Not loaded yet: what uses it is not drawn yet, and draws once it has loaded. */
    data object Loading : SceneTextureLoad

    /**
     * It could not be read or decoded: what uses it is not drawn.
     *
     * @property cause Why it failed.
     */
    class Failed(val cause: Throwable) : SceneTextureLoad
}
