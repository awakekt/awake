/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation.walkable

import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/** Where a [NavPathFollower] is on its way to a destination. */
enum class NavFollowStatus {
    /** No destination. */
    Idle,

    /** Walking a route. */
    Moving,

    /** At the destination; the destination is cleared. */
    Arrived,

    /**
     * Stopped as near the destination as it can get: the destination is walled in, or the start has
     * nowhere to stand. The destination is kept, and the follower re-plans every [NavPathFollower.repathInterval]
     * in case the way opens or the budget reaches further from here.
     */
    Unreachable,
}

/**
 * Walks one agent along routes from a [NavPathQuery]: plans when given a destination, steers toward
 * each waypoint in turn, and re-plans on an interval and when the agent stops making progress.
 *
 * The follower only says which way to go; whatever moves the agent (a character controller, a
 * transform, a network) does the moving, and the follower reads where the agent got to on the next
 * [update]. Being pushed off the route, or held against something the surface does not know about,
 * shows up as no progress for [stuckSeconds], and the follower re-plans from where the agent is.
 *
 * Allocation-free per update: the route lives in this follower's own [path], reused across plans,
 * and the query is passed in, so one query on a thread serves every follower on it.
 *
 * @property agent The body routed.
 */
class NavPathFollower(var agent: NavAgent = NavAgent()) {
    /** The route being walked. Read it to draw it; the follower rewrites it on each plan. */
    val path: NavPath = NavPath()

    /** Where the follower is going. Set through [moveTo]. */
    val destination: Vec3f = Vec3f(0f, 0f, 0f)

    /** Metres from the destination that count as arriving. */
    var arriveDistance: Float = DEFAULT_ARRIVE_DISTANCE

    /** Metres from a corner that count as reaching it, so the agent turns rather than circles. */
    var waypointRadius: Float = DEFAULT_WAYPOINT_RADIUS

    /** Seconds between plans while walking; zero plans only on a new destination or when stuck. */
    var repathInterval: Float = DEFAULT_REPATH_INTERVAL

    /** Seconds without [stuckDistance] of progress before the follower re-plans. */
    var stuckSeconds: Float = DEFAULT_STUCK_SECONDS

    /** Metres the agent must move within [stuckSeconds] to count as making progress. */
    var stuckDistance: Float = DEFAULT_STUCK_DISTANCE

    /** Where the follower is. */
    var status: NavFollowStatus = NavFollowStatus.Idle
        private set

    /** Index into [path] of the waypoint being steered toward. */
    var waypoint: Int = 0
        private set

    /** Plans made since the follower was created, for tests and diagnostics. */
    var plans: Int = 0
        private set

    private var planDue = false
    private var sinceRepath = 0f
    private var sinceProgress = 0f
    private var progressX = 0f
    private var progressZ = 0f

    /**
     * Heads for [target], planning on the next [update]. Copies [target].
     *
     * @param target The destination; only X and Z are routed.
     */
    fun moveTo(target: Vec3f) {
        destination.set(target)
        status = NavFollowStatus.Moving
        planDue = true
    }

    /** Drops the destination and the route. */
    fun stop() {
        status = NavFollowStatus.Idle
        path.clear()
        planDue = false
    }

    /**
     * Steps the follower for an agent standing at [position], writing the unit XZ direction to walk
     * in into [direction].
     *
     * @param query Plans routes; shared by every follower on this thread.
     * @param position Where the agent stands now.
     * @param delta Seconds since the last update.
     * @param direction Receives the direction to walk, or zero when the agent should stand still.
     * @return True when the agent should walk.
     */
    fun update(query: NavPathQuery, position: Vec3f, delta: Float, direction: Vec3f): Boolean {
        direction.set(0f, 0f, 0f)
        val active = status == NavFollowStatus.Moving || status == NavFollowStatus.Unreachable
        if (active) {
            trackProgress(position, delta)
            if (planDue || (repathInterval > 0f && sinceRepath >= repathInterval)) plan(query, position)
            advance(position)
            steer(position, direction)
        }
        return status == NavFollowStatus.Moving
    }

    private fun trackProgress(position: Vec3f, delta: Float) {
        sinceRepath += delta
        val dx = position.x - progressX
        val dz = position.z - progressZ
        if (dx * dx + dz * dz >= stuckDistance * stuckDistance) {
            progressX = position.x
            progressZ = position.z
            sinceProgress = 0f
        } else if (status == NavFollowStatus.Moving) {
            sinceProgress += delta
            if (sinceProgress >= stuckSeconds) planDue = true
        }
    }

    private fun plan(query: NavPathQuery, position: Vec3f) {
        query.findPath(position, destination, agent, path)
        plans++
        planDue = false
        sinceRepath = 0f
        sinceProgress = 0f
        progressX = position.x
        progressZ = position.z
        // Waypoint 0 is where the agent already stands.
        waypoint = 1
        status = if (path.status == NavPathStatus.NoPath) NavFollowStatus.Unreachable else NavFollowStatus.Moving
    }

    /** Passes every waypoint already reached, then settles what reaching the end means. */
    private fun advance(position: Vec3f) {
        while (waypoint < path.size && reached(position, waypoint)) waypoint++
        if (status == NavFollowStatus.Moving && waypoint >= path.size) {
            val complete = path.status == NavPathStatus.Complete
            status = if (complete) NavFollowStatus.Arrived else NavFollowStatus.Unreachable
            if (complete) path.clear()
        }
    }

    private fun reached(position: Vec3f, index: Int): Boolean {
        val radius = if (index == path.size - 1) arriveDistance else waypointRadius
        val dx = path.x(index) - position.x
        val dz = path.z(index) - position.z
        return dx * dx + dz * dz <= radius * radius
    }

    private fun steer(position: Vec3f, direction: Vec3f) {
        if (status != NavFollowStatus.Moving || waypoint >= path.size) return
        val dx = path.x(waypoint) - position.x
        val dz = path.z(waypoint) - position.z
        val length = sqrt(dx * dx + dz * dz)
        if (length > 0f) direction.set(dx / length, 0f, dz / length)
    }

    /** Neutral defaults; a game authors its own. */
    companion object {
        /** Default [arriveDistance] in metres. */
        const val DEFAULT_ARRIVE_DISTANCE: Float = 0.25f

        /** Default [waypointRadius] in metres. */
        const val DEFAULT_WAYPOINT_RADIUS: Float = 0.35f

        /** Default [repathInterval] in seconds. */
        const val DEFAULT_REPATH_INTERVAL: Float = 2f

        /** Default [stuckSeconds]. */
        const val DEFAULT_STUCK_SECONDS: Float = 0.75f

        /** Default [stuckDistance] in metres. */
        const val DEFAULT_STUCK_DISTANCE: Float = 0.1f
    }
}
