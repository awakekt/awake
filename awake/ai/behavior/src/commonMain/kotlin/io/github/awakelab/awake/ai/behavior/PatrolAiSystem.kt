/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ai.behavior

import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.navigation.PathRequest
import io.github.awakelab.awake.navigation.PathStatus
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlin.math.sqrt

/**
 * Walks every [PatrolBehavior] entity along its stops.
 *
 * Like the other behaviours it does no pathfinding — it decides which stop is next and writes a
 * `PathRequest`; `PathRequestSystem` answers it, and [RouteFollower] owns the route mechanics.
 */
class PatrolAiSystem : System {

    override fun update(world: World, delta: Float) {
        world.family<Transform, PatrolBehavior>().forEach { entity, transform, patrol ->
            val request = world.get<PathRequest>(entity) ?: return@forEach
            if (patrol.stops.isEmpty() || patrol.finished) return@forEach
            skipUnreachableStop(patrol, request)
            patrol.adoptArrivedRoute(request)
            if (dwelling(patrol, delta)) return@forEach
            patrol.requestRouteWhenDue(transform, patrol.currentStop, request, delta)
            patrol.steer(transform, delta)
            if (reachedCurrentStop(patrol, transform)) arriveAtStop(patrol)
        }
    }

    /**
     * An unreachable stop advances the route instead of retrying it forever.
     *
     * Stops are authored, so this is a content bug — but a patrol that deadlocks on one bad stop
     * hides the rest of the beat, and a guard that visibly skips one corner is easier to notice
     * than one that stopped moving for no stated reason.
     */
    private fun skipUnreachableStop(patrol: PatrolBehavior, request: PathRequest) {
        if (request.status == PathStatus.Unreachable) advance(patrol)
    }

    /**
     * Arrival is measured against the stop itself, not against the route running out.
     *
     * The adopted route always lags one frame behind [PatrolBehavior.stopIndex] -- a request is
     * issued for the current stop and answered afterwards -- so a route that finishes instantly,
     * which is what happens when the entity already stands on the stop it asked for, would advance
     * the index a second time and silently skip the stop after it. Distance to the current stop
     * has no such coupling.
     */
    private fun reachedCurrentStop(patrol: PatrolBehavior, transform: Transform): Boolean {
        val stop = patrol.currentStop ?: return false
        val dx = stop.x - transform.position.x
        val dz = stop.z - transform.position.z
        return sqrt(dx * dx + dz * dz) <= patrol.waypointRadius
    }

    /** @return true while the entity is holding at a stop and should not move this tick. */
    private fun dwelling(patrol: PatrolBehavior, delta: Float): Boolean {
        if (patrol.dwellRemaining <= 0f) return false
        patrol.dwellRemaining -= delta
        return patrol.dwellRemaining > 0f
    }

    /** Starts the dwell and picks the next stop; the route is cleared so steering idles meanwhile. */
    private fun arriveAtStop(patrol: PatrolBehavior) {
        patrol.path = emptyList()
        patrol.waypointIndex = 0
        patrol.dwellRemaining = patrol.dwellSeconds
        advance(patrol)
    }

    /**
     * Moves [PatrolBehavior.stopIndex] on by the style's rule.
     *
     * A single-stop route is its own special case: ping-pong has nothing to bounce between, and
     * looping would repath to the spot it is standing on every interval.
     */
    private fun advance(patrol: PatrolBehavior) {
        val lastIndex = patrol.stops.lastIndex
        if (lastIndex <= 0) {
            if (patrol.style == PatrolStyle.Once) patrol.finished = true
            return
        }
        when (patrol.style) {
            PatrolStyle.Loop -> patrol.stopIndex = (patrol.stopIndex + 1) % patrol.stops.size
            PatrolStyle.Once -> {
                if (patrol.stopIndex >= lastIndex) patrol.finished = true else patrol.stopIndex++
            }
            PatrolStyle.PingPong -> {
                if (patrol.stopIndex + patrol.direction !in 0..lastIndex) patrol.direction = -patrol.direction
                patrol.stopIndex += patrol.direction
            }
        }
        // The next stop is a different place, so last stop's route must not be walked into it.
        patrol.timeSinceRepath = Float.MAX_VALUE
    }
}
