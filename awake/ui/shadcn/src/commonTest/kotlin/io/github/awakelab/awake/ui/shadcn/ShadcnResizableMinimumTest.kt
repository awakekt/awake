/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.fillMaxWidth
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEvent
import io.github.awakelab.awake.compose.ui.input.pointer.PointerEventType
import io.github.awakelab.awake.compose.ui.input.pointer.PointerInputDispatcher
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsNode
import io.github.awakelab.awake.compose.ui.semantics.SemanticsTreeBuilder
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.components.shadcnResizablePanelGroup
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A panel's minimum is a real width, and a panel that declares only a minimum starts there.
 *
 * The minimum used to be a fraction, which means something different at every window size: an
 * inspector held above 14% of the group is 200px on a laptop and 90px in a half-width window, and
 * 90px is not an inspector. These drag past the stop deliberately -- a clamp that only holds for
 * small deltas holds for none of the ones a user actually produces.
 */
class ShadcnResizableMinimumTest {

    private val width = 600
    private val height = 200

    /**
     * One root across frames, recomposed in place.
     *
     * A fresh root per frame is a fresh `remember`, so the split resets and a drag is lost --
     * exactly the mistake that made this test first report a panel that had not moved at all.
     */
    private class Host(private val width: Int, private val height: Int) {
        val root = LayoutNode(ColumnMeasurePolicy())

        fun frame(content: context(Composer) () -> Unit): List<SemanticsNode> {
            composeInto(root) { provideShadcnTheme(shadcnThemeValues(dark = true)) { content() } }
            root.layoutTree(Constraints.of(width, width, height, height))
            return SemanticsTreeBuilder().build(root)
        }
    }

    private fun List<SemanticsNode>.tagged(tag: String): SemanticsNode =
        firstNotNullOfOrNull { find(it, tag) } ?: error("no node tagged '$tag'")

    private fun find(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { find(it, tag) }

    @Test
    fun aPanelDeclaringOnlyAMinimumStartsAtIt() {
        val content: context(Composer)
        () -> Unit = {
            shadcnResizablePanelGroup(Modifier.fillMaxWidth().height(200.dp)) {
                panel(min = 150.dp, tag = "a") {}
                panel(size = 0.5f, tag = "b") {}
            }
        }
        // Two frames: the first has no extent, and a real minimum cannot be turned into a fraction
        // without one.
        val host = Host(width, height)
        host.frame(content)
        val semantics = host.frame(content)

        assertEquals(150, semantics.tagged("a").width, "the minimum-only panel did not start at 150dp")
    }

    @Test
    fun draggingPastTheStopLeavesThePanelAtItsMinimumWidth() {
        val content: context(Composer)
        () -> Unit = {
            shadcnResizablePanelGroup(
                // `height`, not `fillMaxHeight`: the root is a Column, which measures children
                // against an unbounded main axis, and a fillMax under one is a no-op. The handle
                // came out 1x0 and swallowed every press.
                Modifier.fillMaxWidth().height(200.dp),
                handleTags = listOf("handle"),
            ) {
                panel(min = 200.dp, size = 0.5f, tag = "a") {}
                panel(min = 100.dp, size = 0.5f, tag = "b") {}
            }
        }
        val host = Host(width, height)
        host.frame(content)
        val semantics = host.frame(content)
        val root = host.root
        val handle = semantics.tagged("handle")

        val dispatcher = PointerInputDispatcher()
        val y = handle.y + handle.height / 2
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Press), handle.x, y)
        // Far past the stop, in one throw -- 250px left of a 300px panel with a 200px floor.
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Move), handle.x - 250, y, dx = -250)
        dispatcher.dispatch(root, PointerEvent(PointerEventType.Release), handle.x - 250, y)

        val after = host.frame(content)
        val a = after.tagged("a").width
        val b = after.tagged("b").width
        assertEquals(200, a, "panel a shrank past its 200dp minimum")
        assertTrue(a + b >= width - 2, "the drag lost width: $a + $b of $width")
    }
}
