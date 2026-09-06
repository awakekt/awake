/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.navigation.PathStatus
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.sqrt

/**
 * The state every "walk somewhere over a navmesh" behaviour needs, regardless of *where* it walks.
 *
 * Chasing, fleeing and patrolling differ only in how they pick a goal. Everything after that — ask
 * on an interval, adopt the answer, step along it, advance on arrival — is identical, and writing
 * it once per behaviour would triplicate the awkward parts (a route surviving while its
 * replacement is outstanding, an unreachable answer clearing rather than stranding).
 *
 * This is the `MoveTo` leaf the decision-runtime plan predicted, arrived at from three concrete
 * behaviours rather than designed up front. See
 * docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md.
 */
interface RouteFollower {
    // Tuning values, and `var` because tuning is what they are for: the inspector edits them on
    // a live entity and the scene file authors them. Every implementation already declared them
    // that way; only this interface was narrower than its own implementations.

    /** Metres per second along the current route. */
    var speed: Float

    /** Seconds between path queries. Pathfinding costs milliseconds; it cannot run every frame. */
    var repathInterval: Float

    /** How close counts as reaching a waypoint. */
    var waypointRadius: Float

    /** Waypoints from the last query, replaced wholesale on each repath. */
    var path: List<Vec3f>

    /** Index into [path] of the waypoint currently being steered toward. */
    var waypointIndex: Int

    /** Starts already elapsed, so the first update paths whatever [repathInterval] is. */
    var timeSinceRepath: Float
}

/** True once the follower has walked past the end of its route. */
internal val RouteFollower.routeFinished: Boolean
    get() = waypointIndex >= path.size

/**
 * Takes ownership of a finished route so steering never reads a request about to be reused, and
 * returns the request to [PathStatus.Idle] so it is not adopted twice.
 *
 * An unreachable answer clears the route rather than leaving the follower walking its previous one
 * into a wall.
 */
internal fun RouteFollower.adoptArrivedRoute(request: PathRequest) {
    when (request.status) {
        PathStatus.Ready -> {
            path = request.waypoints
            waypointIndex = 0
            request.cancel()
        }
        PathStatus.Unreachable -> {
            path = emptyList()
            waypointIndex = 0
            request.cancel()
        }
        else -> Unit
    }
}

/**
 * Asks for a route to [goal] when the interval has elapsed and nothing is already outstanding.
 *
 * A null [goal] means the behaviour has nowhere to go this tick — a destroyed target, an empty
 * patrol route — and stops the queries rather than pathing to a stale position.
 *
 * @return true when a request was issued.
 */
internal fun RouteFollower.requestRouteWhenDue(
    from: Transform,
    goal: Vec3f?,
    request: PathRequest,
    delta: Float,
): Boolean {
    timeSinceRepath += delta
    val due = timeSinceRepath >= repathInterval
    if (!due || request.status == PathStatus.Pending || goal == null) return false
    timeSinceRepath = 0f
    request.requestPath(from.position, goal)
    return true
}

/**
 * Steps [transform] toward the current waypoint, advancing when it is reached.
 *
 * Movement is kinematic and XZ-only: navigation waypoints carry no height, so whatever owns the
 * entity puts it back on the ground.
 */
internal fun RouteFollower.steer(transform: Transform, delta: Float) {
    val waypoint = path.getOrNull(waypointIndex) ?: return
    val dx = waypoint.x - transform.position.x
    val dz = waypoint.z - transform.position.z
    val distance = sqrt(dx * dx + dz * dz)
    if (distance <= waypointRadius) {
        waypointIndex++
    } else {
        val invDistance = 1f / distance
        transform.position.x += dx * invDistance * speed * delta
        transform.position.z += dz * invDistance * speed * delta
    }
}
