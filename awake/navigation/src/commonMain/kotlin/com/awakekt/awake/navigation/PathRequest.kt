/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Poolable

/** Where a [PathRequest] is in its lifecycle. */
enum class PathStatus {
    /** Nothing asked for. A fresh or cancelled request rests here. */
    Idle,

    /** Asked for, not answered. [PathRequestSystem] picks these up. */
    Pending,

    /** Answered; [PathRequest.waypoints] holds the route. */
    Ready,

    /** Answered; no route exists, and [PathRequest.waypoints] is empty. */
    Unreachable,
}

/**
 * One entity's outstanding navigation query.
 *
 * A request-and-poll component rather than a blocking call, because a path is not something an
 * entity can wait for inside one frame. That shape is also what the decision-runtime plan needs: a
 * `MoveTo` behaviour writes a request, stays in a running state while the status is [Pending], and
 * calls [cancel] if it is interrupted. See
 * docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md.
 *
 * [start] and [goal] are owned by this component. [requestPath] copies into them rather than
 * holding the caller's vectors, which are mutable and usually belong to a `Transform` that moves
 * out from under the query.
 */
class PathRequest : Poolable {
    /** Query origin. Owned; write it through [requestPath]. */
    val start: Vec3f = Vec3f(0f, 0f, 0f)

    /** Query destination. Owned; write it through [requestPath]. */
    val goal: Vec3f = Vec3f(0f, 0f, 0f)

    /**
     * Lifecycle position. A consumer moves it to [Pending] through [requestPath] and back to
     * [Idle] through [cancel]; [PathRequestSystem] owns the answer transitions.
     */
    var status: PathStatus = PathStatus.Idle

    /** The route once [status] is [PathStatus.Ready], and empty otherwise. */
    var waypoints: List<Vec3f> = emptyList()

    /**
     * Counts how many distinct questions this component has asked.
     *
     * [PathRequestSystem] captures it when it starts a search and rechecks it before applying the
     * answer, which is what a status check alone cannot do: an entity that cancels and immediately
     * asks again is [PathStatus.Pending] both times, so the answer to the abandoned question would
     * otherwise look like the answer to the current one.
     */
    var queryGeneration: Int = 0
        private set

    /** Asks for a route from [from] to [to], discarding any previous answer. */
    fun requestPath(from: Vec3f, to: Vec3f) {
        start.set(from)
        goal.set(to)
        status = PathStatus.Pending
        waypoints = emptyList()
        queryGeneration++
    }

    /** Withdraws the query and drops any answer, whether or not one had arrived. */
    fun cancel() {
        status = PathStatus.Idle
        waypoints = emptyList()
        queryGeneration++
    }

    override fun reset() {
        start.set(0f, 0f, 0f)
        goal.set(0f, 0f, 0f)
        status = PathStatus.Idle
        waypoints = emptyList()
        // Bumped rather than zeroed: a pooled component's job is to invalidate whatever the
        // previous occupant left outstanding, and a counter that restarts can match it again.
        queryGeneration++
    }
}
