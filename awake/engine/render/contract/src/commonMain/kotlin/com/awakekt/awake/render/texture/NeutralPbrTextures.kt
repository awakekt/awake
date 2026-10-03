/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * The 1x1 textures a material binds for a PBR channel it has no map for. Each leaves its factor as
 * the whole value, as glTF does when a map is absent: metallic-roughness is white (roughness in G,
 * metallic in B, both 1), occlusion white, a flat normal, and no emission. Every backend binds these.
 */
object NeutralPbrTextures {
    val MetallicRoughness = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
    val Normal = TextureAsset(byteArrayOf(-128, -128, -1, -1), 1, 1)
    val Occlusion = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
    val Emissive = TextureAsset(byteArrayOf(0, 0, 0, -1), 1, 1)
}
