/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls

import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.core.math.CameraPoseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraInputProcessorTest {

    @Test
    fun primaryDragOrbitsYawAndPitch() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 50f)
        val config = CameraControlConfig(orbitSensitivity = 0.01f)

        // First frame down sets anchor
        val snap1 = InputSnapshot(
            pointerX = 100f, pointerY = 100f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = emptySet(), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        assertFalse(processor.process(state, config, snap1, 0.016f))

        // Second frame dragged right (+50px) and down (+20px)
        val snap2 = InputSnapshot(
            pointerX = 150f, pointerY = 120f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = emptySet(), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        assertTrue(processor.process(state, config, snap2, 0.016f))
        assertEquals(0.5f, state.yaw, 1e-4f)
        assertEquals(-0.2f, state.pitch, 1e-4f)
    }

    @Test
    fun scrollWheelZoomsExponentially() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 100f)
        val config = CameraControlConfig(zoomRate = 0.1f)

        val zoomSnap = InputSnapshot(
            pointerX = 0f, pointerY = 0f, pointerDown = false, scrollDeltaX = 0f, scrollDeltaY = 2.0f,
            keysDown = emptySet(), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        assertTrue(processor.process(state, config, zoomSnap, 0.016f))
        assertTrue(state.distance < 100f, "Positive scroll must zoom in (decrease distance).")
    }

    @Test
    fun shiftDragAppliesScreenSpacePan() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 50f)
        val config = CameraControlConfig(panScale = 0.002f)

        val snap1 = InputSnapshot(
            pointerX = 100f, pointerY = 100f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = setOf(Key.Shift), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        processor.process(state, config, snap1, 0.016f)

        val snap2 = InputSnapshot(
            pointerX = 150f, pointerY = 100f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = setOf(Key.Shift), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        assertTrue(processor.process(state, config, snap2, 0.016f))
        assertTrue(state.center.x < 0f, "Shift + Right Drag must pan target center left in screen space.")
    }

    /**
     * The other half of the same basis. `right` was negated, which flipped `up` with it, so both
     * pan axes were inverted and only the horizontal one was covered.
     */
    @Test
    fun shiftDragDownPansTargetCenterUp() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 50f)
        val config = CameraControlConfig(panScale = 0.002f)

        val start = InputSnapshot(
            pointerX = 100f, pointerY = 100f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = setOf(Key.Shift), keysPressed = emptySet(), keysReleased = emptySet(),
            typedText = "", editActions = emptyList(),
        )
        processor.process(state, config, start, 0.016f)

        val draggedDown = InputSnapshot(
            pointerX = 100f, pointerY = 150f, pointerDown = true, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = setOf(Key.Shift), keysPressed = emptySet(), keysReleased = emptySet(),
            typedText = "", editActions = emptyList(),
        )

        assertTrue(processor.process(state, config, draggedDown, 0.016f))
        assertTrue(
            state.center.y > 0f,
            "Dragging down must lift the target so the scene follows the cursor downward.",
        )
    }

    @Test
    fun keyboardWasdMovesTargetAlongOrientation() {
        val processor = CameraInputProcessor()
        val state = CameraPoseState(yaw = 0f, pitch = 0f, distance = 50f)
        val config = CameraControlConfig(baseMoveSpeed = 100f)

        val wasdSnap = InputSnapshot(
            pointerX = 0f, pointerY = 0f, pointerDown = false, scrollDeltaX = 0f, scrollDeltaY = 0f,
            keysDown = setOf(Key.W), keysPressed = emptySet(), keysReleased = emptySet(), typedText = "", editActions = emptyList(),
        )
        assertTrue(processor.process(state, config, wasdSnap, delta = 0.1f))
        assertTrue(state.center.z < 0f, "W key must glide target center forward (-Z when facing yaw=0).")
    }
}
