/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.navigation

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.navigation.grid.NavGroundHeight
import com.awakekt.awake.navigation.walkable.NavPathQuery
import com.awakekt.awake.navigation.walkable.NavSurface
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.atan2

/**
 * Walks every [NavigationAgent] over [surface]: each frame its follower plans or re-plans as it
 * needs, and the agent is steered toward the next corner.
 *
 * An agent with a [MovementControl] whose driver is [MovementDriver.Agent] is steered through it:
 * the system writes the control's world-space intent and sets [MovementControl.moveSpeed] to the
 * agent's speed, so a character controller or the movement system moves it, and a wall it was
 * pushed into shows up as no progress and a re-plan. The intent is cleared when the agent stands
 * still, so an arrived agent stops. Any other agent moves by its [Transform] at its speed and turns to
 * face where it walks.
 *
 * The agent's position is its transform's, which is the world position for an entity with no
 * parent. One [NavPathQuery] serves every agent, on the frame thread, and nothing allocates per
 * frame once the agents' routes have grown to size.
 *
 * @param surface Where agents can stand.
 * @param ground World Y for waypoints.
 * @param maxExpandedNodes The most samples one plan expands.
 */
class NavigationAgentSystem(
    surface: NavSurface,
    ground: NavGroundHeight = NavGroundHeight.FLAT,
    maxExpandedNodes: Int = NavPathQuery.DEFAULT_MAX_EXPANDED_NODES,
) : System {
    private val query = NavPathQuery(surface, ground, maxExpandedNodes)
    private val direction = Vec3f(0f, 0f, 0f)

    override fun update(world: World, delta: Float) {
        // The reified overload: a `Type::class` argument would allocate a class reference every frame.
        world.queryEach<NavigationAgent, Transform> { entity, agent, transform ->
            val walking = agent.follower.update(query, transform.position, delta, direction)
            val control = world.get<MovementControl>(entity)
            if (control != null && control.driver == MovementDriver.Agent) {
                steer(control, walking, agent.speed)
            } else if (walking) {
                transform.position.x += direction.x * agent.speed * delta
                transform.position.z += direction.z * agent.speed * delta
                transform.rotation.y = atan2(direction.x, direction.z)
            }
        }
    }

    private fun steer(control: MovementControl, walking: Boolean, speed: Float) {
        control.moveX = if (walking) direction.x else 0f
        control.moveZ = if (walking) direction.z else 0f
        // moveSpeed is a Float?, so assigning a speed boxes it: write it only when it changes.
        if (!walking) {
            control.moveSpeed = null
        } else if (control.moveSpeed != speed) {
            control.moveSpeed = speed
        }
    }
}
