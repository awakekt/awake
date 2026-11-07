/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.assets.AssetImages
import com.awakekt.awake.compose.assets.LocalAssetImages
import com.awakekt.awake.compose.assets.LocalAssetSource
import com.awakekt.awake.compose.runtime.ProvidedValue
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.io.AssetSource
import kotlinx.coroutines.CoroutineScope

/**
 * The asset source and per-scene image cache [SceneAppLifecycleRuntime] provides to the scene's UI,
 * with the two locals built once per change rather than once per frame.
 */
internal class SceneUiAssets {
    var source: AssetSource? = null
        set(value) {
            if (field === value) return
            field = value
            images?.reset()
            images = null
            sourceLocal = LocalAssetSource provides value
            imagesLocal = NO_IMAGES
        }

    /** The images the scene's canvas names: each new scene's cache starts from them. */
    var seed: Map<String, ImageBitmap> = emptyMap()
        set(value) {
            field = value
            images?.reset(value)
        }

    var images: AssetImages? = null
        private set

    var sourceLocal: ProvidedValue<AssetSource?> = NO_SOURCE
        private set

    var imagesLocal: ProvidedValue<AssetImages?> = NO_IMAGES
        private set

    /**
     * What the scene canvas draws its images from: the cache, which reads a path the first time an
     * element asks for it, or [seed] alone while there is none.
     */
    val canvasImages: Map<String, ImageBitmap> get() = images?.asMap() ?: seed

    /** Whether a frame must [prepare] the cache: there is a [source] and no cache for it yet. */
    val needsImages: Boolean get() = images == null && source != null

    /** Makes the cache over [source] in [scope], started from [seed]. */
    fun prepare(scope: CoroutineScope) {
        val files = source ?: return
        val cache = AssetImages(files, scope).also { it.reset(seed) }
        images = cache
        imagesLocal = LocalAssetImages provides cache
    }

    private companion object {
        val NO_SOURCE: ProvidedValue<AssetSource?> = LocalAssetSource provides null
        val NO_IMAGES: ProvidedValue<AssetImages?> = LocalAssetImages provides null
    }
}
