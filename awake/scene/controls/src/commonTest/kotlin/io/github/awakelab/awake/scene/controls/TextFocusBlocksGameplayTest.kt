/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls

import io.github.awakelab.awake.compose.ui.platform.InputOwnership
import io.github.awakelab.awake.compose.ui.platform.blocksGameplayKeys
import io.github.awakelab.awake.core.input.Input
import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.controls.camera.ActiveCamera
import io.github.awakelab.awake.scene.controls.camera.CameraInputSystem
import io.github.awakelab.awake.scene.controls.camera.CameraMode
import io.github.awakelab.awake.scene.controls.camera.CameraRig
import io.github.awakelab.awake.scene.controls.movement.MovementControl
import io.github.awakelab.awake.scene.controls.movement.PlayerInputSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the defect where typing into a focused text field also drove the game: ownership was
 * checked with `isCaptured`, which is pointer capture only, so "wasd" both inserted characters
 * and walked the player while the camera hotkeys kept firing.
 */
class TextFocusBlocksGameplayTest {
    @Test
    fun blocksGameplayKeysCoversPointerCaptureAndTextFocus() {
        assertFalse(InputOwnership().blocksGameplayKeys, "an idle UI must not block gameplay")
        assertTrue(InputOwnership(isCaptured = true).blocksGameplayKeys)
        assertTrue(InputOwnership(isTextInputFocused = true).blocksGameplayKeys)
        assertTrue(
            InputOwnership(isCaptured = true, isTextInputFocused = true).blocksGameplayKeys,
        )
    }

    @Test
    fun typingIntoAFocusedFieldDoesNotMoveThePlayer() {
        val world = World()
        val subject = world.create()
        world.add(subject, MovementControl().apply { moveZ = 1f })

        PlayerInputSystem(
            inputProvider = { GameplayInput(snapshotWith(Key.W), InputOwnership(isTextInputFocused = true)) },
        ).update(world, DELTA)

        val control = world.get(subject, MovementControl::class)!!
        assertEquals(0f, control.moveZ, "W typed into a text field must not walk the player")
        assertEquals(0f, control.moveX)
    }

    @Test
    fun holdingWithoutUiOwnershipStillMovesThePlayer() {
        val world = World()
        val subject = world.create()
        world.add(subject, MovementControl())

        PlayerInputSystem(
            inputProvider = { GameplayInput(snapshotWith(Key.W), InputOwnership()) },
        ).update(world, DELTA)

        assertEquals(1f, world.get(subject, MovementControl::class)!!.moveZ)
    }

    @Test
    fun cameraHotkeyIsIgnoredWhileTextInputIsFocused() {
        val world = World()
        val config = activeCamera(world)
        var focused = true

        val input = Input()
        val system = CameraInputSystem(
            inputProvider = { GameplayInput(input.currentSnapshot, InputOwnership(isTextInputFocused = focused)) },
        )

        input.setKeyDown(Key.F2, down = true)
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(
            CameraMode.FirstPerson,
            config.mode,
            "F2 typed into a field must not switch mode",
        )

        // Releasing focus while the key is still held must not replay the press: the edge was
        // spent on the blocked frame, which is exactly what the deleted lastKeysDown copy had
        // to remember to do by hand.
        focused = false
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(CameraMode.FirstPerson, config.mode, "a still-held key must not re-fire")

        input.setKeyDown(Key.F2, down = false)
        input.updateSnapshot()
        input.setKeyDown(Key.F2, down = true)
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(CameraMode.ThirdPerson, config.mode, "a fresh press must switch mode")
    }

