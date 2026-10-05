/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.NavMesh

/**
 * A [NavMesh] over one baked [NavGridTile]: search, then smooth.
 *
 * One tile, for a world that is one heightmap. A streamed world uses a streamed navigation grid
 * instead, which holds a tile per world cell and searches across the resident set; both read the same
 * search, so the only difference is where walkability comes from.
 *
 * @property tile The underlying [NavGridTile] providing walkability and grid geometry.
 */
class NavGrid(val tile: NavGridTile) : NavMesh {
    /**
     * Returns a smoothed walkable path between [start] and [end] world positions on [tile].
     *
     * @param start The starting world position.
     * @param end The destination world position.
     * @return An ordered list of waypoint positions from start to end, or an empty list if no path exists.
     */
    override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> =
        tile.smoothPath(tile.findPath(start, end))
}
