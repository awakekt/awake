/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunAndTurnTest {
    private val world = World()
    private val transform = Transform()
    private val control = MovementControl().apply {
        speed = 2f
        runSpeed = 6f
        turnSpeed = (2 * PI).toFloat()
        moveZ = 1f
    }
    private val system = MatrixRelativeMovementSystem()

    init {
        world.create().also {
            world.add(it, transform)
            world.add(it, control)
        }
    }

    @Test
    fun runningMovesAtTheRunSpeedAndWalkingAtTheSpeed() {
        repeat(STEPS) { system.update(world, STEP) }
        assertEquals(-2f, transform.position.z, TOLERANCE, "a second's walk forward (-Z) at 2 u/s")

        control.run = true
        repeat(STEPS) { system.update(world, STEP) }
        assertEquals(-8f, transform.position.z, TOLERANCE, "then a second's run at 6 u/s")
    }

    @Test
    fun itTurnsToFaceWhereItMovesAtTheTurnSpeed() {
        system.update(world, STEP)
        val firstStep = abs(transform.rotation.y)
        assertEquals((2 * PI / 60).toFloat(), firstStep, TOLERANCE, "one frame turns turnSpeed * delta")

        repeat(STEPS) { system.update(world, STEP) }
        assertTrue(abs(abs(transform.rotation.y) - PI.toFloat()) < TOLERANCE, "moving -Z it ends facing -Z (yaw pi); ${transform.rotation.y}")
    }

    @Test
    fun noTurnSpeedLeavesItsFacingAlone() {
        control.turnSpeed = 0f
        repeat(STEPS) { system.update(world, STEP) }
        assertEquals(0f, transform.rotation.y)
    }

    private companion object {
        const val STEP = 1f / 60f
        const val STEPS = 60
        const val TOLERANCE = 1e-3f
    }
}
