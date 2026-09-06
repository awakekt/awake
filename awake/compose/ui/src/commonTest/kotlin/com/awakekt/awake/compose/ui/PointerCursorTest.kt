/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cursor request travels node -> hover -> frame output.
 *
 * Every link is silent when broken: a cursor nobody collects, or a hover list nobody reads, leaves
 * the pointer as an arrow and nothing throws. `ui-core` shipped that exact gap -- its own
 * `UiCursor` doc says the platform host owns the call and that without the wiring "every
 * hover-driven cursor stays the default arrow".
 */
class PointerCursorTest {

    private fun hoverAt(x: Int, y: Int, content: context(Composer) () -> Unit): PointerCursor {
        val host = ComposeHost()
        // Twice: input is dispatched against the previous frame's placed tree, so the first frame
        // has no geometry to hit-test and reports Default whatever is under the pointer.
        val input = FrameInput(viewportWidth = 100, viewportHeight = 100, pointerX = x, pointerY = y)
        host.frame(input, content)
        return host.frame(input, content).effects.cursor
    }

    @Test
    fun aHoveredNodeRequestsItsCursor() {
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
        }

        assertEquals(PointerCursor.Text, cursor)
    }

    @Test
    fun nothingHoveredLeavesTheDefault() {
        val cursor = hoverAt(90, 90) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
        }

        assertEquals(PointerCursor.Default, cursor)
    }

    @Test
    fun theInnermostRequestWins() {
        // A resize handle inside a panel shows the resize arrow, not the panel's pointer -- which
        // is CSS's rule and the one a nested control depends on.
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(PointerCursor.Pointer)) {
                Box(Modifier.size(20.dp).pointerCursor(PointerCursor.ResizeHorizontal))
            }
        }

        assertEquals(PointerCursor.ResizeHorizontal, cursor)
    }

    @Test
    fun aNodeWithoutARequestDefersToItsAncestor() {
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(PointerCursor.Pointer)) {
                Box(Modifier.size(20.dp))
            }
        }

        assertEquals(PointerCursor.Pointer, cursor)
    }
}
