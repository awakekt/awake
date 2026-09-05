/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.behavior

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.Entity
import io.github.awakelab.awake.ecs.Poolable

/**
 * **Starter behavior** — engine-provided example. Copy and adapt for game-specific flee logic
 * rather than inheriting; the engine does not guarantee backwards compatibility on this class.
 *
 * Makes an entity run away from [threat] once it comes within [panicRadius], and stop once it is
 * further away than [safeRadius].
 *
 * Two radii rather than one: a single threshold makes an entity hovering at exactly that distance
 * start and stop every frame. The gap between them is the hysteresis that stops the twitch — the
 * same reason `WorldPartitionConfig` separates its loading and unloading radii.
 */
class FleeBehavior(
    /** Entity to run from. A null threat, or one destroyed, leaves the entity standing still. */
    var threat: Entity? = null,
    /** Start fleeing when the threat is closer than this. */
    var panicRadius: Float = DEFAULT_PANIC_RADIUS,
    /** Stop fleeing once the threat is further than this. Must exceed [panicRadius]. */
    var safeRadius: Float = DEFAULT_SAFE_RADIUS,
    /** How far ahead to aim when picking somewhere to run to. */
    var fleeDistance: Float = DEFAULT_FLEE_DISTANCE,
    override var speed: Float = DEFAULT_SPEED,
    override var repathInterval: Float = DEFAULT_REPATH_INTERVAL,
    override var waypointRadius: Float = DEFAULT_WAYPOINT_RADIUS,
) : Poolable,
    RouteFollower {
    override var path: List<Vec3f> = emptyList()
    override var waypointIndex: Int = 0
    override var timeSinceRepath: Float = Float.MAX_VALUE

    /** True while running; drives the hysteresis between [panicRadius] and [safeRadius]. */
    var fleeing: Boolean = false

    /**
     * How many escape directions the current panic has already tried.
     *
     * Running straight away from a threat aims at a wall about as often as not, and the grid
     * answers that with `Unreachable`. Rather than stand still and be caught, each failed attempt
     * fans the next one further off the direct line. Reset when a route is found.
     */
    var escapeAttempt: Int = 0

    override fun reset() {
        threat = null
        panicRadius = DEFAULT_PANIC_RADIUS
        safeRadius = DEFAULT_SAFE_RADIUS
        fleeDistance = DEFAULT_FLEE_DISTANCE
        speed = DEFAULT_SPEED
        repathInterval = DEFAULT_REPATH_INTERVAL
        waypointRadius = DEFAULT_WAYPOINT_RADIUS
        path = emptyList()
        waypointIndex = 0
        timeSinceRepath = Float.MAX_VALUE
        fleeing = false
        escapeAttempt = 0
    }

    companion object {
        const val DEFAULT_PANIC_RADIUS = 6f
        const val DEFAULT_SAFE_RADIUS = 12f
        const val DEFAULT_FLEE_DISTANCE = 10f
        const val DEFAULT_SPEED = 3.5f
        const val DEFAULT_REPATH_INTERVAL = 0.4f
        const val DEFAULT_WAYPOINT_RADIUS = 0.3f

        /** How far each retry fans off the direct escape line, in radians. */
        const val ESCAPE_FAN_RADIANS = 0.6f

        /** Retries before giving up this interval; beyond a half-turn the "escape" runs inward. */
        const val MAX_ESCAPE_ATTEMPTS = 5
    }
}
