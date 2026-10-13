/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.ScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val GLFW_KEY_W = 87
private const val GLFW_KEY_SPACE = 32

/** [Input] is a per-session instance now (no longer a global object) -- each test
 * constructs its own, so there's no shared state to reset between tests. */
class GlfwInputBridgeTest {

    @Test
    fun heldGameplayKeyReachesInput() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_W }
        pollGlfwInput(reader, input)
        assertTrue(input.isKeyDown(Key.W), "a held GLFW key must translate to Input.isKeyDown")

        reader.keysDown -= GLFW_KEY_W
        pollGlfwInput(reader, input)
        assertFalse(input.isKeyDown(Key.W), "releasing the GLFW key must clear Input.isKeyDown next poll")
    }

    @Test
    fun pointerScalesByFramebufferRatio() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply {
            mouseDown = true
            cursorXValue = 100.0
            cursorYValue = 50.0
            scaleX = 2f
            scaleY = 2f
        }
        pollGlfwInput(reader, input)
        assertTrue(input.pointerDown)
        assertEquals(200f, input.pointerX, "HiDPI framebuffer scale must be applied to cursor x")
        assertEquals(100f, input.pointerY, "HiDPI framebuffer scale must be applied to cursor y")
    }

    @Test
    fun rightButtonDrivesTheSecondaryPointerNotThePrimaryOne() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { secondaryMouseDown = true }
        pollGlfwInput(reader, input)
        assertTrue(
            input.updateSnapshot().secondaryPointerDown,
            "GLFW button 1 must reach Input.secondaryPointerDown -- shadcnContextMenu gates on it",
        )
        assertFalse(input.pointerDown, "a right-click must not also register as a left click")
    }

    @Test
    fun scrollDeltaPassesThroughUnconsumed() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { pendingScrollDeltaY = -3.5 }
        pollGlfwInput(reader, input)
        assertEquals(-3.5f, input.scrollDeltaY, "a scroll tick must reach Input.scrollDeltaY for the UI/camera layer to consume")
    }

    @Test
    fun sidewaysScrollReachesInputScrollDeltaX() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply {
            pendingScrollDeltaX = 2.25
            pendingScrollDeltaY = 0.5
        }

        pollGlfwInput(reader, input)
        pollGlfwInput(reader, input)

        assertEquals(2.25f, input.scrollDeltaX, "a trackpad's sideways swipe must reach Input.scrollDeltaX, once")
        assertEquals(0.5f, input.scrollDeltaY)
        assertEquals(2.25f, input.updateSnapshot().scrollDeltaX, "and the frame's snapshot")
    }

    @Test
    fun theScrollSourceReachesTheSnapshotForOneFrame() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply {
            pendingScrollDeltaY = 0.3
            pendingScrollSource = ScrollSource.Trackpad
        }

        pollGlfwInput(reader, input)
        pollGlfwInput(reader, input)

        assertEquals(ScrollSource.Trackpad, input.updateSnapshot().scrollSource, "a later poll that learned nothing keeps it")
        assertEquals(ScrollSource.Unknown, input.updateSnapshot().scrollSource, "and it lasts one frame, like the deltas")
    }

    @Test
    fun everyFunctionKeyF1ThroughF12MapsToItsGlfwCode() {
        val input = Input()
        val reader = FakeGlfwWindowInput()
        // GLFW's own codes, independent of DefaultGlfwGameplayKeys's private constants, so a
        // mapping removed from the table -- not merely renumbered -- still fails this.
        val expected = mapOf(
            290 to Key.F1, 291 to Key.F2, 292 to Key.F3, 293 to Key.F4, 294 to Key.F5,
            295 to Key.F6, 296 to Key.F7, 297 to Key.F8, 298 to Key.F9,
            299 to Key.F10, 300 to Key.F11, 301 to Key.F12,
        )

        expected.forEach { (glfwKey, key) ->
            assertEquals(key, DefaultGlfwGameplayKeys[glfwKey], "GLFW key code $glfwKey must map to $key")
            reader.keysDown.clear()
            reader.keysDown += glfwKey
            pollGlfwInput(reader, input)
            assertTrue(input.isKeyDown(key), "GLFW key code $glfwKey must map to $key")
        }
    }

    @Test
    fun unmappedKeyIsIgnored() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_SPACE }
        pollGlfwInput(reader, input, keys = mapOf(GLFW_KEY_W to Key.W))
        assertFalse(input.isKeyDown(Key.Space), "a key not present in the keys map must not set any Input key")
    }
}
