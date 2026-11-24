/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.texture.Texture

/**
 * The image views of [pbrTextures]' maps, each uploaded, or the neutral map in place of one it lacks.
 * When [ownedOut] is provided, each material-owned uploaded texture is appended to it while shared
 * neutral fallback textures are omitted.
 */
internal fun Renderer.pbrImageViews(
    pbrTextures: PbrTextureSet?,
    ownedOut: MutableList<Texture>? = null,
): PbrImageViews = PbrImageViews(
    metallicRoughness = pbrImageView(pbrTextures?.metallicRoughness, Renderer.NEUTRAL_METALLIC_ROUGHNESS, ownedOut),
    normal = pbrImageView(pbrTextures?.normal, Renderer.NEUTRAL_NORMAL, ownedOut),
    occlusion = pbrImageView(pbrTextures?.occlusion, Renderer.NEUTRAL_OCCLUSION, ownedOut),
    emissive = pbrImageView(pbrTextures?.emissive, Renderer.NEUTRAL_EMISSIVE, ownedOut),
)

internal fun Renderer.pbrImageView(asset: TextureAsset?, neutral: TextureAsset): Long =
    pbrImageView(asset, neutral, owned = null)

private fun Renderer.pbrImageView(asset: TextureAsset?, neutral: TextureAsset, owned: MutableList<Texture>?): Long =
    if (asset != null) {
        uploadTexture(asset).also { owned?.add(it) }.imageView.handle
    } else {
        textureResources.neutral(neutral) {
            Texture(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                neutral.data,
                neutral.width,
                neutral.height,
            )
        }.imageView.handle
    }
