/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Poolable

/**
 * **Starter behavior** — engine-provided example. Copy and adapt for game-specific chase logic
 * rather than inheriting; the engine does not guarantee backwards compatibility on this class.
 *
 * Makes an entity pursue [target] along a navmesh path.
 *
 * Carries this chaser's own route state through [RouteFollower], which is what lets a single
 * [ChaseAiSystem] drive every chaser in the world rather than needing one system instance per NPC.
 * The only thing chasing adds to walking a route is *where* the route goes: [target]'s position.
 *
 * Not a `data class`: [path], [waypointIndex] and [timeSinceRepath] are runtime state, and a
 * generated `equals` covering only the constructor arguments would be quietly wrong.
 *
 * @property target Entity to pursue. A null target, or one that has been destroyed, is skipped.
 * @property speed Movement speed in units per second.
 * @property repathInterval Interval in seconds between recalculating the path.
 * @property waypointRadius Proximity threshold to advance to the next waypoint.
 */
class ChaseBehavior(
    var target: Entity? = null,
    override var speed: Float = DEFAULT_SPEED,
    override var repathInterval: Float = DEFAULT_REPATH_INTERVAL,
    override var waypointRadius: Float = DEFAULT_WAYPOINT_RADIUS,
) : Poolable,
    RouteFollower {
    override var path: List<Vec3f> = emptyList()
    override var waypointIndex: Int = 0
    override var timeSinceRepath: Float = Float.MAX_VALUE

    override fun reset() {
        target = null
        speed = DEFAULT_SPEED
        repathInterval = DEFAULT_REPATH_INTERVAL
        waypointRadius = DEFAULT_WAYPOINT_RADIUS
        path = emptyList()
        waypointIndex = 0
        timeSinceRepath = Float.MAX_VALUE
    }

    /**
     * Default constants and tuning parameters for chase behaviors.
     */
    companion object {
        /** Default pursuit speed in units per second. */
        const val DEFAULT_SPEED = 2.5f

        /** Default interval in seconds between recalculating path requests. */
        const val DEFAULT_REPATH_INTERVAL = 0.5f

        /** Default proximity distance threshold to consider a waypoint reached. */
        const val DEFAULT_WAYPOINT_RADIUS = 0.3f
    }
}
