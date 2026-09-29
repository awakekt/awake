/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.JumpSystem
import com.awakekt.awake.scene.controls.movement.MatrixRelativeMovementSystem
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterMovementTest {
    @Test
    fun aJumpRisesAndLandsWhereItStarted() {
        val world = World()
        val player = world.create()
        val transform = Transform().apply { position.set(0f, 0.5f, 0f) }
        val control = MovementControl().apply { jump = true }
        world.add(player, transform)
        world.add(player, control)
        val jump = JumpSystem()

        jump.update(world, DELTA)
        control.jump = false
        var peak = transform.position.y
        repeat(STEPS) {
            jump.update(world, DELTA)
            peak = maxOf(peak, transform.position.y)
        }

        assertTrue(peak > 1f, "the jump only reached $peak")
        assertEquals(0.5f, transform.position.y, "the player must land back on the floor it stood on")
    }

    @Test
    fun holdingJumpInTheAirDoesNotJumpAgain() {
        val world = World()
        val player = world.create()
        val transform = Transform()
        val control = MovementControl().apply { jump = true }
        world.add(player, transform)
        world.add(player, control)
        val jump = JumpSystem()

        var peak = 0f
        // Held for less than one flight: a mid-air re-jump would push the peak past a single jump's.
        repeat(STEPS / 4) {
            jump.update(world, DELTA)
            peak = maxOf(peak, transform.position.y)
        }
        control.jump = false
        repeat(STEPS) {
            jump.update(world, DELTA)
            peak = maxOf(peak, transform.position.y)
        }

        val singleJumpPeak = JumpSystem.DEFAULT_JUMP_VELOCITY * JumpSystem.DEFAULT_JUMP_VELOCITY /
            (2 * -JumpSystem.DEFAULT_GRAVITY)
        assertTrue(abs(peak - singleJumpPeak) < PEAK_TOLERANCE, "peak $peak, one jump reaches $singleJumpPeak")
    }

    @Test
    fun anEntitysOwnSpeedOverridesTheSystemSpeed() {
        val world = World()
        val player = world.create()
        val transform = Transform()
        world.add(player, transform)
        world.add(player, MovementControl().apply { moveZ = 1f; speed = 6f })

        MatrixRelativeMovementSystem(speed = 2f).update(world, 1f)

        assertEquals(6f, abs(transform.position.z), 1e-4f)
    }

    private companion object {
        const val DELTA = 1f / 60f
        const val STEPS = 120
        const val PEAK_TOLERANCE = 0.1f
    }
}
