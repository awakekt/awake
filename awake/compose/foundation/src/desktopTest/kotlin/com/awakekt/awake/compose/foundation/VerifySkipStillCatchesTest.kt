/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.draw.drawBehind
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ComposeHost.verifySkippedCompositions] compares a retained frame with a fresh one by where each
 * primitive lands, so a subtree a retained draw replays inside a translate range is not reported
 * just for being spelled as a range (#1512). These keep it honest: what does land differently is
 * still reported, inside a moved subtree, out of order, and with no placement to explain it. A
 * comparison that lost the ranges' offsets fails the first two moved cases; one that lost order fails
 * the reordered case.
 */
class VerifySkipStillCatchesTest {
    private fun host() = ComposeHost().apply {
        skipUnchangedComposition = true
        skipUnchangedMeasure = true
        retainUnchangedDraw = true
        verifySkippedCompositions = true
    }

    private val input = FrameInput(200, 100, deltaSeconds = 0f)
    private var shift = 0
    private var red = 0.2f
    private var inner = 0f

    /** A subtree moved by a read while placing, drawing what [red] and [inner] say from plain fields. */
    context(_: Composer)
    private fun Moved() {
        Box(Modifier.offset { IntOffset(shift, 0) }) {
            Box(Modifier.size(20.dp).drawBehind { drawRect(inner, 0f, 10f, 10f, Color(red, 0f, 0f)) })
        }
    }

    @Test
    fun aMovedSubtreeIsNotStaleForMovingAlone() {
        val host = host()
        repeat(FRAMES) {
            shift = it
            host.frame(input) { Moved() }
        }
        assertEquals(0, host.staleSkips, "moving a subtree, its drawing unchanged, was reported stale")
    }

    @Test
    fun aChangedColourInsideAMovedSubtreeIsStale() {
        val host = host()
        repeat(FRAMES) {
            shift = it
            red = 0.1f * (it % 5)
            host.frame(input) { Moved() }
        }
        assertTrue(host.staleSkips > 0, "a colour changed inside a moved subtree went unnoticed")
    }

    @Test
    fun aChangedPositionInsideAMovedSubtreeIsStale() {
        val host = host()
        repeat(FRAMES) {
            shift = it
            inner = (it % 3).toFloat()
            host.frame(input) { Moved() }
        }
        assertTrue(host.staleSkips > 0, "a primitive moved inside a moved subtree went unnoticed")
    }

    @Test
    fun aReorderedPairIsStale() {
        var flipped = false
        val host = host()
        repeat(FRAMES) {
            flipped = it % 2 == 1
            host.frame(input) {
                Box {
                    // Two boxes on the same spot: which is drawn last is all that changes.
                    if (flipped) {
                        Square(Color(1f, 0f, 0f))
                        Square(Color(0f, 0f, 1f))
                    } else {
                        Square(Color(0f, 0f, 1f))
                        Square(Color(1f, 0f, 0f))
                    }
                }
            }
        }
        assertTrue(host.staleSkips > 0, "two primitives drawn in the other order went unnoticed")
    }

    @Test
    fun aPlainFieldWriteWithNoPlacementToExplainItIsStale() {
        var label = Color(0f, 1f, 0f)
        val host = host()
        repeat(FRAMES) {
            label = Color(0f, 0.1f * (it % 5), 0f)
            host.frame(input) { Square(label) }
        }
        assertTrue(host.staleSkips > 0, "a plain field read while composing went unnoticed")
    }

    context(_: Composer)
    private fun Square(color: Color) {
        Box(Modifier.size(10.dp).drawBehind { drawRect(0f, 0f, 10f, 10f, color) })
    }

    private companion object {
        const val FRAMES = 12
    }
}
