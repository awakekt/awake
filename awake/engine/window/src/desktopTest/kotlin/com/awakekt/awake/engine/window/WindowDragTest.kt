/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowDragTest {
    @Test
    fun theWindowFollowsThePointerFromWhereItWasGrabbed() {
        val window = FakeDragWindow(x = 100, y = 200, cursorX = 30.0, cursorY = 10.0)
        val drag = WindowDrag(window)

        drag.update(dragging = true)
        window.pointer(45.0, 4.0)
        drag.update(dragging = true)

        assertEquals(115 to 194, window.position)
    }

    @Test
    fun aWindowThatHasCaughtUpWithThePointerStaysPut() {
        val window = FakeDragWindow(x = 0, y = 0, cursorX = 10.0, cursorY = 10.0)
        val drag = WindowDrag(window)

        drag.update(dragging = true)
        window.pointer(20.0, 10.0)
        drag.update(dragging = true)
        drag.update(dragging = true)

        assertEquals(10 to 0, window.position)
        assertEquals(1, window.moves)
    }

    @Test
    fun theFirstFrameOfADragOnlyGrabs() {
        val window = FakeDragWindow(x = 50, y = 50, cursorX = 5.0, cursorY = 5.0)

        WindowDrag(window).update(dragging = true)

        assertEquals(0, window.moves)
    }

    @Test
    fun lettingGoEndsTheDragAndTheNextOneGrabsAgain() {
        val window = FakeDragWindow(x = 0, y = 0, cursorX = 10.0, cursorY = 10.0)
        val drag = WindowDrag(window)
        drag.update(dragging = true)
        drag.update(dragging = false)

        window.pointer(300.0, 300.0)
        drag.update(dragging = true)

        assertEquals(0, window.moves)
    }

    @Test
    fun aFullscreenWindowIsNeverMoved() {
        val window = FakeDragWindow(x = 0, y = 0, cursorX = 10.0, cursorY = 10.0, fullscreen = true)
        val drag = WindowDrag(window)

        drag.update(dragging = true)
        window.pointer(90.0, 90.0)
        drag.update(dragging = true)

        assertEquals(0, window.moves)
    }

    @Test
    fun aNullWindowHandleIsRefused() {
        assertFailsWith<IllegalArgumentException> { glfwWindowDrag(0L) }
        assertFailsWith<IllegalArgumentException> { windowDecoration(0L) }
        assertFailsWith<IllegalArgumentException> { setWindowDecoration(0L, WindowDecoration.None) }
    }

    /** A window whose content area moves with it, so the pointer's place in it shifts back. */
    private class FakeDragWindow(
        x: Int,
        y: Int,
        cursorX: Double,
        cursorY: Double,
        private val fullscreen: Boolean = false,
    ) : GlfwDragWindow {
        var position = x to y
            private set
        var moves = 0
            private set
        private var screenX = x + cursorX
        private var screenY = y + cursorY

        /** Puts the pointer at ([x], [y]) in the window's content area as it is now. */
        fun pointer(x: Double, y: Double) {
            screenX = position.first + x
            screenY = position.second + y
        }

        override fun isFullscreen(): Boolean = fullscreen

        override fun cursor(): DoubleArray = doubleArrayOf(screenX - position.first, screenY - position.second)

        override fun bounds(): IntArray = intArrayOf(position.first, position.second, 800, 600)

        override fun moveTo(x: Int, y: Int) {
            position = x to y
            moves++
        }
    }
}
