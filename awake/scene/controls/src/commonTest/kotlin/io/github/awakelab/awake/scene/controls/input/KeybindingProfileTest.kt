/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls.input

import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.core.math.Vec3f
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KeybindingProfileTest {

    private enum class TestAction {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        JUMP,
        SPRINT,
    }

    private fun snapshot(
        down: Set<Key> = emptySet(),
        pressed: Set<Key> = emptySet(),
        released: Set<Key> = emptySet(),
    ) = InputSnapshot(
        pointerX = 0f,
        pointerY = 0f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = down,
        keysPressed = pressed,
        keysReleased = released,
        typedText = "",
        editActions = emptyList(),
    )

    @Test
    fun dslBuildsProfileWithPrimaryAndSecondaryKeys() {
        val profile = keybindingProfile<TestAction> {
            bind(TestAction.FORWARD, Key.W, Key.ArrowUp)
            bind(TestAction.JUMP, Key.Space)
        }

        val fwd = profile.getBinding(TestAction.FORWARD)
        assertNotNull(fwd)
        assertEquals(Key.W, fwd.primary)
        assertEquals(Key.ArrowUp, fwd.secondary)

        val jump = profile.getBinding(TestAction.JUMP)
        assertNotNull(jump)
        assertEquals(Key.Space, jump.primary)
    }

    @Test
    fun isPressedAndIsDownEvaluateCorrectly() {
        val profile = keybindingProfile<TestAction> {
            bind(TestAction.FORWARD, Key.W, Key.ArrowUp)
            bind(TestAction.JUMP, Key.Space)
        }

        val input = snapshot(
            down = setOf(Key.W),
            pressed = setOf(Key.Space),
            released = emptySet(),
        )

        assertTrue(profile.isDown(TestAction.FORWARD, input))
        assertFalse(profile.isDown(TestAction.JUMP, input))
        assertTrue(profile.isPressed(TestAction.JUMP, input))
    }

    @Test
    fun getAxis2DComputesNormalizedDirectionVector() {
        val profile = keybindingProfile<TestAction> {
            bind(TestAction.FORWARD, Key.W)
            bind(TestAction.BACKWARD, Key.S)
            bind(TestAction.LEFT, Key.A)
            bind(TestAction.RIGHT, Key.D)
        }

        // Forward
        val fwd = profile.getAxis2D(TestAction.FORWARD, TestAction.BACKWARD, TestAction.LEFT, TestAction.RIGHT, snapshot(down = setOf(Key.W)))
        assertEquals(0f, fwd.x)
        assertEquals(-1f, fwd.z)

        // Diagonal W + D
        val diag = profile.getAxis2D(TestAction.FORWARD, TestAction.BACKWARD, TestAction.LEFT, TestAction.RIGHT, snapshot(down = setOf(Key.W, Key.D)))
        val expected = (1f / sqrt(2f))
        assertTrue(abs(diag.x - expected) < 1e-4f)
        assertTrue(abs(diag.z - (-expected)) < 1e-4f)

        // Opposing cancel
        val cancel = profile.getAxis2D(TestAction.FORWARD, TestAction.BACKWARD, TestAction.LEFT, TestAction.RIGHT, snapshot(down = setOf(Key.W, Key.S)))
        assertEquals(Vec3f.ZERO, cancel)
    }

    @Test
    fun rebindUpdatesPrimaryAndSecondarySlots() {
        val profile = keybindingProfile<TestAction> {
            bind(TestAction.JUMP, Key.Space)
        }

        profile.rebind(TestAction.JUMP, Key.J)
        assertTrue(profile.isPressed(TestAction.JUMP, snapshot(pressed = setOf(Key.J))))
        assertFalse(profile.isPressed(TestAction.JUMP, snapshot(pressed = setOf(Key.Space))))

        profile.rebind(TestAction.JUMP, Key.Space, isSecondary = true)
        assertTrue(profile.isPressed(TestAction.JUMP, snapshot(pressed = setOf(Key.Space))))
        assertTrue(profile.isPressed(TestAction.JUMP, snapshot(pressed = setOf(Key.J))))
    }
}
