/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.terrain.TerrainSurface

/**
 * A terrain surface drawn from [palette]: [TerrainLayersShaders] with its layer array, layer table
 * and control map bound.
 *
 * @param palette The layers, in the order the control map indexes them.
 * @param albedo The palette's layers from [packLayerArray], in palette order.
 * @param control Which layers cover each texel; aligned with the terrain's heightmap.
 * @param lightmap Baked lighting aligned with [control], or none to light by the scene alone.
 */
fun terrainLayersSurface(
    palette: TerrainLayerPalette,
    albedo: TextureAsset,
    control: TerrainControlMap,
    lightmap: TerrainLightmap = TerrainLightmap.Neutral,
): TerrainSurface = terrainLayersSurface(palette, albedo, control, lightmap, emptyMap(), TerrainQuality.Standard)

/**
 * [terrainLayersSurface] with optional immutable [textureKeys] (such as [TerrainLayerArrayKey] for
 * [LAYER_ALBEDO_BINDING]) so multiple terrains sharing one palette reuse a single uploaded GPU
 * texture array.
 */
fun terrainLayersSurface(
    palette: TerrainLayerPalette,
    albedo: TextureAsset,
    control: TerrainControlMap,
    lightmap: TerrainLightmap = TerrainLightmap.Neutral,
    textureKeys: Map<Int, Any>,
): TerrainSurface = terrainLayersSurface(palette, albedo, control, lightmap, textureKeys, TerrainQuality.Standard)

/**
 * [terrainLayersSurface] configured for [quality] and optional [includeSurfaceDeposit], preserving
 * shared [textureKeys] and matching [control]'s slot count.
 */
@Suppress("LongParameterList")
fun terrainLayersSurface(
    palette: TerrainLayerPalette,
    albedo: TextureAsset,
    control: TerrainControlMap,
    lightmap: TerrainLightmap = TerrainLightmap.Neutral,
    textureKeys: Map<Int, Any> = emptyMap(),
    quality: TerrainQuality,
    includeSurfaceDeposit: Boolean = quality == TerrainQuality.Standard,
): TerrainSurface {
    val issues = palette.validate()
    require(issues.isEmpty()) { "Invalid terrain layer palette: ${issues.joinToString("; ")}" }
    require(albedo.layerCount >= palette.layers.size) {
        "The albedo array has ${albedo.layerCount} layers for a ${palette.layers.size}-layer palette."
    }
    val highest = control.highestLayer()
    require(highest < palette.layers.size) {
        "The control map uses layer $highest; the palette has ${palette.layers.size} layers."
    }
    return TerrainSurface(
        shaders = terrainLayersShaderSet(
            slots = control.slots,
            quality = quality,
            paged = false,
            includeSurfaceDeposit = includeSurfaceDeposit,
        ),
        textures = mapOf(
            LAYER_ALBEDO_BINDING to albedo,
            LAYER_TABLE_BINDING to layerTable(palette),
            CONTROL_INDICES_BINDING to control.indicesTexture(),
            CONTROL_WEIGHTS_BINDING to control.weightsTexture(),
            LIGHTMAP_BINDING to lightmap.texture(),
        ),
        // Bind the shared layer sampler to the mipmapped, anisotropic albedo array rather than the
        // single-level heightmap sampler.
        samplerTextures = mapOf(LAYER_SAMPLER_BINDING to LAYER_ALBEDO_BINDING),
        textureKeys = textureKeys,
    )
}
