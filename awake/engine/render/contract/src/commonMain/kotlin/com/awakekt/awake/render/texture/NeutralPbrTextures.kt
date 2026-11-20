/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.texture

/**
 * The 1x1 textures a material binds for a PBR channel it has no map for. Each leaves its factor as
 * the whole value, as glTF does when a map is absent: metallic-roughness is white (roughness in G,
 * metallic in B, both 1), occlusion white, a flat normal, and emission white, so a material with no
 * emissive map glows by its emissive factor alone. Every backend binds these.
 */
object NeutralPbrTextures {
    /** 1x1 white texture providing unit metallic (B=1.0) and roughness (G=1.0). */
    val MetallicRoughness = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)

    /** 1x1 tangent-space flat normal vector texture (128, 128, 255, 255). */
    val Normal = TextureAsset(byteArrayOf(-128, -128, -1, -1), 1, 1)

    /** 1x1 white ambient occlusion texture providing full exposure. */
    val Occlusion = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)

    /**
     * 1x1 white emissive texture: the emissive factor stands alone, as glTF specifies for a material
     * with no `emissiveTexture`. It was black, so a textured mesh with a factor and no map didn't glow,
     * while a skinned one glowed by the factor (#1829).
     */
    val Emissive = TextureAsset(byteArrayOf(-1, -1, -1, -1), 1, 1)
}
