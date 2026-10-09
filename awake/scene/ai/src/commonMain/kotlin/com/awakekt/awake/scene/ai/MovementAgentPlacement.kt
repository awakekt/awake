/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.AgentPlacement
import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.ai.behavior.FleeBehavior
import com.awakekt.awake.ai.behavior.PatrolBehavior
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import kotlin.math.sqrt

/**
 * Places an agent by its scene `Transform`, and moves an agent whose [MovementControl] has an
 * [MovementDriver.Agent] driver through that control, so whatever moves the control moves the agent.
 *
 * With a character controller that means walls stop the agent and slopes and steps carry it, where
 * [TransformAgentPlacement] walks it through them. Steering sets the control's world-space intent and
 * its [MovementControl.moveSpeed] to the behaviour's speed: the behaviour's speed wins over
 * `movement_control.speed`, so an agent can patrol slowly and chase fast, and the authored speed is
 * left as it was. The agent moves on the controller's next steps, not at once.
 *
 * An agent with no control, or one the player drives, moves by its transform as
 * [TransformAgentPlacement] moves it. Run [AgentIntentResetSystem] before the behaviours each frame,
 * so an agent that no behaviour steers stops.
 */
object MovementAgentPlacement : AgentPlacement {
    override fun position(world: World, entity: Entity, into: Vec3f): Boolean =
        TransformAgentPlacement.position(world, entity, into)

    override fun moveBy(world: World, entity: Entity, dx: Float, dz: Float) {
        val control = world.agentControl(entity) ?: return TransformAgentPlacement.moveBy(world, entity, dx, dz)
        // A distance with no time: keep the direction and leave the speed to the control.
        val length = sqrt(dx * dx + dz * dz)
        if (length == 0f) return
        control.moveX = dx / length
        control.moveZ = dz / length
    }

    override fun steer(world: World, entity: Entity, velocityX: Float, velocityZ: Float, delta: Float) {
        val control = world.agentControl(entity) ?: return super.steer(world, entity, velocityX, velocityZ, delta)
        val speed = sqrt(velocityX * velocityX + velocityZ * velocityZ)
        if (speed == 0f) return
        control.moveX = velocityX / speed
        control.moveZ = velocityZ / speed
        control.moveSpeed = speed
    }

    private fun World.agentControl(entity: Entity): MovementControl? =
        get<MovementControl>(entity)?.takeIf { it.driver == MovementDriver.Agent }
}

/**
 * Clears the intent of every agent a behaviour steers through [MovementAgentPlacement]: an
 * [MovementDriver.Agent] [MovementControl] on an entity with a [PatrolBehavior], [ChaseBehavior] or
 * [FleeBehavior].
 *
 * Run it each frame before the behaviours. A behaviour that stops steering, because its agent
 * arrived or lost its target, then stops the agent, instead of leaving the last intent to walk it on.
 * An agent another driver moves, such as a network or a script, keeps its intent.
 */
class AgentIntentResetSystem : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(MovementControl::class) { entity, control ->
            if (control.driver != MovementDriver.Agent || !world.hasBehaviour(entity)) return@queryEach
            control.moveX = 0f
            control.moveZ = 0f
            control.moveSpeed = null
        }
    }

    private fun World.hasBehaviour(entity: Entity): Boolean =
        get<PatrolBehavior>(entity) != null || get<ChaseBehavior>(entity) != null || get<FleeBehavior>(entity) != null
}
