/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A wheel canvas element's frame zeroes `GameplayInput.scrollDeltaY` through `InputOwnership`, the
 * same way a focused `TextField`'s frame blocks gameplay keys: scrolling an inventory must not also
 * zoom the camera.
 */
class CanvasWheelBlocksGameplayTest {

    private fun snapshot(scrollDeltaY: Float) = InputSnapshot(
        pointerX = 0f,
        pointerY = 0f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = scrollDeltaY,
        keysDown = emptySet(),
        keysPressed = emptySet(),
        keysReleased = emptySet(),
        typedText = "",
        editActions = emptyList(),
    )

    @Test
    fun scrollDeltaYIsZeroWhenThePointerIsOverAWheelElement() {
        val world = World()
        val panel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            wheel = true
            offsetX = 10f
            offsetY = 10f
            width = 100f
            height = 100f
        }
        world.add(world.create(), panel)
        val host = ComposeHost()
        host.frame(FrameInput(200, 200)) { SceneCanvas(world) } // places the tree.

        val output = host.frame(FrameInput(200, 200, pointerX = 40, pointerY = 40, scrollDeltaY = -2f)) { SceneCanvas(world) }
        assertTrue(output.ownership.isScrollConsumed, "the wheel element must actually take the wheel for this to prove anything")

        val input = GameplayInput(snapshot(-2f), output.ownership)

        assertEquals(0f, input.scrollDeltaY, "a wheel over the panel must not also reach gameplay")
    }

    @Test
    fun scrollDeltaYKeepsTheDeltaWhenThePointerIsOverNoWheelElement() {
        val world = World()
        val panel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            wheel = true
            offsetX = 10f
            offsetY = 10f
            width = 100f
            height = 100f
        }
        world.add(world.create(), panel)
        val host = ComposeHost()
        host.frame(FrameInput(200, 200)) { SceneCanvas(world) } // places the tree.

        // Far outside the panel's box.
        val output = host.frame(FrameInput(200, 200, pointerX = 190, pointerY = 190, scrollDeltaY = -2f)) { SceneCanvas(world) }

        val input = GameplayInput(snapshot(-2f), output.ownership)

        assertNotEquals(0f, input.scrollDeltaY, "the wheel away from any wheel element must still reach gameplay")
        assertEquals(-2f, input.scrollDeltaY)
    }
}
