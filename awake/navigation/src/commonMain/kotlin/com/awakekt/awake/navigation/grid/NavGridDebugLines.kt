/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.grid

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.scene.world.WorldCellCoord

/**
 * World-space wireframes for what navigation is actually reasoning about: every sample the bake
 * rejected, drawn as a cross, and the route each chaser is currently walking.
 *
 * Returns lines rather than drawing them, matching `debugVisualizationLines` and for the reason
 * its own doc gives: `Renderer.drawDebugLines` replaces the frame's line buffer instead of
 * appending, so a caller with more than one source of debug lines has to merge them into a single
 * call or silently lose all but the last.
 *
 * [surfaceAt] lifts flat data onto the ground. The tile stores walkability, not height, and a
 * route's waypoints carry no Y at all, so both would otherwise draw at y=0 and vanish under
 * terrain. Pass `Heightmap::heightAtWorld` with its off-map NaN flattened, or `{ _, _ -> 0f }` on
 * level ground.
 *
 * The markers are the *grid's* opinion rather than the mesh's, which is the point: a bake that is
 * subtly wrong shows up here instead of hiding behind terrain that looks fine.
 *
 * Colours and sizes are fixed. A caller wanting its own builds the lines itself — this is a
 * diagnostic, and a palette parameter for one consumer would be a knob nobody turns.
 */
fun navGridDebugLines(
    tile: NavGridTile,
    surfaceAt: (x: Float, z: Float) -> Float,
    routes: List<AgentRoute> = emptyList(),
): List<LineSegment> = NavGridDebugSink(surfaceAt).run {
    blockedMarkers(tile, originX = 0f, originZ = 0f, color = BLOCKED_COLOR)
    routes(routes)
    lines
}

/**
 * The same picture for a [StreamedNavGrid], plus what is specific to streaming: where the cells
 * are, and which ones are loaded.
 *
 * Three things are drawn that the single-tile overload has no use for. Each resident cell is
 * outlined, so the residency the grid is searching is visible rather than inferred. Blocked
 * markers alternate between two shades on a checkerboard of cells, which is what makes a tile
 * baked at the wrong offset obvious — a misplaced tile breaks the pattern. And routes are coloured
 * per chaser, because the interesting failure in a streamed world is *one* agent pathing oddly
 * near a boundary, which is unreadable when every route is the same colour.
 *
 * Only what is loaded is drawn, which is the point — the edge of the markers is the edge of what
 * navigation can answer questions about.
 */
fun navGridDebugLines(
    grid: StreamedNavGrid,
    surfaceAt: (x: Float, z: Float) -> Float,
    routes: List<AgentRoute> = emptyList(),
): List<LineSegment> = NavGridDebugSink(surfaceAt).run {
    grid.residentCells.forEach { coord ->
        val tile = grid.tileAt(coord) ?: return@forEach
        blockedMarkers(
            tile,
            originX = coord.x * grid.worldCellSize,
            originZ = coord.z * grid.worldCellSize,
            color = blockedColorFor(coord),
        )
        cellOutline(coord, grid.worldCellSize)
    }
    routes(routes)
    lines
}

/**
 * Collects one overlay's segments.
 *
 * A sink rather than free functions passing `lines` and `surfaceAt` down every call: those two
 * belong to the whole drawing, not to any one shape, and threading them through six-argument
 * helpers is how a marker ends up on the wrong list or at the wrong height.
 */
open class NavGridDebugSink(val surfaceAt: (Float, Float) -> Float) {
    private val _lines: MutableList<LineSegment> = ArrayList()
    val lines: List<LineSegment> get() = _lines

    /** Every sample of [tile] an agent cannot stand on, offset to that tile's place in the world. */
    fun blockedMarkers(tile: NavGridTile, originX: Float, originZ: Float, color: Color) {
        for (z in 0 until tile.depth) {
            for (x in 0 until tile.width) {
                if (tile.isWalkable(x, z)) continue
                cross(originX + tile.worldX(x), originZ + tile.worldZ(z), MARKER_ARM, MARKER_LIFT, color)
            }
        }
    }

