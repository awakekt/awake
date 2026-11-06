/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Android's immersive [Fullscreen] over fake system bars, so it runs without a device. */
internal class SystemBarsFullscreenTest {
    @Test
    fun enterHidesTheBarsAndExitShowsThem() {
        val bars = FakeBars()
        val screen = SystemBarsFullscreen(bars)

        assertTrue(screen.enter())
        assertTrue(screen.isActive)
        screen.exit()

        assertFalse(screen.isActive)
        assertEquals(listOf("hide", "show"), bars.calls)
    }

    @Test
    fun enterWhenImmersiveAndExitWhenNotAskNothingOfTheSystem() {
        val bars = FakeBars()
        val screen = SystemBarsFullscreen(bars)

        screen.exit()
        screen.enter()
        screen.enter()

        assertEquals(listOf("hide"), bars.calls)
    }

    @Test
    fun barsTheSystemBroughtBackReadAsNotFullscreen() {
        val bars = FakeBars()
        val screen = SystemBarsFullscreen(bars)
        screen.enter()

        bars.hidden = false

        assertFalse(screen.isActive)
        assertTrue(screen.toggle())
        assertEquals(listOf("hide", "hide"), bars.calls)
    }

    @Test
    fun enterReportsTheRequestHandedOverBeforeTheBarsHide() {
        val bars = FakeBars(applies = false)
        val screen = SystemBarsFullscreen(bars)

        assertTrue(screen.enter())
        assertFalse(screen.isActive)
    }

    /** Bars that hide and show at once, or, when [applies] is false, only record the request. */
    private class FakeBars(private val applies: Boolean = true) : SystemBars {
        override var hidden = false
        val calls = mutableListOf<String>()

        override fun hide() {
            calls += "hide"
            if (applies) hidden = true
        }

        override fun show() {
            calls += "show"
            if (applies) hidden = false
        }
    }
}
