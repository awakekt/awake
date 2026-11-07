/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.navigation.grid.NavGroundHeight
import kotlin.test.assertTrue

/** Small worlds the walkable tests share: flat ground, a 40 m square at 1 m, centred on the origin. */
internal object NavTestWorlds {
    val FLAT: NavGroundHeight = NavGroundHeight { _, _ -> 0f }

    /** 40 x 40 samples at 1 m, sample (0, 0) at (-20, -20). */
    fun square(size: Int = 40, sampleSize: Float = 1f): NavRegion =
        NavRegion(-size * sampleSize / 2f, -size * sampleSize / 2f, size, size, sampleSize)

    /** A wall 0.5 m thick along x = 0, from the near edge to [endZ], [height] tall. */
    fun wall(endZ: Float, height: Float = 3f): NavObstacle =
        NavObstacle(NavFootprint.Rect(-0.25f, -50f, 0.25f, endZ), 0f, height)

    /** A 10 x 10 m building centred on the origin. */
    val BUILDING: NavObstacle = NavObstacle(NavFootprint.Rect(-5f, -5f, 5f, 5f), 0f, 8f)

    /** Asserts every leg of [path] crosses only samples [agent] can stand on. */
    fun assertWalkable(surface: NavSurface, path: NavPath, agent: NavAgent) {
        val required = agent.requiredClearance(surface.sampleSize)
        for (index in 1 until path.size) {
            val clear = surface.hasClearLine(
                surface.sampleOf(path.x(index - 1), surface.originX),
                surface.sampleOf(path.z(index - 1), surface.originZ),
                surface.sampleOf(path.x(index), surface.originX),
                surface.sampleOf(path.z(index), surface.originZ),
                required,
            )
            assertTrue(clear, "leg ${index - 1} -> $index of ${path.toList()} crosses blocked ground")
        }
    }

    /** Clearance at the sample nearest world ([x], [z]). */
    fun clearanceAt(surface: NavSurface, x: Float, z: Float): Int =
        surface.clearance(surface.sampleOf(x, surface.originX), surface.sampleOf(z, surface.originZ))
}
