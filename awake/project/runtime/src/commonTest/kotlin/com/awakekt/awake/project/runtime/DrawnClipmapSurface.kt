/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import kotlin.math.floor

/**
 * The height the clipmap's level 0 draws at [x], [z], for a terrain at the origin: what a test compares
 * a collider against, written out from what the renderer does rather than from the collider's code.
 *
 * Level 0 puts a vertex every [TerrainClipmapConfig.baseSpacing] on the grid that starts at the map's
 * first sample, displaced by the heightmap sampled bilinearly ([Heightmap.heightAtWorld]); the core mesh
 * splits each cell along its `(x, z)-(x+1, z+1)` diagonal (`gridTriangleIndices`). Between vertices the
 * height is the plane of the triangle the point falls in.
 */
internal fun Heightmap.drawnHeight(x: Float, z: Float, config: TerrainClipmapConfig = TerrainClipmapConfig()): Float {
    val spacing = config.baseSpacing
    val cellX = floor((x - minX) / spacing)
    val cellZ = floor((z - minZ) / spacing)
    val x0 = minX + cellX * spacing
    val z0 = minZ + cellZ * spacing
    val u = (x - x0) / spacing
    val v = (z - z0) / spacing
    val h00 = heightAtWorld(x0, z0)
    val h11 = heightAtWorld(x0 + spacing, z0 + spacing)
    return if (u >= v) {
        val h10 = heightAtWorld(x0 + spacing, z0)
        h00 + (h10 - h00) * u + (h11 - h10) * v
    } else {
        val h01 = heightAtWorld(x0, z0 + spacing)
        h00 + (h01 - h00) * v + (h11 - h01) * u
    }
}
