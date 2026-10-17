/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.input.InputDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [CanvasControls]: touch controls shown by the device last used, let go of when hidden, and big enough for a thumb. */
class CanvasControlsTest {
    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()
    private var controls: CanvasControls? = null
    private var device: InputDevice? = null
    private var hostSaysTouch = false

    private fun element(kind: CanvasElementKind, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind; offsetX = 0f; offsetY = 0f; touchOnly = true }.apply(configure)
            .also { items += CanvasItem(items.size + 1, it) }

    private fun frame(vararg touches: PointerFrame): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) {
            CanvasOverlay(items, showTouchControls = hostSaysTouch, controls = controls, inputDevice = device)
        }

    private fun drawn(element: CanvasElement): Boolean = frame().semantics.findTag("canvas-element-${items.indexOfFirst { it.element === element } + 1}") != null

    @Test
    fun autoShowsTouchControlsByTheDeviceLastUsed() {
        val stick = element(CanvasElementKind.Joystick) { width = 100f; height = 100f }
        controls = CanvasControls(touchVisibility = CanvasTouchVisibility.Auto)

        hostSaysTouch = true
        assertTrue(drawn(stick), "until anything is used, as the host says")
        device = InputDevice.Controller
        assertFalse(drawn(stick), "a controller hides them")
        device = InputDevice.Touch
        assertTrue(drawn(stick), "a touch shows them again")
        device = InputDevice.KeyboardMouse
        assertFalse(drawn(stick))
    }

    @Test
    fun alwaysAndNeverOverrideTheHost() {
        assertTrue(CanvasTouchVisibility.Always.shows(host = false, last = InputDevice.KeyboardMouse))
        assertFalse(CanvasTouchVisibility.Never.shows(host = true, last = InputDevice.Touch))
        assertTrue(CanvasTouchVisibility.Host.shows(host = true, last = InputDevice.Controller))
    }

    @Test
    fun controlsHiddenMidPressLetGoOfTheirPointers() {
        val stick = element(CanvasElementKind.Joystick) { width = 100f; height = 100f; joystick = CanvasJoystick() }
        val button = element(CanvasElementKind.Button) { anchor = CanvasAnchor.TopRight; width = 80f; height = 80f; button = CanvasButton() }
        hostSaysTouch = true
        frame()
        frame(PointerFrame(1, 50, 50, down = true, pressed = true), PointerFrame(2, WIDTH - 40, 40, down = true, pressed = true))
        frame(PointerFrame(1, 75, 50, down = true), PointerFrame(2, WIDTH - 40, 40, down = true))
        assertEquals(0.5f, stick.stickX, TOLERANCE)
        assertTrue(button.isHeld)
        button.consumeDown()

        hostSaysTouch = false
        frame(PointerFrame(1, 75, 50, down = true), PointerFrame(2, WIDTH - 40, 40, down = true))

        assertEquals(0f, stick.stickX, "a hidden stick lets go")
        assertFalse(button.isHeld, "a hidden button lets go")
        assertFalse(button.consumeRelease(), "without reporting a release it never had")
        hostSaysTouch = true
        frame(PointerFrame(1, 75, 50, down = false, released = true), PointerFrame(2, WIDTH - 40, 40, down = false, released = true))
        frame(PointerFrame(3, 50, 50, down = true, pressed = true))
        assertTrue(stick.isHeld, "shown again, it takes a new thumb")
    }

    @Test
    fun aSmallButtonTakesATouchWithinTheMinimumTouchSize() {
        val button = element(CanvasElementKind.Button) { offsetX = 100f; offsetY = 100f; width = 20f; height = 20f; button = CanvasButton() }
        hostSaysTouch = true
        frame()

        // 10 dp left of a 20 dp button: inside the 44 dp target around its centre.
        frame(PointerFrame(1, 92, 110, down = true, pressed = true))
        frame(PointerFrame(1, 92, 110, down = false, released = true))
        assertTrue(button.consumePress(), "a touch 8 dp outside a 20 dp button still lands")

        controls = CanvasControls(minTouchSize = 0f)
        frame()
        frame(PointerFrame(1, 92, 110, down = true, pressed = true))
        frame(PointerFrame(1, 92, 110, down = false, released = true))
        assertFalse(button.consumePress(), "with no minimum, only what is drawn takes it")
    }

    @Test
    fun settingsReportTheirProblems() {
        assertTrue(CanvasControls().problems().isEmpty())
        assertEquals(listOf("minTouchSize must not be negative"), CanvasControls(minTouchSize = -1f).problems())
    }

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val TOLERANCE = 0.01f
    }
}
