/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/** Two presses on a Button within [CanvasElement.DOUBLE_CLICK_SECONDS] make one double-click. */
class CanvasDoubleClickTest {
    private val world = World()
    private val host = ComposeHost()
    private val clock = TestTimeSource()
    private val button = CanvasElement().apply {
        kind = CanvasElementKind.Button
        offsetX = 10f
        offsetY = 10f
        width = 40f
        height = 20f
        doubleAction = "equip"
        clock = this@CanvasDoubleClickTest.clock
    }.also { world.add(world.create(), it) }

    private fun frame(pressed: Boolean = false, down: Boolean = false, released: Boolean = false) {
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = 30, pointerY = 20, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }
    }

    /** A mouse click on the button: the pointer arrives, goes down and comes up. */
    private fun click() {
        frame()
        frame(pressed = true, down = true)
        frame(released = true)
    }

    @Test
    fun twoQuickClicksAreADoubleClickAndStillTwoPresses() {
        click()
        clock += 200.milliseconds
        click()

        assertTrue(button.consumeDoublePress())
        assertFalse(button.consumeDoublePress(), "once per double-click")
        assertTrue(button.consumePress(), "the second click is still a press")
    }

    @Test
    fun clicksFurtherApartAreNot() {
        click()
        clock += 600.milliseconds
        click()

        assertFalse(button.consumeDoublePress())
    }

    @Test
    fun aThirdQuickClickStartsANewPair() {
        click()
        clock += 100.milliseconds
        click()
        assertTrue(button.consumeDoublePress())
        clock += 100.milliseconds
        click()

        assertFalse(button.consumeDoublePress(), "the third click is the first of the next pair")
        clock += 100.milliseconds
        click()
        assertTrue(button.consumeDoublePress())
    }

    @Test
    fun aSavedElementKeepsItsDoubleAction() {
        val saved = with(CanvasElementBinding) { button.toSceneComponent() }

        assertEquals("equip", saved.doubleAction)
        assertEquals("equip", with(CanvasElementBinding) { saved.toComponent() }.doubleAction)
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 100
    }
}
