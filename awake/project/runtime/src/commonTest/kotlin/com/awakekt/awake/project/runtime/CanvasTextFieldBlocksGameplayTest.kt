/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A focused canvas `TextField`'s frame blocks gameplay keys through `GameplayInput`, the same way a
 * focused `BasicTextField` already does: typing "w" to chat must not also walk the player.
 */
class CanvasTextFieldBlocksGameplayTest {
    @Test
    fun aFocusedTextFieldsFrameBlocksGameplayKeysThroughGameplayInput() {
        val world = World()
        val field = CanvasElement().apply { kind = CanvasElementKind.TextField }
        world.add(world.create(), field)
        val host = ComposeHost()
        fun frame() = host.frame(FrameInput(200, 100)) { SceneCanvas(world) }

        frame() // attaches the field's modifier chain.
        field.focused = true
        val output = frame() // the queued request is applied at the start of this frame.
        assertTrue(output.ownership.isTextInputFocused, "the field must actually be focused for this to prove anything")

        val wPressed = InputSnapshot(
            pointerX = 0f,
            pointerY = 0f,
            pointerDown = false,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = setOf(Key.W),
            keysPressed = setOf(Key.W),
            keysReleased = emptySet(),
            typedText = "w",
            editActions = emptyList(),
        )
        val input = GameplayInput(wPressed, output.ownership)

        assertFalse(input.wasPressed(Key.W), "W typed into a focused canvas TextField must not reach gameplay")
        assertFalse(input.isDown(Key.W))
    }
}