    @Test
    fun dragOutsideViewportBoundsDoesNotOrbitCamera() {
        val world = World()
        val config = activeCamera(world)
        val initialYaw = config.yaw
        val viewport = io.github.awakelab.awake.core.math2d.Rectangle(x = 200f, y = 100f, width = 600f, height = 400f)

        var pointerX = 50f
        var pointerY = 200f
        var pointerDown = true

        val system = io.github.awakelab.awake.scene.controls.camera.CameraSystem(
            inputProvider = {
                GameplayInput(
                    InputSnapshot(
                        pointerX = pointerX,
                        pointerY = pointerY,
                        pointerDown = pointerDown,
                        scrollDeltaX = 0f,
                        scrollDeltaY = 0f,
                        keysDown = emptySet(),
                        keysPressed = emptySet(),
                        keysReleased = emptySet(),
                        typedText = "",
                        editActions = emptyList(),
                    ),
                    InputOwnership(),
                )
            },
            viewportBounds = { viewport },
        )

        // First frame: press starts outside viewport
        system.update(world, DELTA)
        // Second frame: drag moves outside viewport
        pointerX = 80f
        system.update(world, DELTA)

        assertEquals(initialYaw, config.yaw, "dragging outside viewport bounds must not orbit camera")

        // Now release and press INSIDE viewport
        pointerX = 300f
        pointerY = 250f
        pointerDown = false
        system.update(world, DELTA)

        pointerDown = true
        system.update(world, DELTA)
        pointerX = 350f
        system.update(world, DELTA)

        assertTrue(config.yaw != initialYaw, "dragging inside viewport bounds must orbit camera")
    }

    @Test
    fun scrollingOutsideViewportBoundsMustNotZoomCamera() {
        val world = World()
        val config = activeCamera(world)
        config.mode = CameraMode.ThirdPerson
        config.needsReset = false
        config.distance = 10f
        val initialDistance = config.distance
        val viewport = io.github.awakelab.awake.core.math2d.Rectangle(200f, 100f, 600f, 400f)

        var pointerX = 50f
        var pointerY = 50f
        val scrollDeltaY = 5f

        val system = io.github.awakelab.awake.scene.controls.camera.CameraSystem(
            inputProvider = {
                GameplayInput(
                    InputSnapshot(
                        pointerX = pointerX,
                        pointerY = pointerY,
                        pointerDown = false,
                        scrollDeltaX = 0f,
                        scrollDeltaY = scrollDeltaY,
                        keysDown = emptySet(),
                        keysPressed = emptySet(),
                        keysReleased = emptySet(),
                        typedText = "",
                        editActions = emptyList(),
                    ),
                    InputOwnership(),
                )
            },
            viewportBounds = { viewport },
        )

        system.update(world, DELTA)
        assertEquals(initialDistance, config.distance, "scrolling outside viewport bounds must not zoom camera")

        // Now scroll inside viewport
        pointerX = 300f
        pointerY = 200f
        system.update(world, DELTA)
        assertTrue(config.distance < initialDistance, "scrolling inside viewport bounds must zoom camera")
    }

    @Test
    fun modalLayerBlocksPointerClicksAndScrollAndKeys() {
        val snapshot = InputSnapshot(
            pointerX = 300f,
            pointerY = 200f,
            pointerDown = true,
            scrollDeltaX = 0f,
            scrollDeltaY = 2f,
            keysDown = setOf(Key.W),
            keysPressed = setOf(Key.W),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )

        val inputWithModal = GameplayInput(snapshot, InputOwnership(isModalOpen = true))
        assertFalse(inputWithModal.pointerDown, "modal open must suppress pointerDown")
        assertFalse(
            inputWithModal.isDown(io.github.awakelab.awake.core.input.PointerButton.Primary),
            "modal open must suppress isDown(button)",
        )
        assertEquals(0f, inputWithModal.scrollDeltaY, "modal open must suppress scrollDeltaY")
        assertFalse(inputWithModal.isDown(Key.W), "modal open must suppress isDown(key)")
        assertFalse(inputWithModal.wasPressed(Key.W), "modal open must suppress wasPressed(key)")
        assertTrue(inputWithModal.keysOwnedByUi, "modal open must own keys for UI")
        assertTrue(inputWithModal.isModalOpen, "modal open flag must be true")
    }

    private fun activeCamera(world: World): CameraRig {
        val entity = world.create()
        world.add(
            entity,
            io.github.awakelab.awake.scene.rendering.Camera(
                io.github.awakelab.awake.core.math.Lens.perspective(),
            ),
        )
        val config = CameraRig()
        world.add(entity, config)
        world.add(entity, ActiveCamera())
        return config
    }

    private fun snapshotWith(vararg keys: Key): InputSnapshot = InputSnapshot(
        pointerX = 0f,
        pointerY = 0f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = keys.toSet(),
        keysPressed = keys.toSet(),
        keysReleased = emptySet(),
        typedText = "",
        editActions = emptyList(),
    )

    private companion object {
        const val DELTA = 0.016f
    }
}
