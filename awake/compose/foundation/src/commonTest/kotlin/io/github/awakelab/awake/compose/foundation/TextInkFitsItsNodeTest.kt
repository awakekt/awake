/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.text.Text
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.math2d.sp
import io.github.awakelab.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text is not clipped, and it scales with the size it was asked for.
 *
 * This module measures text in several ways -- advance, metrics, width -- and none of those compare
 * what was *painted*. That gap is why "is the text being cut?" could only be answered by squinting
 * at a render.
 *
 * The invariant a clip breaks is that ink tracks the requested size. A glyph cut off at a fixed
 * atlas boundary stops growing while the size keeps rising, so the ratio collapses -- which no
 * single-size assertion would catch.
 */
class TextInkFitsItsNodeTest {

    private fun ink(text: String, size: Float): ClosedFloatingPointRange<Float> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Text(text, style = TextStyle(size = size.sp)) }
        root.layoutTree(Constraints.of(0, 2000, 0, 2000))
        val glyphs = Painter().paint(root).filterIsInstance<DrawCommand.Glyph>()
        return glyphs.minOf { it.y }..glyphs.maxOf { it.y + it.h }
    }

    private fun height(text: String, size: Float): Float = ink(text, size).let { it.endInclusive - it.start }

    @Test
    fun everySizeInTheScalePaintsSomething() {
        listOf(12f, 14f, 16f, 18f, 20f, 24f, 30f, 36f).forEach { size ->
            assertTrue(height("Agy", size) > 0f, "size $size painted no ink")
        }
    }

    @Test
    fun inkGrowsWithTheRequestedSizeAcrossTheWholeScale() {
        // A glyph clipped at a fixed boundary stops growing while the size keeps rising. Checked
        // across the scale rather than at one size, because the atlas is authored at 16 and any
        // ceiling would only appear above it.
        val heights = listOf(12f, 14f, 16f, 18f, 20f, 24f, 30f, 36f).map { it to height("Agy", it) }

        heights.zipWithNext().forEach { (smaller, larger) ->
            assertTrue(
                larger.second > smaller.second,
                "ink stopped growing between ${smaller.first} and ${larger.first}: $heights",
            )
        }
    }

    @Test
    fun descendersPaintBelowTheBaselineRatherThanBeingFlattened() {
        // If a descender were cut, "Agy" and "AgV" would paint to the same depth.
        val withDescender = ink("Agy", 24f).endInclusive
        val without = ink("AVX", 24f).endInclusive

        assertTrue(
            withDescender > without,
            "g and y painted no deeper than caps -- descenders are being cut",
        )
    }

    @Test
    fun theLastGlyphIsPaintedInFull() {
        // A string clipped at its end loses the final glyph's ink entirely, so its right edge
        // matches the shorter string's.
        fun right(text: String): Float {
            val root = LayoutNode(ColumnMeasurePolicy())
            composeInto(root) { Text(text, style = TextStyle(size = 16f.sp)) }
            root.layoutTree(Constraints.of(0, 2000, 0, 2000))
            return Painter().paint(root).filterIsInstance<DrawCommand.Glyph>().maxOf { it.x + it.w }
        }

        assertTrue(right("WWWW") > right("WWW"), "the fourth glyph added no ink")
    }

    @Test
    fun oneGlyphPerCharacter() {
        // A dropped glyph is a different failure from a clipped one and looks the same in a render.
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) { Text("Agy", style = TextStyle(size = 16f.sp)) }
        root.layoutTree(Constraints.of(0, 2000, 0, 2000))

        assertEquals(3, Painter().paint(root).filterIsInstance<DrawCommand.Glyph>().size)
    }
}