    /**
     * Every agent's route, one colour each, ending in a cross on the goal.
     *
     * The colour is picked by entity id rather than by iteration order: a family's order is not
     * promised to be stable across frames, and a route that changes colour when an unrelated
     * entity is destroyed is a diagnostic that lies.
     */
    fun routes(routes: List<AgentRoute>) {
        routes.forEach { (entity, route) ->
            if (route.isEmpty()) return@forEach
            val color = routeColorFor(entity)
            for (index in 0 until route.size - 1) {
                _lines += LineSegment(route[index].onSurface(), route[index + 1].onSurface(), color)
            }
            cross(route.last().x, route.last().z, GOAL_ARM, ROUTE_LIFT, color)
        }
    }

    /** The cell's footprint at ground level, so residency is visible and not inferred from markers. */
    fun cellOutline(coord: WorldCellCoord, cellSize: Float) {
        val minX = coord.x * cellSize
        val minZ = coord.z * cellSize
        val maxX = minX + cellSize
        val maxZ = minZ + cellSize
        edge(minX, minZ, maxX, minZ)
        edge(maxX, minZ, maxX, maxZ)
        edge(maxX, maxZ, minX, maxZ)
        edge(minX, maxZ, minX, minZ)
    }

    fun cross(worldX: Float, worldZ: Float, arm: Float, lift: Float, color: Color) {
        val y = surfaceAt(worldX, worldZ) + lift
        _lines += LineSegment(
            Vec3f(worldX - arm, y, worldZ - arm),
            Vec3f(worldX + arm, y, worldZ + arm),
            color,
        )
        _lines += LineSegment(
            Vec3f(worldX - arm, y, worldZ + arm),
            Vec3f(worldX + arm, y, worldZ - arm),
            color,
        )
    }

    /**
     * One outline edge, in segments rather than a single line: an edge crossing a hill would
     * otherwise sink through it, since the two endpoints are the only heights a straight segment
     * knows about.
     */
    private fun edge(startX: Float, startZ: Float, endX: Float, endZ: Float) {
        for (step in 0 until OUTLINE_SEGMENTS) {
            _lines += LineSegment(
                point(startX, startZ, endX, endZ, step.toFloat() / OUTLINE_SEGMENTS),
                point(startX, startZ, endX, endZ, (step + 1).toFloat() / OUTLINE_SEGMENTS),
                CELL_OUTLINE_COLOR,
            )
        }
    }

    private fun point(startX: Float, startZ: Float, endX: Float, endZ: Float, fraction: Float): Vec3f {
        val x = startX + (endX - startX) * fraction
        val z = startZ + (endZ - startZ) * fraction
        return Vec3f(x, surfaceAt(x, z) + OUTLINE_LIFT, z)
    }

    protected fun Vec3f.onSurface(): Vec3f = Vec3f(x, surfaceAt(x, z) + ROUTE_LIFT, z)
}

/** Checkerboard by cell, so a tile drawn at the wrong offset breaks a pattern instead of hiding. */
fun blockedColorFor(coord: WorldCellCoord): Color =
    if ((coord.x + coord.z) % 2 == 0) BLOCKED_COLOR else BLOCKED_COLOR_ALTERNATE

/** Stable per entity: same agent, same colour, for as long as it exists. */
fun routeColorFor(entity: Entity): Color =
    ROUTE_COLORS[(entity.id % ROUTE_COLORS.size + ROUTE_COLORS.size) % ROUTE_COLORS.size]

private const val MARKER_ARM = 0.35f
private const val GOAL_ARM = 0.6f
private const val MARKER_LIFT = 0.05f
private const val ROUTE_LIFT = 0.35f
private const val OUTLINE_LIFT = 0.15f

/** Enough to follow rolling ground without turning one cell border into hundreds of segments. */
private const val OUTLINE_SEGMENTS = 8

val BLOCKED_COLOR: Color = Color(r = 0.95f, g = 0.25f, b = 0.2f)
val BLOCKED_COLOR_ALTERNATE: Color = Color(r = 0.85f, g = 0.45f, b = 0.15f)
private val CELL_OUTLINE_COLOR = Color(r = 0.25f, g = 0.55f, b = 0.95f)

/** Distinguishable at a glance and from each other, which is the only requirement. */
val ROUTE_COLORS: List<Color> = listOf(
    Color(r = 1f, g = 0.85f, b = 0.1f),
    Color(r = 0.2f, g = 0.95f, b = 0.6f),
    Color(r = 0.95f, g = 0.4f, b = 0.85f),
    Color(r = 0.4f, g = 0.75f, b = 1f),
)
