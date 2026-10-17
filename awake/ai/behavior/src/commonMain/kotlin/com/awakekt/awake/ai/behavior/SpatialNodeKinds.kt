/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.behavior

import com.awakekt.awake.ai.definition.AiRegistry
import com.awakekt.awake.ai.definition.NodeCategory
import com.awakekt.awake.ai.definition.NodeKind
import com.awakekt.awake.ai.definition.NodeKindId

/**
 * The behaviour-tree kinds that move and place agents, over [placement] and each agent's
 * [com.awakekt.awake.navigation.PathRequest]. Register them with [registerSpatialKinds]. The moving
 * kinds route through the request, so a `PathRequestSystem` answers them, as it does
 * [ChaseBehavior]'s.
 *
 * A target is a blackboard key holding an entity or a position, such as perception's
 * `perception.target`, or a fixed point. Moving kinds fail when their agent has no request or no
 * place, when the target has none, or when it cannot be reached; leaving a moving node (a parent
 * resets it) withdraws its route question.
 *
 * @param placement Where agents are, and how they move and turn.
 */
class SpatialNodeKinds(placement: AgentPlacement) {
    /** `ai.move-to`: walks to a target and succeeds there. */
    val moveTo: NodeKind<MoveToParams> = NodeKind(
        NodeKindId("ai.move-to"),
        MoveToParams.serializer(),
        NodeCategory.Action,
        displayName = "Move To",
        description = "Walks to target over the navigation grid; succeeds within arriveRadius, fails if it cannot get there.",
    ) { params, _ -> MoveToNode(placement, params) }

    /** `ai.follow`: keeps within a distance of a target. */
    val follow: NodeKind<FollowParams> = NodeKind(
        NodeKindId("ai.follow"),
        FollowParams.serializer(),
        NodeCategory.Action,
        displayName = "Follow",
        description = "Keeps within distance of target, standing still when close; runs until the target is gone.",
    ) { params, _ -> FollowNode(placement, params) }

    /** `ai.flee`: runs from a threat until safe. */
    val flee: NodeKind<FleeParams> = NodeKind(
        NodeKindId("ai.flee"),
        FleeParams.serializer(),
        NodeCategory.Action,
        displayName = "Flee From",
        description = "Runs away from from until safeDistance away, trying other directions when the way out is blocked.",
    ) { params, _ -> FleeNode(placement, params) }

    /** `ai.wander`: walks to a random point near where it started. */
    val wander: NodeKind<WanderParams> = NodeKind(
        NodeKindId("ai.wander"),
        WanderParams.serializer(),
        NodeCategory.Action,
        displayName = "Wander",
        description = "Walks to a random point within radius of where it first ran, from the tree's seed, and succeeds there.",
    ) { params, scope -> WanderNode(placement, params, scope.seed xor (params.seed * SEED_SPREAD)) }

    /** `ai.face`: turns toward a target. */
    val face: NodeKind<FaceParams> = NodeKind(
        NodeKindId("ai.face"),
        FaceParams.serializer(),
        NodeCategory.Action,
        displayName = "Face",
        description = "Turns the agent toward target and succeeds.",
    ) { params, _ -> FaceNode(placement, params) }

    /** `ai.distance`: compares the distance to a target. */
    val distance: NodeKind<DistanceParams> = NodeKind(
        NodeKindId("ai.distance"),
        DistanceParams.serializer(),
        NodeCategory.Condition,
        displayName = "Distance",
        description = "Succeeds when the ground distance to target compares by op with distance; < means within.",
    ) { params, _ -> DistanceNode(placement, params) }

    /** `ai.in-area`: tests a position against a circle or a box. */
    val inArea: NodeKind<AreaParams> = NodeKind(
        NodeKindId("ai.in-area"),
        AreaParams.serializer(),
        NodeCategory.Condition,
        displayName = "In Area",
        description = "Succeeds when the agent, or who, stands in the circle or box on the ground plane.",
    ) { params, _ -> InAreaNode(placement, params) }

    /** Every spatial kind. */
    val nodeKinds: List<NodeKind<*>> = listOf(moveTo, follow, flee, wander, face, distance, inArea)

    private companion object {
        const val SEED_SPREAD = -0x61c8864680b583ebL
    }
}

/** Registers the [SpatialNodeKinds] over [placement]. */
fun AiRegistry.registerSpatialKinds(placement: AgentPlacement): AiRegistry {
    SpatialNodeKinds(placement).nodeKinds.forEach(::register)
    return this
}
