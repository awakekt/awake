/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Whether an agent needing [required] clearance can stand at sample ([x], [z]).
 */
internal fun NavSurface.standable(x: Int, z: Int, required: Int): Boolean = clearance(x, z) >= required

/** The sample nearest world [coordinate] along an axis starting at [origin]. */
internal fun NavSurface.sampleOf(coordinate: Float, origin: Float): Int = ((coordinate - origin) / sampleSize).roundToInt()

/**
 * Drops, in place, every waypoint of [path] the one before it can already see past, turning the
 * search's staircase into straight runs between the corners that matter. The path's ends stay.
 *
 * Waypoints sit on sample centres, which is what the search writes. Each kept waypoint is copied to
 * an index no later than its own, so compacting needs no second buffer.
 */
internal fun NavSurface.smooth(path: NavPath, required: Int) {
    if (path.size <= 2) return
    var write = 1
    var anchorX = sampleOf(path.x(0), originX)
    var anchorZ = sampleOf(path.z(0), originZ)
    for (index in 2 until path.size) {
        val toX = sampleOf(path.x(index), originX)
        val toZ = sampleOf(path.z(index), originZ)
        if (!hasClearLine(anchorX, anchorZ, toX, toZ, required)) {
            val corner = index - 1
            path.set(write++, path.x(corner), path.y(corner), path.z(corner))
            anchorX = sampleOf(path.x(corner), originX)
            anchorZ = sampleOf(path.z(corner), originZ)
        }
    }
    val last = path.size - 1
    path.set(write++, path.x(last), path.y(last), path.z(last))
    path.size = write
}

/**
 * Whether the segment between two sample centres crosses only samples the agent can stand on.
 *
 * A supercover walk, visiting every sample the segment touches rather than one per column as a
 * drawn line would: a thin diagonal gap is what a drawn line skips and a walking agent cannot fit
 * through. Where the segment passes exactly through a corner shared by four samples, both side
 * samples must be standable, the same rule the search applies to a diagonal step; without it,
 * smoothing would cut the corner the search refused to.
 */
internal fun NavSurface.hasClearLine(startX: Int, startZ: Int, endX: Int, endZ: Int, required: Int): Boolean {
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
    var clear = standable(x, z, required)
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
                clear = standable(x + stepX, z, required) && standable(x, z + stepZ, required)
                x += stepX
                z += stepZ
                error += doubleX - doubleZ
                remaining -= 2
            }
        }
        if (clear) clear = standable(x, z, required)
    }
    return clear
}
