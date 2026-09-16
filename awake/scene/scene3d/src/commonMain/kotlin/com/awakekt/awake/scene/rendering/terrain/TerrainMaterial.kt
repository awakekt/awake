/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.render.texture.TextureAsset

/**
 * Surface material configuration for multi-layer terrain shading.
 *
 * Provides optional satellite tint / colormap and diffuse textures corresponding to the
 * 4 channels of a terrain splat weightmap.
 *
 * @property colorMap Optional global colormap or satellite tint texture.
 * @property diffuseLayers Diffuse ground textures (up to 4 channels: R, G, B, A).
 * @property tilingScale UV tiling frequency for diffuse layers.
 * @property roughness Surface roughness value for PBR shading.
 */
data class TerrainMaterial(
    val colorMap: TextureAsset? = null,
    val diffuseLayers: List<TextureAsset> = emptyList(),
    val tilingScale: Float = 16.0f,
    val roughness: Float = 0.9f,
)
