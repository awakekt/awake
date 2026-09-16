/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.terrain

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.splat.TerrainSplatWeightMap

/**
 * First-class ECS component representing an open-world multi-texture terrain landscape.
 *
 * Which textures the weightmap's channels resolve to is authored world policy and belongs to
 * the consuming game pack (see D28) -- this component carries the neutral terrain data only.
 *
 * @property heightmap Continuous elevation heightmap.
 * @property splatMap 4-Channel RGBA splat weightmap for multi-layer texture blending.
 * @property tilingScale World-space UV tiling frequency for diffuse textures.
 * @property clipmapConfig Concentric LOD ring configuration for dynamic camera tracking.
 * @property isVisible Visibility toggle for terrain render passes.
 * @property material Optional surface material containing diffuse layers and colormap textures.
 * @property revision Invalidation counter incremented when height samples or splat weights are modified.
 */
data class TerrainComponent(
    var heightmap: Heightmap,
    var splatMap: TerrainSplatWeightMap? = null,
    var tilingScale: Float = 16.0f,
    val clipmapConfig: TerrainClipmapConfig = TerrainClipmapConfig(),
    var isVisible: Boolean = true,
    var material: TerrainMaterial? = null,
    var revision: Int = 0,
)
