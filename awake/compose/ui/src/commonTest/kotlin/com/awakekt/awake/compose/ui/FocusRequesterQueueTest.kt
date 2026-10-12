/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.focus.focusTarget
import com.awakekt.awake.compose.ui.layout.Layout
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `FocusRequester.requestFocus()`/`freeFocus()` queue against the node's attached modifier, and
 * `ComposeHost` drains the queue once at the start of its next frame, before that frame dispatches
 * input. See `FocusRequester.kt` and `FocusOwner.applyPendingFocusRequests`.
 */
class FocusRequesterQueueTest {

    // Places every declared child at the origin; these tests never check layout, only focus.
    private val stackPolicy = MeasurePolicy { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(50, 50) { placeables.forEach { it.placeAt(0, 0) } }
    }

    private fun frameInput() = FrameInput(viewportWidth = 200, viewportHeight = 200)

    @Test
    fun requestFocusAppliesAtTheStartOfTheNextFrameNotImmediately() {
        val host = ComposeHost(density = 1f)
        val requester = FocusRequester()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "field", modifier = Modifier.size(50.dp).focusRequester(requester).focusTarget(), measurePolicy = stackPolicy)
        }

        host.frame(frameInput(), content)
        val field = host.root.children[0]

        requester.requestFocus()
        assertNull(host.focusOwner.focused, "queued, not applied synchronously")

        host.frame(frameInput(), content)
        assertEquals(field, host.focusOwner.focused, "applied at the start of the next frame")
    }

    @Test
    fun freeFocusAppliesAtTheStartOfTheNextFrame() {
        val host = ComposeHost(density = 1f)
        val requester = FocusRequester()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "field", modifier = Modifier.size(50.dp).focusRequester(requester).focusTarget(), measurePolicy = stackPolicy)
        }

        host.frame(frameInput(), content)
        val field = host.root.children[0]
        assertTrue(host.focusOwner.requestFocus(host.root, field))

        requester.freeFocus()
        assertEquals(field, host.focusOwner.focused, "queued, not freed synchronously")

        host.frame(frameInput(), content)
        assertNull(host.focusOwner.focused, "freed at the start of the next frame")
    }

    @Test
    fun aRequestForADetachedNodeIsDroppedWhileAnAttachedRequesterStillWorks() {
        val host = ComposeHost(density = 1f)
        val stays = FocusRequester()
        val leaves = FocusRequester()
        var includeLeaves = true
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "stays", modifier = Modifier.size(50.dp).focusRequester(stays).focusTarget(), measurePolicy = stackPolicy)
            if (includeLeaves) {
                Layout(nodeType = "leaves", modifier = Modifier.size(50.dp).focusRequester(leaves).focusTarget(), measurePolicy = stackPolicy)
            }
        }

        host.frame(frameInput(), content)
        includeLeaves = false
        host.frame(frameInput(), content) // "leaves" node detaches this pass.
        val staysNode = host.root.children[0]

        // "stays" first, "leaves" second: if a detached requester wrongly still applied, it would
        // be the later call and would win, overwriting "stays" as the focused node.
        stays.requestFocus()
        leaves.requestFocus()
        host.frame(frameInput(), content)

        assertEquals(staysNode, host.focusOwner.focused, "a request against a detached node must not steal focus")
    }

    @Test
    fun theLaterOfARequestAndAFreeInOneFrameWins() {
        val host = ComposeHost(density = 1f)
        val requester = FocusRequester()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "field", modifier = Modifier.size(50.dp).focusRequester(requester).focusTarget(), measurePolicy = stackPolicy)
        }
        host.frame(frameInput(), content)
        val field = host.root.children[0]

        requester.freeFocus()
        requester.requestFocus()
        host.frame(frameInput(), content)
        assertEquals(field, host.focusOwner.focused, "free, then request: focused")

        requester.requestFocus()
        requester.freeFocus()
        host.frame(frameInput(), content)
        assertNull(host.focusOwner.focused, "request, then free: not")
    }

    @Test
    fun aRequestMadeBeforeItsNodeIsComposedWaitsForIt() {
        val host = ComposeHost(density = 1f)
        val requester = FocusRequester()
        var shown = false
        val content: context(Composer)
        () -> Unit = {
            if (shown) {
                Layout(nodeType = "field", modifier = Modifier.size(50.dp).focusRequester(requester).focusTarget(), measurePolicy = stackPolicy)
            }
        }
        host.frame(frameInput(), content)

        // A window opening and focusing its field in one step: the field isn't composed yet.
        requester.requestFocus()
        shown = true
        host.frame(frameInput(), content)
        host.frame(frameInput(), content)

        assertEquals(host.root.children[0], host.focusOwner.focused)
    }

    @Test
    fun freeFocusOnlyClearsFocusIfThisRequestersNodeStillHoldsItWhenApplied() {
        val host = ComposeHost(density = 1f)
        val a = FocusRequester()
        val b = FocusRequester()
        val content: context(Composer)
        () -> Unit = {
            Layout(nodeType = "a", modifier = Modifier.size(50.dp).focusRequester(a).focusTarget(), measurePolicy = stackPolicy)
            Layout(nodeType = "b", modifier = Modifier.size(50.dp).focusRequester(b).focusTarget(), measurePolicy = stackPolicy)
        }

        host.frame(frameInput(), content)
        val nodeA = host.root.children[0]
        val nodeB = host.root.children[1]

        assertTrue(host.focusOwner.requestFocus(host.root, nodeA))
        a.freeFocus() // queued while A holds focus.
        // Focus moves on, synchronously, before the queued free is drained.
        assertTrue(host.focusOwner.requestFocus(host.root, nodeB))

        host.frame(frameInput(), content)

        assertEquals(nodeB, host.focusOwner.focused, "a's stale free must not undo focus that moved on since it was queued")
    }
}
