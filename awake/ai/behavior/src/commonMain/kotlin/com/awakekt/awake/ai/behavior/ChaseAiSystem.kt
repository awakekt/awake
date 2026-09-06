/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.PathRequest
import com.awakekt.awake.scene.core.transform.Transform

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
 * A chaser therefore needs three components: [Transform], [ChaseBehavior] and [PathRequest]. One
 * missing its request is skipped rather than steering blind.
 *
 * Everything after "where do I go" is [RouteFollower]'s: asking on an interval, adopting the
 * answer, stepping along it. All this class contributes is the target's position.
 *
 * Order in the schedule is not load-bearing. Running before the path system answers a request in
 * the same frame; running after answers it in the next. Neither is wrong, because a chaser keeps
 * steering along its previous route while a new one is outstanding.
 *
 * Chase only, not chase-*and*-avoid; dodge/avoid behaviour is a follow-up. See
 * docs/tasks/2026-08-30-behavior-tree-state-machine-plan.md for where composable behaviours go.
 */
class ChaseAiSystem : System {

    override fun update(world: World, delta: Float) {
        world.family<Transform, ChaseBehavior>().forEach { entity, transform, chase ->
            val request = world.get<PathRequest>(entity) ?: return@forEach
            chase.adoptArrivedRoute(request)
            chase.requestRouteWhenDue(transform, targetPositionOf(world, chase), request, delta)
            chase.steer(transform, delta)
        }
    }

    /**
     * Reads the target through the world rather than from a held reference, so a destroyed or
     * retargeted entity stops the queries instead of routing toward a position nothing updates.
     */
    private fun targetPositionOf(world: World, chase: ChaseBehavior) =
        chase.target?.let { world.get<Transform>(it) }?.position
}
