/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.withSolidOpaque
import com.awakekt.awake.terrain.TerrainSurface

/**
 * Fragment shading quality tier for multi-layer terrain surfaces.
 */
enum class TerrainQuality {
    /**
     * Full desktop terrain shading: 4-tap bilinear control-map merging, height-based blend pass,
     * 4-tap paged lightmap seam filtering, and dynamic weather surface deposits.
     */
    Standard,

    /**
     * Mobile-oriented lean terrain shading: single nearest/center control texel fetch (2 fetches
     * instead of 8, zero 16-way slot merge), single-pass linear layer blend, `exp2` inverse tiling,
     * single-tap paged lightmap, fast polynomial/sqrt sRGB transforms, and optional surface deposit
     * disabled by default.
     */
    Fast,
}

/** Lean [TerrainQuality.Fast] shader set for 4-slot non-paged terrain control maps, with Early-Z core companion. */
val TerrainLayersFastShaders: ShaderSet by lazy {
    aslShaderSet { terrainLayersFastShader(it) }
        .withSolidOpaque(aslShaderSet { terrainLayersFastCoreShader(it) })
}

/** Lean [TerrainQuality.Fast] shader set for 4-slot paged terrain control maps, with Early-Z core companion. */
val PagedTerrainLayersFastShaders: ShaderSet by lazy {
    aslShaderSet { pagedTerrainLayersFastShader(it) }
        .withSolidOpaque(aslShaderSet { pagedTerrainLayersFastCoreShader(it) })
}

/**
 * Returns the layered terrain [ShaderSet] (with discard-free Level-0 core companion) for [quality],
 * [slots] (`4` or `8`), [paged] streaming mode, and optional [includeSurfaceDeposit].
 */
fun terrainLayersShaderSet(
    slots: Int = CONTROL_SLOTS,
    quality: TerrainQuality = TerrainQuality.Standard,
    paged: Boolean = false,
    includeSurfaceDeposit: Boolean = quality == TerrainQuality.Standard,
): ShaderSet {
    require(slots == CONTROL_SLOTS || slots == MAX_CONTROL_SLOTS) {
        "Terrain control slots must be $CONTROL_SLOTS or $MAX_CONTROL_SLOTS; was $slots."
    }
    return when (quality) {
        TerrainQuality.Standard -> standardLayersShaderSet(slots, paged)
        TerrainQuality.Fast -> fastLayersShaderSet(slots, paged, includeSurfaceDeposit)
    }
}

private fun standardLayersShaderSet(slots: Int, paged: Boolean): ShaderSet = when {
    !paged && slots == CONTROL_SLOTS -> TerrainLayersShaders
    !paged && slots == MAX_CONTROL_SLOTS -> WideTerrainLayersShaders
    else -> aslShaderSet { terrainLayersShader(it, slots, paged = true) }
        .withSolidOpaque(aslShaderSet { terrainLayersShader(it, slots, paged = true, allowDiscard = false) })
}

private fun fastLayersShaderSet(slots: Int, paged: Boolean, includeSurfaceDeposit: Boolean): ShaderSet {
    if (slots == CONTROL_SLOTS && !includeSurfaceDeposit) {
        return if (paged) PagedTerrainLayersFastShaders else TerrainLayersFastShaders
    }
    return aslShaderSet {
        terrainLayersFastShader(it, slots, paged = paged, allowDiscard = true, includeSurfaceDeposit = includeSurfaceDeposit)
    }.withSolidOpaque(
        aslShaderSet {
            terrainLayersFastShader(it, slots, paged = paged, allowDiscard = false, includeSurfaceDeposit = includeSurfaceDeposit)
        },
    )
}

/**
 * Returns a copy of this layered [TerrainSurface] configured for [quality], preserving all bound
 * layer arrays, tables, control maps, lightmaps, sampler overrides, and shared [TerrainSurface.textureKeys].
 */
fun TerrainSurface.withTerrainQuality(
    quality: TerrainQuality,
    slots: Int = CONTROL_SLOTS,
    includeSurfaceDeposit: Boolean = quality == TerrainQuality.Standard,
): TerrainSurface = TerrainSurface(
    shaders = terrainLayersShaderSet(
        slots = slots,
        quality = quality,
        paged = false,
        includeSurfaceDeposit = includeSurfaceDeposit,
    ),
    textures = textures,
    samplerTextures = samplerTextures,
    textureKeys = textureKeys,
)
