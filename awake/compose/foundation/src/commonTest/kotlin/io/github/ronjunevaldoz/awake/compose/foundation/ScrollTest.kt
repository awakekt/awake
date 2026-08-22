// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.graphics.Painter
import io.github.ronjunevaldoz.awake.compose.ui.layout.composeInto
import io.github.ronjunevaldoz.awake.compose.ui.layout.layoutTree
import io.github.ronjunevaldoz.awake.compose.ui.node.LayoutNode
import io.github.ronjunevaldoz.awake.compose.ui.platform.ComposeHost
import io.github.ronjunevaldoz.awake.compose.ui.platform.FrameInput
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val red = Color(1f, 0f, 0f, 1f)

/** A 100-tall viewport over 250 of content: three 50px rows plus two 50px gaps' worth of boxes. */
private fun scroller(state: ScrollState, viewport: Int = 100, rows: Int = 5): LayoutNode {
    val root = LayoutNode(ColumnMeasurePolicy())
    composeInto(root) {
        Column(Modifier.size(100.dp, viewport.dp).verticalScroll(state)) {
            repeat(rows) { Spacer(Modifier.size(100.dp, 50.dp).background(red)) }
        }
    }
    root.layoutTree(Constraints.of(0, 200, 0, 200))
    return root
}

class ScrollStateTest {

    @Test
    fun scrollingIsClampedToTheContent() {
        val state = ScrollState()
        scroller(state)

        assertEquals(150, state.maxValue, "250 of content in a 100 viewport")
        assertEquals(100, state.viewportSize)
        assertEquals(0, state.scrollBy(-10), "scrolled above the top")
        assertEquals(150, state.scrollBy(9999), "scrolled past the end")
        assertEquals(150, state.value)
    }

    @Test
    fun scrollByReportsWhatItConsumed() {
        // The remainder is what lets an outer scrollable take over at the boundary.
        val state = ScrollState()
        scroller(state)
        state.scrollBy(140)

        assertEquals(10, state.scrollBy(50), "consumed more than was left")
    }

    @Test
    fun contentShrinkingPullsTheOffsetBack() {
        // Otherwise the offset stays past the new end and the viewport shows blank space with no
        // way to scroll back into content.
        val state = ScrollState()
        scroller(state, rows = 5)
        state.scrollBy(150)
        assertEquals(150, state.value)

        scroller(state, rows = 3)

        assertEquals(50, state.value, "the offset outlived the content it pointed at")
    }

    @Test
    fun anUnscrollableContentReportsNoRange() {
        val state = ScrollState()
        scroller(state, viewport = 300)

        assertEquals(0, state.maxValue)
        assertFalse(state.canScrollForward)
        assertFalse(state.canScrollBackward)
    }

    @Test
    fun theEdgesAreReportedSeparately() {
        val state = ScrollState()
        scroller(state)

        assertTrue(state.canScrollForward)
        assertFalse(state.canScrollBackward)

        state.scrollBy(150)
        assertFalse(state.canScrollForward)
        assertTrue(state.canScrollBackward)
    }
}

class ScrollLayoutTest {

    @Test
    fun theNodeIsViewportSizedNotContentSized() {
        // If it reported its content's height, a Column would hand it all the room and it would
        // never scroll -- the failure would look like "scrolling does nothing".
        val root = scroller(ScrollState())

        assertEquals(100, root.children[0].height)
    }

    @Test
    fun scrollingMovesTheContentUnderTheViewport() {
        val state = ScrollState()
        val root = scroller(state)
        val before = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().map { it.y }

        state.scrollBy(60)
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        val after = Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>().map { it.y }

        assertEquals(before.map { it - 60f }, after)
    }

    @Test
    fun overflowIsClipped() {
        // An unclipped scroller paints across whatever sits beside it, and the symptom reads as a
        // z-order bug rather than a missing clip.
        val root = scroller(ScrollState())
        val primitives = Painter().paint(root)

        assertTrue(primitives.any { it is UiDrawPrimitive.ClipPush }, "nothing clipped the overflow")
        assertTrue(primitives.any { it is UiDrawPrimitive.ClipPop }, "the clip was never popped")
    }
}

/** Scroll through the real frame loop, which is where wheel events actually arrive. */
class ScrollThroughFrameTest {

    private fun hostWith(state: ScrollState): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            Column(Modifier.size(100.dp, 100.dp).verticalScroll(state)) {
                repeat(5) { Spacer(Modifier.size(100.dp, 50.dp).background(red)) }
            }
        }
        return host to content
    }

    private fun wheel(delta: Float) =
        FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 10, pointerY = 10, scrollDeltaY = delta)

    private val still = FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 10, pointerY = 10)

    @Test
    fun aWheelOverTheListScrollsIt() {
        val state = ScrollState()
        val (host, content) = hostWith(state)
        host.frame(still, content)

        host.frame(wheel(-1f), content)

        assertTrue(state.value > 0, "the wheel did not reach the scroller")
    }

    @Test
    fun theFrameReportsThatTheUiTookTheWheel() {
        // Gameplay reads this to know the wheel was not meant for it -- zooming a camera while
        // scrolling a list is the bug it prevents.
        val state = ScrollState()
        val (host, content) = hostWith(state)
        host.frame(still, content)

        val output = host.frame(wheel(-1f), content)

        assertTrue(output.ownership.isScrollConsumed)
        assertTrue(output.ownership.isOverScrollable)
    }

    @Test
    fun aWheelAtTheEndIsNotConsumed() {
        // Otherwise a list pinned at its end swallows the wheel forever and an outer scrollable --
        // or the camera behind it -- can never take over.
        val state = ScrollState()
        val (host, content) = hostWith(state)
        host.frame(still, content)
        repeat(20) { host.frame(wheel(-1f), content) }

        val output = host.frame(wheel(-1f), content)

        assertEquals(150, state.value, "not actually at the end")
        assertFalse(output.ownership.isScrollConsumed, "the UI kept swallowing the wheel at the end")
    }

    @Test
    fun aWheelAwayFromTheListIsNotOverAScrollable() {
        val state = ScrollState()
        val (host, content) = hostWith(state)
        host.frame(still, content)

        val output = host.frame(
            FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 190, pointerY = 190, scrollDeltaY = -1f),
            content,
        )

        assertFalse(output.ownership.isOverScrollable)
        assertEquals(0, state.value)
    }
}
