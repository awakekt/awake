/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.gestures.onSecondaryPress
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** A touch screen has no right button, so holding a finger is how it opens a context menu. */
class FingerLongPressTest {
    private var menus = 0
    private var clicks = 0
    private val content: context(Composer) () -> Unit = {
        Box(Modifier.onSecondaryPress { _, _ -> menus++ }) {
            Spacer(Modifier.size(80.dp).clickable { clicks++ })
        }
    }

    private fun finger(x: Int, down: Boolean, seconds: Float = 1f / 60f) =
        FrameInput(200, 200, pointers = listOf(PointerFrame(1, x, 20, down)), deltaSeconds = seconds)

    @Test
    fun holdingAFingerOpensTheMenuWithoutAlsoClicking() {
        val host = ComposeHost()
        host.frame(FrameInput(200, 200), content)
        host.frame(finger(20, down = true), content)
        host.frame(finger(20, down = true, seconds = 0.6f), content)
        host.frame(finger(20, down = false), content)

        assertEquals(1, menus)
        assertEquals(0, clicks, "lifting the finger that opened the menu must not click what's under it")
    }

    @Test
    fun aQuickTapStillClicks() {
        val host = ComposeHost()
        host.frame(FrameInput(200, 200), content)
        host.frame(finger(20, down = true), content)
        host.frame(finger(20, down = false), content)

        assertEquals(0, menus)
        assertEquals(1, clicks)
    }

    @Test
    fun aHeldDragIsNotALongPress() {
        val host = ComposeHost()
        host.frame(FrameInput(200, 200), content)
        host.frame(finger(20, down = true), content)
        host.frame(finger(60, down = true, seconds = 0.6f), content)

        assertEquals(0, menus)
    }

    @Test
    fun aHeldMouseButtonIsNotAContextMenu() {
        val host = ComposeHost()
        host.frame(FrameInput(200, 200, 20, 20), content)
        host.frame(FrameInput(200, 200, 20, 20, pointerDown = true), content)
        host.frame(FrameInput(200, 200, 20, 20, pointerDown = true, deltaSeconds = 0.6f), content)

        assertEquals(0, menus, "a mouse has the real right button")
    }
}
