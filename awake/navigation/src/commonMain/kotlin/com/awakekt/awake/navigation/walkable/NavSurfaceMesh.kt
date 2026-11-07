/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.NavMesh
import com.awakekt.awake.navigation.PathRequestSystem
import com.awakekt.awake.navigation.grid.NavGroundHeight

/**
 * A [NavSurface] as the [NavMesh] that [PathRequestSystem] and the AI behaviours route through, for
 * one [agent].
 *
 * Each call answers with a fresh [NavPathQuery], so calls from several threads are safe, which
 * [PathRequestSystem] needs when it searches off the frame thread; the price is that each call
 * allocates its search state. Code that routes every frame holds its own query and [NavPath].
 *
 * An unreachable target answers with the route to the nearest reachable point, not an empty list,
 * so an agent asked to walk into a building walks to its wall. Only a start with nowhere to stand
 * answers empty.
 *
 * @param surface Where agents can stand.
 * @param agent The body routed.
 * @param ground World Y for each waypoint.
 * @param maxExpandedNodes The most samples one query expands.
 */
class NavSurfaceMesh(
    private val surface: NavSurface,
    private val agent: NavAgent,
    private val ground: NavGroundHeight = NavGroundHeight.FLAT,
    private val maxExpandedNodes: Int = NavPathQuery.DEFAULT_MAX_EXPANDED_NODES,
) : NavMesh {
    override fun findPath(start: Vec3f, end: Vec3f): List<Vec3f> {
        val path = NavPath()
        NavPathQuery(surface, ground, maxExpandedNodes).findPath(start, end, agent, path)
        return path.toList()
    }
}
