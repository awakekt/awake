/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.ai

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.System
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.components.Transform
import io.github.awakelab.awake.scene.navigation.PathRequest
import io.github.awakelab.awake.scene.navigation.PathStatus
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runs every [FleeBehavior] entity away from its threat.
 *
 * Like [ChaseAiSystem] it does no pathfinding — it decides *where* to run and writes a
 * `PathRequest`; `PathRequestSystem` answers it. All the route mechanics are [RouteFollower]'s.
 *
 * A flee target is guessed rather than searched: the grid can say whether a spot is reachable but
 * not which reachable spot is furthest from a threat, and answering that properly means a
 * Dijkstra flood outward, which is a much larger feature than this behaviour needs.
 */
class FleeAiSystem : System {

    override fun update(world: World, delta: Float) {
        world.family<Transform, FleeBehavior>().forEach { entity, transform, flee ->
            val request = world.get<PathRequest>(entity) ?: return@forEach
            val threatPosition = flee.threat?.let { world.get<Transform>(it) }?.position
            updatePanic(flee, transform, threatPosition)
            noteEscapeOutcome(flee, request)
            flee.adoptArrivedRoute(request)
            if (flee.fleeing) {
                flee.requestRouteWhenDue(transform, escapeGoal(flee, transform, threatPosition), request, delta)
                flee.steer(transform, delta)
            }
        }
    }

    /**
     * Enters panic inside `panicRadius` and leaves it outside `safeRadius`, leaving the band
     * between them to whichever state is already active. A lost threat calms the entity rather
     * than leaving it running forever at whatever it last saw.
     */
    private fun updatePanic(flee: FleeBehavior, transform: Transform, threat: Vec3f?) {
        if (threat == null) {
            stopFleeing(flee)
            return
        }
        val distance = planarDistance(transform.position, threat)
        when {
            distance <= flee.panicRadius -> flee.fleeing = true
            distance >= flee.safeRadius -> stopFleeing(flee)
            else -> Unit
        }
    }

    private fun stopFleeing(flee: FleeBehavior) {
        flee.fleeing = false
        flee.path = emptyList()
        flee.waypointIndex = 0
        flee.escapeAttempt = 0
    }

    /**
     * Counts an `Unreachable` answer as a failed escape direction so the next attempt fans wider.
     *
     * Read before [RouteFollower.adoptArrivedRoute] clears the status, which is why this is a
     * separate step rather than part of the adopt.
     */
    private fun noteEscapeOutcome(flee: FleeBehavior, request: PathRequest) {
        when (request.status) {
            PathStatus.Unreachable -> flee.escapeAttempt++
            PathStatus.Ready -> flee.escapeAttempt = 0
            else -> Unit
        }
    }

    /**
     * A point `fleeDistance` away, directly opposite the threat on the first try and fanned by
     * [FleeBehavior.ESCAPE_FAN_RADIANS] per failure after that.
     *
     * Beyond [FleeBehavior.MAX_ESCAPE_ATTEMPTS] the fan has swung far enough that "away" would
     * start pointing back toward the threat, so it gives up for this interval and tries again from
     * straight-away on the next one — by which time the threat has moved and the geometry differs.
     */
    private fun escapeGoal(flee: FleeBehavior, transform: Transform, threat: Vec3f?): Vec3f? {
        if (threat == null || flee.escapeAttempt >= FleeBehavior.MAX_ESCAPE_ATTEMPTS) return null
        var dx = transform.position.x - threat.x
        var dz = transform.position.z - threat.z
        val distance = sqrt(dx * dx + dz * dz)
        if (distance < MIN_SEPARATION) {
            // Standing on the threat: any direction is equally away, so pick a fixed one rather
            // than normalising a zero vector into NaN.
            dx = 1f
            dz = 0f
        } else {
            dx /= distance
            dz /= distance
        }
        // Alternate the fan left and right so retries spread either side of straight-away rather
        // than sweeping one way and hugging the obstacle that blocked the first try.
        val step = (flee.escapeAttempt + 1) / 2
        val sign = if (flee.escapeAttempt % 2 == 0) 1f else -1f
        val angle = sign * step * FleeBehavior.ESCAPE_FAN_RADIANS
        val cosA = cos(angle)
        val sinA = sin(angle)
        return Vec3f(
            transform.position.x + (dx * cosA - dz * sinA) * flee.fleeDistance,
            0f,
            transform.position.z + (dx * sinA + dz * cosA) * flee.fleeDistance,
        )
    }

    private fun planarDistance(a: Vec3f, b: Vec3f): Float {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return sqrt(dx * dx + dz * dz)
    }

    private companion object {
        /** Below this the away-vector is noise, not a direction. */
        const val MIN_SEPARATION = 0.001f
    }
}
