/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest

/**
 * Steers every [ChaseBehavior] entity along the route its [PathRequest] most recently returned.
 *
 * **This system does no pathfinding.** It decides *when* to ask — every
 * [ChaseBehavior.repathInterval] seconds, because a query costs milliseconds and cannot run at
 * 60Hz for one NPC, let alone many — and
 * [PathRequestSystem][com.awakekt.awake.navigation.PathRequestSystem] decides
 * how to answer. Splitting them is what lets the answer move off the frame thread later without
 * this class knowing, and what lets a chaser share one navigation path with every other behaviour
 * that needs one.
 *
 * A chaser therefore needs [ChaseBehavior] and [PathRequest], and a position from [placement]. One
 * missing either is skipped rather than steering blind.
 *
 * Everything after "where do I go" is [RouteFollower]'s: asking on an interval, adopting the
 * answer, stepping along it. All this class contributes is the target's position.
 *
 * Order in the schedule is not load-bearing. Running before the path system answers a request in
 * the same frame; running after answers it in the next. Neither is wrong, because a chaser keeps
 * steering along its previous route while a new one is outstanding.
 *
 * Chase only, not chase-*and*-avoid; dodge/avoid behaviour is a follow-up.
 */
class ChaseAiSystem(
    /** Where chasers and their targets are, and how a chaser moves. */
    private val placement: AgentPlacement,
) : Family2System<ChaseBehavior, PathRequest>(ChaseBehavior::class, PathRequest::class) {
    private val position = Vec3f(0f, 0f, 0f)
    private val targetPosition = Vec3f(0f, 0f, 0f)

    override fun process(world: World, delta: Float, entity: Entity, first: ChaseBehavior, second: PathRequest) {
        if (!placement.position(world, entity, position)) return
        first.adoptArrivedRoute(second)
        first.requestRouteWhenDue(position, targetPositionOf(world, first), second, delta)
        first.steer(placement, world, entity, position, delta)
    }

    override fun update(world: World, delta: Float) = runTick(world, delta) { entity, first, second ->
        process(world, delta, entity, first, second)
    }

    /**
     * Reads the target through the world rather than from a held reference, so a destroyed or
     * retargeted entity stops the queries instead of routing toward a position nothing updates.
     */
    private fun targetPositionOf(world: World, chase: ChaseBehavior): Vec3f? {
        val target = chase.target ?: return null
        return if (placement.position(world, target, targetPosition)) targetPosition else null
    }
}
