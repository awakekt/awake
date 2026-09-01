/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.Column
import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.Spacer
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A `verticalScroll` clips with a plain rect scissor, which the coalescer's exact-clip skip
 * ([DrawScope]'s `safeInteriorRect`) has nothing to do with -- it only helps *path* clips. Without
 * this, every row of a tall scrolled column was emitted and staged every frame, on- or off-screen.
 */
class ScrollClipCullingTest {

    private val red = Color(1f, 0f, 0f, 1f)

    private fun paintScrolledColumn(rowCount: Int, rowHeightDp: Int, viewportHeightDp: Int, scrollValue: Int): List<UiDrawPrimitive.Quad> {
        val root = LayoutNode(ColumnMeasurePolicy())
        val state = ScrollState(initial = scrollValue)
        composeInto(root) {
            Column(Modifier.size(50.dp, viewportHeightDp.dp).verticalScroll(state)) {
                repeat(rowCount) {
                    Spacer(Modifier.size(50.dp, rowHeightDp.dp).background(red))
                }
            }
        }
        root.layoutTree(Constraints.of(0, 200, 0, 200))
        return Painter().paint(root).filterIsInstance<UiDrawPrimitive.Quad>()
    }

    @Test
    fun rowsScrolledPastTheViewportAreNotEmitted() {
        // 20 rows * 20px = 400px of content, a 100px viewport, scrolled 300px down -- rows 0..13
        // (y 0..279) are fully above the visible 300..400 window and must not reach the frame.
        val quads = paintScrolledColumn(rowCount = 20, rowHeightDp = 20, viewportHeightDp = 100, scrollValue = 300)

        assertTrue(quads.none { it.y + it.h <= 0f }, "a row fully above the viewport was still emitted: ${quads.map { it.y }}")
        assertTrue(quads.isNotEmpty(), "the visible rows must still be emitted")
        assertTrue(quads.size < 20, "culling should have dropped at least the off-screen rows")
    }

    @Test
    fun rowsStillOnScreenAreUnaffected() {
        // No scrolling: every row is within the (oversized) viewport and must all still paint --
        // the cull must never drop something that is actually visible.
        val quads = paintScrolledColumn(rowCount = 5, rowHeightDp = 20, viewportHeightDp = 200, scrollValue = 0)

        assertEquals(5, quads.size)
    }

    @Test
    fun aRowStraddlingTheViewportEdgeIsStillEmitted() {
        // Partially visible must not be treated as fully outside -- only a genuine non-overlap
        // may be culled.
        val quads = paintScrolledColumn(rowCount = 3, rowHeightDp = 20, viewportHeightDp = 25, scrollValue = 0)

        // Row 1 spans y 20..40, viewport is 0..25 -- it overlaps by 5px and must survive.
        assertTrue(quads.any { it.y == 20f }, "a row straddling the viewport edge was dropped: ${quads.map { it.y }}")
    }
}
