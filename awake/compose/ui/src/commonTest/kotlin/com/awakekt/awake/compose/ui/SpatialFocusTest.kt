/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.focus.FocusDirection
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusProperties
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.focus.focusTarget
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpatialFocusTest {

    private val gridPolicy = MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(200, 200) {
            // 2x2 grid: (0,0), (100,0), (0,100), (100,100)
            if (placeables.size > 0) placeables[0].placeAt(0, 0)
            if (placeables.size > 1) placeables[1].placeAt(100, 0)
            if (placeables.size > 2) placeables[2].placeAt(0, 100)
            if (placeables.size > 3) placeables[3].placeAt(100, 100)
        }
    }

    @Test
    fun spatial2DNavigationNavigatesGrid() {
        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "grid",
                modifier = Modifier,
                measurePolicy = gridPolicy,
                content = {
                    Layout(nodeType = "b0", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b1", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b2", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b3", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                },
            )
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        val gridNode = host.root.children[0]
        val b0 = gridNode.children[0] // (0, 0)
        val b1 = gridNode.children[1] // (100, 0)
        val b2 = gridNode.children[2] // (0, 100)
        val b3 = gridNode.children[3] // (100, 100)

        // Focus b0 (top-left)
        assertTrue(host.focusOwner.requestFocus(host.root, b0))
        assertEquals(b0, host.focusOwner.focused)

        // Down from b0 -> b2 (bottom-left)
        assertTrue(host.focusOwner.moveFocus(host.root, FocusDirection.Down))
        assertEquals(b2, host.focusOwner.focused)

        // Right from b2 -> b3 (bottom-right)
        assertTrue(host.focusOwner.moveFocus(host.root, FocusDirection.Right))
        assertEquals(b3, host.focusOwner.focused)

        // Up from b3 -> b1 (top-right)
        assertTrue(host.focusOwner.moveFocus(host.root, FocusDirection.Up))
        assertEquals(b1, host.focusOwner.focused)

        // Left from b1 -> b0 (top-left)
        assertTrue(host.focusOwner.moveFocus(host.root, FocusDirection.Left))
        assertEquals(b0, host.focusOwner.focused)
    }

    @Test
    fun arrowKeysTriggerSpatialFocusMovement() {
        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "grid",
                modifier = Modifier,
                measurePolicy = gridPolicy,
                content = {
                    Layout(nodeType = "b0", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b1", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b2", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b3", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                },
            )
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        val gridNode = host.root.children[0]
        val b0 = gridNode.children[0]
        val b2 = gridNode.children[2]

        host.focusOwner.requestFocus(host.root, b0)
        assertEquals(b0, host.focusOwner.focused)

        // Press ArrowDown
        val downEvent = KeyEvent(Key.ArrowDown, KeyEventType.Down)
        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200, keyEvents = listOf(downEvent)), content)

        assertEquals(b2, host.focusOwner.focused)
        assertTrue(downEvent.isConsumed)
    }

    @Test
    fun explicitSpatialFocusPropertiesOverrideGeometry() {
        val host = ComposeHost(density = 1f)
        val req3 = FocusRequester()
        val content: context(Composer)
        () -> Unit = {
            Layout(
                nodeType = "grid",
                modifier = Modifier,
                measurePolicy = gridPolicy,
                content = {
                    // b0 overrides Down to jump diagonally to b3 instead of b2
                    Layout(
                        nodeType = "b0",
                        modifier = Modifier.size(50.dp).focusProperties { down = req3 }.focusTarget(),
                        measurePolicy = gridPolicy,
                    )
                    Layout(nodeType = "b1", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(nodeType = "b2", modifier = Modifier.size(50.dp).focusTarget(), measurePolicy = gridPolicy)
                    Layout(
                        nodeType = "b3",
                        modifier = Modifier.size(50.dp).focusRequester(req3).focusTarget(),
                        measurePolicy = gridPolicy,
                    )
                },
            )
        }

        host.frame(FrameInput(viewportWidth = 200, viewportHeight = 200), content)
        val gridNode = host.root.children[0]
        val b0 = gridNode.children[0]
        val b3 = gridNode.children[3]

        host.focusOwner.requestFocus(host.root, b0)
        assertTrue(host.focusOwner.moveFocus(host.root, FocusDirection.Down))
        assertEquals(b3, host.focusOwner.focused)
    }
}
