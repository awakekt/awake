/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.ColumnMeasurePolicy
import com.awakekt.awake.compose.ui.graphics.Painter
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.layout.layoutTree
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A text node reuses last frame's measurement and glyph run only while nothing that shapes the text
 * changed. Every change below must produce what a node with no history produces.
 */
class TextLayoutCacheTest {
    private data class Frame(val width: Int, val height: Int, val glyphs: Int)

    private val painter = Painter()
    private val root = LayoutNode(ColumnMeasurePolicy())
    private var label = "Hi"
    private var style = TextStyle()

    private fun frame(node: LayoutNode = root, text: String = label, textStyle: TextStyle = style): Frame {
        composeInto(node) { Text(text, style = textStyle) }
        node.layoutTree(Constraints.fixed(WIDTH, HEIGHT))
        val glyphs = painter.paint(node).count { it is UiDrawPrimitive.Glyph }
        val child = node.children[0]
        return Frame(child.width, child.height, glyphs)
    }

    private fun fresh(text: String, textStyle: TextStyle): Frame = frame(LayoutNode(ColumnMeasurePolicy()), text, textStyle)

    @Test
    fun anUnchangedFrameMeasuresAndPaintsTheSame() {
        val first = frame()
        assertEquals(first, frame())
        assertEquals(first, frame())
    }

    @Test
    fun changedTextIsMeasuredAndPaintedAgain() {
        val short = frame()
        label = "Hello there"
        val long = frame()
        assertTrue(long.width > short.width, "$long should be wider than $short")
        assertEquals(fresh("Hello there", style), long)
        assertEquals("Hello there".length, long.glyphs, "one glyph per character, spaces included")
    }

    @Test
    fun changedStyleIsMeasuredAndPaintedAgain() {
        val small = frame()
        style = TextStyle(size = 28.sp)
        val large = frame()
        assertTrue(large.height > small.height, "$large should be taller than $small")
        assertEquals(fresh(label, style), large)
    }

    @Test
    fun changingBackRestoresTheOriginalLayout() {
        val original = frame()
        label = "Something else entirely"
        frame()
        label = "Hi"
        assertEquals(original, frame())
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 200
    }
}
