/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.navigation.grid

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Finds the cheapest walkable route between two samples, or an empty list when none exists.
 *
 * 8-connected A* with an octile heuristic. **Not** Jump Point Search: JPS's pruning is only sound
 * on a uniform-cost grid, and this grid is expected to gain slope-weighted costs, at which point
 * jump points stop being optimal while still returning a plausible-looking path. Revisit only if
 * the cost model is still uniform then.
 *
 * Waypoints carry world X and Z; **Y is left at zero**. The tile stores walkability, not height,
 * so a caller that needs a world Y resolves it through [Heightmap.heightAtWorld]. The current
 * steering consumer only reads X and Z.
 *
 * The path is not smoothed — it steps between sample centres and looks like a staircase. Pass it
 * through [smoothPath] for a walkable route with the redundant waypoints removed.
 */
fun NavGridTile.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> =
    TileField(this).findPath(startX, startZ, goalX, goalZ)

/**
 * Rounds [start] and [goal] to their nearest samples and finds a route between them.
 *
 * Positions off the tile round to a sample outside it, which yields an empty path rather than
 * being clamped onto the edge — a caller asking to path from somewhere the grid does not cover
 * should hear "no", not get a route from the nearest corner.
 */
fun NavGridTile.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = TileField(this).findPath(start, goal)

/**
 * Drops waypoints that the ones around them can already see past, turning A*'s staircase into
 * straight runs between the corners that actually matter.
 *
 * This is the grid's answer to the funnel algorithm, which needs a corridor of convex polygons a
 * grid does not have. Each waypoint's Y is carried through untouched, so smoothing after resolving
 * heights is as valid as smoothing before.
 *
 * Waypoints are assumed to sit on tile samples, which is what [findPath] emits.
 */
fun NavGridTile.smoothPath(path: List<Vec3f>): List<Vec3f> = TileField(this).smoothPath(path)

/** [findPath] in sample coordinates, over any field. Endpoints off the field yield no path. */
fun NavField.findPath(startX: Int, startZ: Int, goalX: Int, goalZ: Int): List<Vec3f> {
    if (!isWalkable(startX, startZ) || !isWalkable(goalX, goalZ)) return emptyList()
    return NavFieldSearch(this).run(startX, startZ, goalX, goalZ)
}

/** [findPath] in world coordinates, over any field. */
fun NavField.findPath(start: Vec3f, goal: Vec3f): List<Vec3f> = findPath(
    startX = sampleAt(start.x, originX),
    startZ = sampleAt(start.z, originZ),
    goalX = sampleAt(goal.x, originX),
    goalZ = sampleAt(goal.z, originZ),
)

private fun NavField.sampleAt(world: Float, origin: Float): Int =
    ((world - origin) / sampleSize).roundToInt()

/** [smoothPath] over any field. */
fun NavField.smoothPath(path: List<Vec3f>): List<Vec3f> {
    if (path.size <= 2) return path
    val smoothed = ArrayList<Vec3f>(path.size)
    smoothed.add(path.first())
    var anchorX = sampleAt(path.first().x, originX)
    var anchorZ = sampleAt(path.first().z, originZ)
    for (i in 2 until path.size) {
        if (hasLineOfSight(anchorX, anchorZ, sampleAt(path[i].x, originX), sampleAt(path[i].z, originZ))) continue
        val corner = path[i - 1]
        smoothed.add(corner)
        anchorX = sampleAt(corner.x, originX)
        anchorZ = sampleAt(corner.z, originZ)
    }
    smoothed.add(path.last())
    return smoothed
}

/**
 * Whether the segment between two sample centres crosses only walkable samples.
 *
 * A supercover traversal, visiting every sample the segment touches rather than the one-per-column
 * set a plain Bresenham line would draw: a thin diagonal gap is exactly what a drawing line skips
 * and a walking agent cannot fit through.
 *
 * **The corner rule has to match [findPath]'s.** When the segment passes exactly through the point
 * shared by four samples — which happens on every 45-degree run — both orthogonal samples must be
 * walkable. Without that, smoothing would straighten a path through a corner the search had
 * deliberately refused to cut, and the refusal in the search would be decorative.
 */
private fun NavField.hasLineOfSight(startX: Int, startZ: Int, endX: Int, endZ: Int): Boolean {
    var x = startX
    var z = startZ
    var doubleX = abs(endX - startX)
    var doubleZ = abs(endZ - startZ)
    val stepX = if (endX > startX) 1 else -1
    val stepZ = if (endZ > startZ) 1 else -1
    var remaining = doubleX + doubleZ
    var error = doubleX - doubleZ
    doubleX *= 2
    doubleZ *= 2

    var clear = true
    while (remaining > 0 && clear) {
        when {
            error > 0 -> {
                x += stepX
                error -= doubleZ
                remaining--
            }
            error < 0 -> {
                z += stepZ
                error += doubleX
                remaining--
            }
            else -> {
                clear = isWalkable(x + stepX, z) && isWalkable(x, z + stepZ)
                x += stepX
                z += stepZ
                error += doubleX - doubleZ
                remaining -= 2
            }
        }
        if (clear) clear = isWalkable(x, z)
    }
    return clear
}
