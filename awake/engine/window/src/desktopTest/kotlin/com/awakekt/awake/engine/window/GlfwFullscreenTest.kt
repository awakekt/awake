/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlfwFullscreenTest {
    @Test
    fun enterPutsTheWindowOnItsMonitorAndExitRestoresItsBounds() {
        val window = FakeWindow(bounds = intArrayOf(40, 60, 800, 600))
        val screen = GlfwFullscreen(window)

        assertTrue(screen.enter())
        assertTrue(screen.isActive)
        window.bounds = intArrayOf(0, 0, 2560, 1440)
        screen.exit()

        assertFalse(screen.isActive)
        assertEquals(listOf(40, 60, 800, 600), window.restored)
    }

    @Test
    fun theBoundsAtTheLastEnterAreTheOnesRestored() {
        val window = FakeWindow(bounds = intArrayOf(0, 0, 800, 600))
        val screen = GlfwFullscreen(window)
        window.bounds = intArrayOf(10, 20, 1024, 768)

        screen.enter()
        screen.exit()

        assertEquals(listOf(10, 20, 1024, 768), window.restored)
    }

    @Test
    fun aMinimisedWindowsEmptySizeIsNeverKept() {
        val window = FakeWindow(bounds = intArrayOf(5, 5, 640, 480))
        val screen = GlfwFullscreen(window)
        window.bounds = intArrayOf(0, 0, 0, 0)

        screen.enter()
        screen.exit()

        assertEquals(listOf(5, 5, 640, 480), window.restored)
    }

    @Test
    fun aRefusedEnterReportsFalseAndStaysWindowed() {
        val window = FakeWindow(bounds = intArrayOf(0, 0, 800, 600), monitor = false)
        val screen = GlfwFullscreen(window)

        assertFalse(screen.enter())
        assertFalse(screen.isActive)
    }

    @Test
    fun enterWhenFullscreenAndExitWhenWindowedDoNothing() {
        val window = FakeWindow(bounds = intArrayOf(0, 0, 800, 600))
        val screen = GlfwFullscreen(window)

        screen.exit()
        assertEquals(null, window.restored)

        screen.enter()
        assertTrue(screen.enter())
        assertEquals(1, window.entered)
    }

    @Test
    fun aWindowMadeFullscreenLeavesToTheFallbackBounds() {
        val window = FakeWindow(bounds = intArrayOf(0, 0, 2560, 1440), fullscreen = true)
        val screen = GlfwFullscreen(window)

        screen.exit()

        assertEquals(listOf(100, 100, 1280, 720), window.restored)
    }

    @Test
    fun aNullWindowHandleIsRefused() {
        assertFailsWith<IllegalArgumentException> { glfwFullscreen(0L) }
        assertFailsWith<IllegalArgumentException> { desktopHostActions(0L) }
    }

    private class FakeWindow(
        var bounds: IntArray,
        private var fullscreen: Boolean = false,
        private val monitor: Boolean = true,
    ) : GlfwFullscreenWindow {
        var restored: List<Int>? = null
        var entered = 0

        override fun isFullscreen(): Boolean = fullscreen

        override fun bounds(): IntArray = bounds

        override fun goFullscreen(): Boolean {
            if (!monitor) return false
            entered++
            fullscreen = true
            return true
        }

        override fun goWindowed(x: Int, y: Int, width: Int, height: Int) {
            restored = listOf(x, y, width, height)
            fullscreen = false
        }
    }
}
