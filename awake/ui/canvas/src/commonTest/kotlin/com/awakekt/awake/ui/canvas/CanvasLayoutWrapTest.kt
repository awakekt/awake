/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A wrapping [CanvasLayout] decides what fits from the sizes as authored, not as each rounds. */
class CanvasLayoutWrapTest {

    @Test
    fun aBarSizedToItsSlotsKeepsThemOnOneLineAtFractionalScales() {
        for (scale in listOf(0.75f, 0.83f, 1.25f, 1.5f, 1.75f)) {
            val out = ComposeHost().frame(FrameInput(400, 300)) { CanvasOverlay(slotBar(width = 23f), scale = scale) }

            val rows = SLOTS.map { assertNotNull(out.semantics.findTag("canvas-element-$it"), "slot $it at $scale").y }
            assertEquals(1, rows.distinct().size, "one line at scale $scale, rows $rows")
        }
    }

    @Test
    fun aBarTooNarrowForItsSlotsStillWraps() {
        val out = ComposeHost().frame(FrameInput(400, 300)) { CanvasOverlay(slotBar(width = 20f), scale = 1.5f) }

        val first = assertNotNull(out.semantics.findTag("canvas-element-${SLOTS.first()}"))
        val last = assertNotNull(out.semantics.findTag("canvas-element-${SLOTS.last()}"))
        assertEquals(first.x, last.x, "the last slot starts a second line")
    }

    /** Four 5-unit slots with 1-unit gaps: 23 units exactly. */
    private fun slotBar(width: Float): List<CanvasItem> {
        val bar = CanvasElement().apply {
            offsetX = 0f
            offsetY = 0f
            this.width = width
            height = 20f
            layout = CanvasLayout(wrap = true, gap = 1f)
        }
        return listOf(CanvasItem(1, bar)) + SLOTS.map { id ->
            CanvasItem(id, CanvasElement().apply { this.width = 5f; height = 5f }, parent = bar)
        }
    }

    private companion object {
        val SLOTS = listOf(2, 3, 4, 5)
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
