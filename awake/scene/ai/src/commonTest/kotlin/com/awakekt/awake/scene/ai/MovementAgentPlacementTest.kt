/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.ai

import com.awakekt.awake.ai.behavior.ChaseBehavior
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovementAgentPlacementTest {
    @Test
    fun anAgentDrivenControlIsSteeredAndTheTransformLeftToWhateverMovesIt() {
        val world = World()
        val (agent, transform, control) = world.agent(MovementDriver.Agent, speed = 2f)

        MovementAgentPlacement.steer(world, agent, velocityX = 3f, velocityZ = -4f, delta = 0.5f)

        assertEquals(0.6f, control.moveX)
        assertEquals(-0.8f, control.moveZ)
        assertEquals(5f, control.currentSpeed(DEFAULT_SPEED), "the behaviour's speed wins")
        assertEquals(2f, control.speed, "and the authored speed is left as it was")
        assertEquals(Vec3f(1f, 0f, 1f), transform.position, "the controller moves it, on its next step")
    }

    @Test
    fun anAgentThePlayerDrivesMovesByItsTransform() {
        val world = World()
        val (agent, transform, control) = world.agent(MovementDriver.Player)

        MovementAgentPlacement.steer(world, agent, velocityX = 4f, velocityZ = 0f, delta = 0.5f)

        assertEquals(Vec3f(3f, 0f, 1f), transform.position)
        assertEquals(0f, control.moveX, "the player's intent is not taken over")
    }

    @Test
    fun anAgentWithNoControlMovesByItsTransform() {
        val world = World()
        val agent = world.create()
        val transform = Transform(position = Vec3f(1f, 0f, 1f))
        world.add(agent, transform)

        MovementAgentPlacement.steer(world, agent, velocityX = 0f, velocityZ = 2f, delta = 0.5f)

        assertEquals(Vec3f(1f, 0f, 2f), transform.position)
        val into = Vec3f(0f, 0f, 0f)
        assertTrue(MovementAgentPlacement.position(world, agent, into))
        assertEquals(Vec3f(1f, 0f, 2f), into)
    }

    @Test
    fun aDistanceWithNoTimeSetsTheDirectionAndLeavesTheSpeedToTheControl() {
        val world = World()
        val (agent, _, control) = world.agent(MovementDriver.Agent, speed = 2f)

        MovementAgentPlacement.moveBy(world, agent, 3f, 4f)

        assertEquals(0.6f, control.moveX)
        assertEquals(0.8f, control.moveZ)
        assertNull(control.moveSpeed)
    }

    @Test
    fun eachFrameStartsWithTheBehaviourAgentsStopped() {
        val world = World()
        val (chaser, _, chaserControl) = world.agent(MovementDriver.Agent)
        world.add(chaser, ChaseBehavior())
        val (_, _, networked) = world.agent(MovementDriver.Agent)
        val (player, _, playerControl) = world.agent(MovementDriver.Player)
        world.add(player, ChaseBehavior())
        listOf(chaserControl, networked, playerControl).forEach {
            it.moveX = 1f
            it.moveZ = 1f
        }
        chaserControl.moveSpeed = 5f

        AgentIntentResetSystem().update(world, 1f / 60f)

        assertEquals(0f, chaserControl.moveX)
        assertEquals(0f, chaserControl.moveZ)
        assertNull(chaserControl.moveSpeed)
        assertEquals(1f, networked.moveX, "an agent no behaviour steers keeps the intent its own driver set")
        assertEquals(1f, playerControl.moveX, "the player's intent is the player's")
    }

    private data class Agent(val entity: Entity, val transform: Transform, val control: MovementControl)

    private fun World.agent(driver: MovementDriver, speed: Float? = null): Agent {
        val entity = create()
        val transform = Transform(position = Vec3f(1f, 0f, 1f))
        val control = MovementControl().also {
            it.driver = driver
            it.speed = speed
        }
        add(entity, transform)
        add(entity, control)
        return Agent(entity, transform, control)
    }

    private companion object {
        const val DEFAULT_SPEED = 1f
    }
}
