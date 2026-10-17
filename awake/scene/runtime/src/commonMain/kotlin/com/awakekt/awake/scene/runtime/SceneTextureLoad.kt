/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.render.texture.TextureAsset

/**
 * Where a sprite or tilemap texture a [SceneAssetResolver] supplies stands: loaded, still loading,
 * or failed. [SceneAssetLibrary.texture] asks on every frame a sprite or tilemap using it is drawn
 * until it has loaded, so a resolver starts loading on the first ask and answers [Loading] until then.
 */
sealed interface SceneTextureLoad {
    /**
     * The decoded pixels, which the frame uploads on first use and keeps.
     *
     * @property image The texture's decoded pixels.
     */
    class Loaded(val image: TextureAsset) : SceneTextureLoad

    /** Not loaded yet: what uses it is not drawn yet, and draws once it has loaded. */
    data object Loading : SceneTextureLoad

    /**
     * It could not be read or decoded: what uses it is not drawn.
     *
     * @property cause Why it failed.
     */
    class Failed(val cause: Throwable) : SceneTextureLoad
}
