/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.walkable.NavAgent
import com.awakekt.awake.navigation.walkable.NavPathFollower
import com.awakekt.awake.scene.binding.RegisteredBinding
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneResolutionContext
import kotlin.reflect.KClass

/**
 * A scene's [SceneNavigationAgent] once it is in a world: the [follower] that plans and steers it,
 * and the [speed] [NavigationAgentSystem] walks it at.
 *
 * @property follower Plans, steers and re-plans this agent. Its tuning is the authored component's.
 * @property speed Metres per second.
 * @property startDestination The destination authored to head for when the scene starts, kept so
 *   the scene exports unchanged; null for none.
 */
class NavigationAgent(
    val follower: NavPathFollower = NavPathFollower(),
    var speed: Float = SceneNavigationAgent.DEFAULT_SPEED,
    val startDestination: Vec3f? = null,
) {
    /**
     * Sends the agent to [target]: it plans on the next update and walks there. Copies [target].
     *
     * @param target World position; only X and Z are routed.
     */
    fun moveTo(target: Vec3f) = follower.moveTo(target)

    /** Stops the agent where it stands and drops its route. */
    fun stop() = follower.stop()
}

/** Binds [NavigationAgent] to [SceneNavigationAgent]: the one mapping between them is here. */
@RegisteredBinding
object NavigationAgentBinding : SceneComponentBinding<NavigationAgent, SceneNavigationAgent> {
    override val componentClass: KClass<NavigationAgent> = NavigationAgent::class
    override val schemaClass: KClass<SceneNavigationAgent> = SceneNavigationAgent::class
    override val serializer = SceneNavigationAgent.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneNavigationAgent,
        context: SceneResolutionContext,
    ) {
        world.add(entity, component.toAgent())
    }

    override fun export(world: World, entity: Entity, component: NavigationAgent): SceneNavigationAgent = component.toScene()
}

/**
 * The live agent for this schema. Height, step and slope are the surface's, baked from the scene's
 * `navigation` rows, so only the radius is the agent's own.
 */
internal fun SceneNavigationAgent.toAgent(): NavigationAgent {
    val follower = NavPathFollower(NavAgent(radius = radius)).also {
        it.arriveDistance = arriveDistance
        it.waypointRadius = waypointRadius
        it.repathInterval = repathInterval
        it.stuckSeconds = stuckSeconds
        it.stuckDistance = stuckDistance
    }
    val start = if (destinationX != null && destinationZ != null) Vec3f(destinationX, 0f, destinationZ) else null
    start?.let(follower::moveTo)
    return NavigationAgent(follower, speed, start)
}

internal fun NavigationAgent.toScene(): SceneNavigationAgent = SceneNavigationAgent(
    radius = follower.agent.radius,
    speed = speed,
    arriveDistance = follower.arriveDistance,
    waypointRadius = follower.waypointRadius,
    repathInterval = follower.repathInterval,
    stuckSeconds = follower.stuckSeconds,
    stuckDistance = follower.stuckDistance,
    destinationX = startDestination?.x,
    destinationZ = startDestination?.z,
)
