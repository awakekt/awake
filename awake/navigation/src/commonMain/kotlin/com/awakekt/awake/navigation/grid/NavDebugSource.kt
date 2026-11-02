/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.world.WorldCellCoord

/**
 * A navigation grid and the routes agents walk on it, on the debug panel and as lines over the ground. Its id is
 * [ID], `nav`: `--debug=nav` on desktop, `?debug=nav` on the web.
 *
 * Made over one baked [NavGridTile] or over a [StreamedNavGrid]; the lines are the ones [navGridDebugLines] returns
 * for the same grid and routes.
 *
 * - **Rows**: *Cells*, how many cells of a streamed grid are resident (a single tile has no such row); *Walkable* and
 *   *Blocked*, how many navigation samples an agent can and cannot stand on, over every resident cell; *Routes*, how
 *   many agents have a route with at least one point.
 * - **Lines**: a cross on every blocked sample ([BLOCKED_COLOR], or on a streamed grid alternating with
 *   [BLOCKED_COLOR_ALTERNATE] by cell); each resident cell's outline on a streamed grid; and each route, in its
 *   agent's colour from [ROUTE_COLORS], ending in a larger cross on its goal.
 *
 * `ground` lays the lines on the terrain, as a tile stores walkability and not height, and a route's points carry
 * no height either. Pass `NavGroundHeight { x, z -> heightmap.heightAtWorld(x, z) }` with its off-map `NaN`
 * flattened, or [NavGroundHeight.FLAT] on level ground.
 *
 * `routes` is asked once a frame for the routes to draw. This module can't see who walks a route, so the game hands
 * them in, keeping one list and changing it when a route changes rather than building one each frame.
 *
 * It measures nothing, and writing its lines and rows allocates nothing. A streamed grid's resident cells are read
 * again only after a tile loads or unloads.
 */
class NavDebugSource private constructor(
    private val tile: NavGridTile?,
    private val grid: StreamedNavGrid?,
    ground: NavGroundHeight,
    private val routes: () -> List<AgentRoute>,
) : DebugSource {
    /** A source over one baked [tile], laid on [ground], drawing the `routes` the game hands in. */
    constructor(
        tile: NavGridTile,
        ground: NavGroundHeight = NavGroundHeight.FLAT,
        routes: () -> List<AgentRoute> = NO_ROUTES,
    ) : this(tile, null, ground, routes)

    /** A source over the resident cells of a streamed [grid], laid on [ground], drawing the `routes` handed in. */
    constructor(
        grid: StreamedNavGrid,
        ground: NavGroundHeight = NavGroundHeight.FLAT,
        routes: () -> List<AgentRoute> = NO_ROUTES,
    ) : this(null, grid, ground, routes)

    override val id: String = ID
    override val title: String = "Navigation"

    private val writer = NavDebugWriter(ground)

    // A streamed grid's resident tiles as of the last time they changed, in a fixed order, and their counts.
    private var resident: Map<WorldCellCoord, NavGridTile>? = null
    private val coords = ArrayList<WorldCellCoord>()
    private val tiles = ArrayList<NavGridTile>()
    private var walkable = 0
    private var samples = 0

    override fun rows(out: DebugRows) {
        refresh()
        if (grid != null) out.row("Cells", coords.size)
        out.row("Walkable", walkable)
        out.row("Blocked", samples - walkable)
        val routes = routes()
        var routed = 0
        for (index in routes.indices) if (routes[index].path.isNotEmpty()) routed++
        out.row("Routes", routed)
    }

    override fun lines(out: DebugLines) {
        if (tile != null) {
            writer.blockedMarkers(out, tile, originX = 0f, originZ = 0f, color = BLOCKED_COLOR)
        } else if (grid != null) {
            refresh()
            val cellSize = grid.worldCellSize
            for (index in coords.indices) writer.residentCell(out, coords[index], tiles[index], cellSize)
        }
        writer.routes(out, routes())
    }

    /** Reads the counts, and a streamed grid's resident tiles when they changed since the last read. */
    private fun refresh() {
        if (tile != null) {
            walkable = tile.walkableCount
            samples = tile.width * tile.depth
        } else {
            val now = grid?.residentTiles
            if (now != null && now !== resident) reread(now)
        }
    }

    /** Takes [now] as the resident tiles, and counts their samples. */
    private fun reread(now: Map<WorldCellCoord, NavGridTile>) {
        resident = now
        coords.clear()
        tiles.clear()
        walkable = 0
        samples = 0
        for ((coord, cellTile) in now) {
            coords += coord
            tiles += cellTile
            walkable += cellTile.walkableCount
            samples += cellTile.width * cellTile.depth
        }
    }

    /** The source's id. */
    companion object {
        /** What turns it on: `--debug=nav`, `?debug=nav`. */
        const val ID: String = "nav"

        private val NONE: List<AgentRoute> = emptyList()
        private val NO_ROUTES: () -> List<AgentRoute> = { NONE }
    }
}
