/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.ScrollSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraInputSystem
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
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
    fun aSingleLineFieldLetsUpAndDownReachGameplayButKeepsLettersAndBackspace() {
        // A consumer pack's single-line chat field steps through its sent lines with Up/Down; the
        // field itself has no use for them, so they must not disappear into `blocksGameplayKeys`.
        val ownership = InputOwnership(isTextInputFocused = true, isTextInputSingleLine = true)
        val input = GameplayInput(
            snapshotWith(Key.ArrowUp, Key.ArrowDown, Key.W, Key.Backspace, Key.F1),
            ownership,
        )

        assertTrue(input.isDown(Key.ArrowUp), "a single-line field has no use for Up")
        assertTrue(input.isDown(Key.ArrowDown), "a single-line field has no use for Down")
        assertTrue(input.wasPressed(Key.ArrowUp))
        assertTrue(input.isDown(Key.F1), "a field never handles function keys")
        assertFalse(input.isDown(Key.W), "a single-line field still takes typed letters")
        assertFalse(input.isDown(Key.Backspace), "a single-line field still takes Backspace")
    }

    @Test
    fun aMultilineFieldKeepsUpAndDownForItsOwnCaret() {
        val ownership = InputOwnership(isTextInputFocused = true, isTextInputSingleLine = false)
        val input = GameplayInput(snapshotWith(Key.ArrowUp, Key.ArrowDown), ownership)

        assertFalse(input.isDown(Key.ArrowUp), "a multi-line field moves its caret with Up")
        assertFalse(input.isDown(Key.ArrowDown), "a multi-line field moves its caret with Down")
    }

    @Test
    fun aFieldCanDeclareExtraKeysItLetsThrough() {
        // Escape is taken by default while a field is focused, but a field may give it up too.
        val blocking = InputOwnership(isTextInputFocused = true, isTextInputSingleLine = true)
        val passThrough = blocking.copy(textInputPassThroughKeys = setOf(Key.Escape))

        assertFalse(GameplayInput(snapshotWith(Key.Escape), blocking).isDown(Key.Escape))
        assertTrue(GameplayInput(snapshotWith(Key.Escape), passThrough).isDown(Key.Escape))
    }

    @Test
    fun capturedPointerStillBlocksEveryKeyRegardlessOfTextInput() {
        // Positive control: the blanket block is still in force for capture and modal, even though
        // a focused text field now takes only the keys it uses.
        val captured = GameplayInput(snapshotWith(Key.ArrowUp, Key.F1), InputOwnership(isCaptured = true))
        assertFalse(captured.isDown(Key.ArrowUp), "pointer capture must still block a key no field uses")
        assertFalse(captured.isDown(Key.F1), "pointer capture must still block every key")

        val modal = GameplayInput(snapshotWith(Key.ArrowUp, Key.F1), InputOwnership(isModalOpen = true))
        assertFalse(modal.isDown(Key.ArrowUp), "a modal must still block a key no field uses")
        assertFalse(modal.isDown(Key.F1), "a modal must still block every key")
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
    fun theDefaultCameraHotkeysAreFunctionKeysSoTheyStillFireWhileATextFieldIsFocused() {
        // CameraInputSystem.DEFAULT_MODE_KEYS maps only F1/F2/F4/F5. No text field handles a
        // function key, so #809's per-key rule now lets it through even while a plain field holds
        // focus -- the over-broad blocking this issue fixes.
        val world = World()
        val config = activeCamera(world)
        val input = Input()
        val system = CameraInputSystem(
            inputProvider = { GameplayInput(input.currentSnapshot, InputOwnership(isTextInputFocused = true)) },
        )

        input.setKeyDown(Key.F2, down = true)
        input.updateSnapshot()
        system.update(world, DELTA)

        assertEquals(CameraMode.ThirdPerson, config.mode, "a function-key hotkey must still reach the game")
    }

    @Test
    fun aCameraHotkeyMappedToALetterIsStillIgnoredWhileTextInputIsFocused() {
        // A hotkey an app maps onto a key the field actually uses (a letter) must stay blocked --
        // the original `ui-core` defect this guards.
        val world = World()
        val config = activeCamera(world)
        var focused = true

        val input = Input()
        val system = CameraInputSystem(
            inputProvider = { GameplayInput(input.currentSnapshot, InputOwnership(isTextInputFocused = focused)) },
            modeKeys = mapOf(Key.W to CameraMode.ThirdPerson),
        )

        input.setKeyDown(Key.W, down = true)
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(
            CameraMode.FirstPerson,
            config.mode,
            "W typed into a field must not switch mode",
        )

        // Releasing focus while the key is still held must not replay the press: the edge was
        // spent on the blocked frame, which is exactly what the deleted lastKeysDown copy had
        // to remember to do by hand.
        focused = false
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(CameraMode.FirstPerson, config.mode, "a still-held key must not re-fire")

        input.setKeyDown(Key.W, down = false)
        input.updateSnapshot()
        input.setKeyDown(Key.W, down = true)
        input.updateSnapshot()
        system.update(world, DELTA)
        assertEquals(CameraMode.ThirdPerson, config.mode, "a fresh press must switch mode")
    }

    @Test
    fun dragOutsideViewportBoundsDoesNotOrbitCamera() {
        val world = World()
        val config = activeCamera(world)
        val initialYaw = config.yaw
        val viewport = com.awakekt.awake.core.math2d.Rectangle(x = 200f, y = 100f, width = 600f, height = 400f)

        var pointerX = 50f
        var pointerY = 200f
        var pointerDown = true

        val system = com.awakekt.awake.scene.controls.camera.CameraSystem(
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
        val viewport = com.awakekt.awake.core.math2d.Rectangle(200f, 100f, 600f, 400f)

        var pointerX = 50f
        var pointerY = 50f
        val scrollDeltaY = 5f

        val system = com.awakekt.awake.scene.controls.camera.CameraSystem(
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
            inputWithModal.isDown(com.awakekt.awake.core.input.PointerButton.Primary),
            "modal open must suppress isDown(button)",
        )
        assertEquals(0f, inputWithModal.scrollDeltaY, "modal open must suppress scrollDeltaY")
        assertFalse(inputWithModal.isDown(Key.W), "modal open must suppress isDown(key)")
        assertFalse(inputWithModal.wasPressed(Key.W), "modal open must suppress wasPressed(key)")
        assertTrue(inputWithModal.keysOwnedByUi, "modal open must own keys for UI")
        assertTrue(inputWithModal.isModalOpen, "modal open flag must be true")
    }

    @Test
    fun sidewaysScrollIsGatedLikeVerticalScroll() {
        val snapshot = InputSnapshot(
            pointerX = 300f,
            pointerY = 200f,
            pointerDown = false,
            scrollDeltaX = 1.5f,
            scrollDeltaY = 2f,
            keysDown = emptySet(),
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
        )

        assertEquals(1.5f, GameplayInput(snapshot, InputOwnership()).scrollDeltaX, "an unclaimed swipe reaches the world")
        assertEquals(0f, GameplayInput(snapshot, InputOwnership(isScrollConsumed = true)).scrollDeltaX, "a scrollable under the pointer took it")
        assertEquals(0f, GameplayInput(snapshot, InputOwnership(isModalOpen = true)).scrollDeltaX, "a modal layer took it")
        assertEquals(
            ScrollSource.Trackpad,
            GameplayInput(snapshot.copy(scrollSource = ScrollSource.Trackpad), InputOwnership()).scrollSource,
            "the platform's trackpad-or-wheel answer reaches the world",
        )
    }

    private fun activeCamera(world: World): CameraRig {
        val entity = world.create()
        world.add(
            entity,
            com.awakekt.awake.scene.rendering.Camera(
                com.awakekt.awake.core.math.Lens.perspective(),
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
