/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.behavior

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Poolable

/** What a patrol does when it reaches the last stop on its route. */
enum class PatrolStyle {
    /** Wraps to the first stop — a circuit. */
    Loop,

    /** Walks the route backwards — a there-and-back beat. */
    PingPong,

    /** Stops. The entity holds position at the last stop. */
    Once,
}

/**
 * **Starter behavior** — engine-provided example. Copy and adapt for game-specific patrol logic
 * rather than inheriting; the engine does not guarantee backwards compatibility on this class.
 *
 * Walks an entity between fixed [stops] over a navmesh.
 *
 * The stops are authored positions, not something navigation discovers, so an unreachable one is a
 * content bug rather than a runtime condition — [PatrolAiSystem] skips it and moves on so a single
 * bad stop cannot deadlock the beat.
 */
class PatrolBehavior(
    /** Positions to visit in order. Owned: the list is copied, not aliased. */
    stops: List<Vec3f> = emptyList(),
    var style: PatrolStyle = PatrolStyle.Loop,
    /** Seconds to hold at each stop before moving on. */
    var dwellSeconds: Float = DEFAULT_DWELL_SECONDS,
    override var speed: Float = DEFAULT_SPEED,
    override var repathInterval: Float = DEFAULT_REPATH_INTERVAL,
    override var waypointRadius: Float = DEFAULT_WAYPOINT_RADIUS,
) : Poolable,
    RouteFollower {
    /**
     * Copied rather than held: callers build these from level data they keep editing, and a patrol
     * quietly changing route because someone mutated the list they passed is the same
     * reference-holding bug `ChaseAiSystem` had with `Transform`.
     */
    var stops: List<Vec3f> = stops.map { it.copy() }
        set(value) {
            field = value.map { it.copy() }
            stopIndex = 0
            path = emptyList()
            waypointIndex = 0
        }

    override var path: List<Vec3f> = emptyList()
    override var waypointIndex: Int = 0
    override var timeSinceRepath: Float = Float.MAX_VALUE

    /** Which stop is currently being walked to. */
    var stopIndex: Int = 0

    /** -1 once a [PatrolStyle.PingPong] route turns around. */
    var direction: Int = 1

    /** Counts down while holding at a stop; zero means moving. */
    var dwellRemaining: Float = 0f

    /** True once a [PatrolStyle.Once] route has reached its end. */
    var finished: Boolean = false

    /** The stop being walked to, or null when the route is empty or finished. */
    val currentStop: Vec3f?
        get() = if (finished) null else stops.getOrNull(stopIndex)

    override fun reset() {
        stops = emptyList()
        style = PatrolStyle.Loop
        dwellSeconds = DEFAULT_DWELL_SECONDS
        speed = DEFAULT_SPEED
        repathInterval = DEFAULT_REPATH_INTERVAL
        waypointRadius = DEFAULT_WAYPOINT_RADIUS
        path = emptyList()
        waypointIndex = 0
        timeSinceRepath = Float.MAX_VALUE
        stopIndex = 0
        direction = 1
        dwellRemaining = 0f
        finished = false
    }

    companion object {
        const val DEFAULT_DWELL_SECONDS = 1f
        const val DEFAULT_SPEED = 1.8f
        const val DEFAULT_REPATH_INTERVAL = 1f
        const val DEFAULT_WAYPOINT_RADIUS = 0.3f
    }
}
