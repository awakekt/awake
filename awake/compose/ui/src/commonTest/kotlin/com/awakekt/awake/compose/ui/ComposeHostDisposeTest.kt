/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberObserver
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

private object DisposeChildType

private val disposeFrame = FrameInput(viewportWidth = 100, viewportHeight = 100)

/** Disposing a host drops its tree and tells what the UI remembered that it was forgotten. */
class ComposeHostDisposeTest {

    private val forgotten = mutableListOf<String>()
    private val made = mutableListOf<Tracked>()

    private inner class Tracked(val name: String) : RememberObserver {
        init {
            made += this
        }

        override fun onForgotten() {
            forgotten += name
        }
    }

    private val content: context(Composer)
    () -> Unit = {
        remember<Tracked> { Tracked("top") }
        Layout(DisposeChildType, measurePolicy = MeasurePolicy { _, _ -> layout(10, 10) {} }) {
            remember<Tracked> { Tracked("nested") }
        }
    }

    @Test
    fun disposingForgetsWhatTheTreeRememberedOnce() {
        val host = ComposeHost()
        host.frame(disposeFrame, content)
        host.frame(disposeFrame, content)
        assertTrue(forgotten.isEmpty(), "a live host forgets nothing")

        host.dispose()
        host.dispose()

        assertEquals(listOf("nested", "top"), forgotten)
        assertEquals(0, host.root.children.size)
    }

    @Test
    fun aFrameAfterDisposeComposesAFreshTree() {
        val host = ComposeHost()
        host.frame(disposeFrame, content)
        val first = made.first { it.name == "top" }

        host.dispose()
        host.frame(disposeFrame, content)

        assertNotSame(first, made.last { it.name == "top" })
        assertEquals(1, host.root.children.size)
    }
}
