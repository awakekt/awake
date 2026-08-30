/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.scene.navigation.NavMesh

/**
 * A [NavMesh] over one baked [NavGridTile]: search, then smooth.
 *
 * One tile, for a world that is one heightmap. A streamed world uses [StreamedNavGrid] instead,
 * which holds a tile per world cell and searches across the resident set; both read the same
 * search, so the only difference is where walkability comes from.
 */
class NavGrid(private val tile: NavGridTile) : NavMesh {
    override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> =
        tile.smoothPath(tile.findPath(start, end))
}
