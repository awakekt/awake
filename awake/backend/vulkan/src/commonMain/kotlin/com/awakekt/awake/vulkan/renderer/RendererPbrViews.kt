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

/** The image views of [pbrTextures]' maps, each uploaded, or the neutral map in place of one it lacks. */
internal fun Renderer.pbrImageViews(pbrTextures: PbrTextureSet?): PbrImageViews = PbrImageViews(
    metallicRoughness = pbrImageView(pbrTextures?.metallicRoughness, Renderer.NEUTRAL_METALLIC_ROUGHNESS),
    normal = pbrImageView(pbrTextures?.normal, Renderer.NEUTRAL_NORMAL),
    occlusion = pbrImageView(pbrTextures?.occlusion, Renderer.NEUTRAL_OCCLUSION),
    emissive = pbrImageView(pbrTextures?.emissive, Renderer.NEUTRAL_EMISSIVE),
)

internal fun Renderer.pbrImageView(asset: TextureAsset?, neutral: TextureAsset): Long =
    if (asset != null) {
        uploadTexture(asset).imageView.handle
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
