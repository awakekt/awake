/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.BoxMeasurePolicy
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.ui.layout.composeInto
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.platform.LocalFontScale
import com.awakekt.awake.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A label's intrinsics answer what its measure would: the same width and the same wrapped height.
 *
 * A parent that plans from intrinsics (FlexBox, `IntrinsicSize`) and then measures must not be
 * told one size and handed another.
 */
class TextIntrinsicsTest {
    private val fontScales = listOf(1f, 1.5f, 2f)
    private val samples = listOf(
        "",
        "Health",
        "Stamina restores slowly over time",
        "Two\nlines",
        "A first hard line\nand a second, longer one that wraps",
    )

    @Test
    fun intrinsicHeightIsTheMeasuredHeightAtThatWidth() {
        for (fontScale in fontScales) {
            for (text in samples) {
                val unwrapped = measured(text, fontScale, Constraints.Infinity)
                val widths = listOf(Constraints.Infinity, unwrapped.width, unwrapped.width / 2, 60, 1)
                for (width in widths) {
                    val label = node(text, fontScale)
                    val atWidth = measured(text, fontScale, width)
                    val case = "\"$text\" at width $width, scale $fontScale"
                    assertEquals(atWidth.height, label.maxIntrinsicHeight(width), "max $case")
                    assertEquals(atWidth.height, label.minIntrinsicHeight(width), "min $case")
                }
            }
        }
    }

    @Test
    fun wrappedTextIsTallerThanOneLine() {
        for (fontScale in fontScales) {
            val label = node("Stamina restores slowly over time", fontScale)
            val oneLine = label.maxIntrinsicHeight(Constraints.Infinity)
            val narrow = label.minIntrinsicWidth(Constraints.Infinity)
            assertTrue(oneLine > 0, "one line has a height at scale $fontScale")
            assertTrue(label.maxIntrinsicHeight(narrow) >= oneLine * 4, "one word a line at scale $fontScale")
        }
    }

    @Test
    fun hardLinesCountAtAnyWidth() {
        for (fontScale in fontScales) {
            val one = node("One", fontScale).maxIntrinsicHeight(Constraints.Infinity)
            val two = node("One\nTwo", fontScale).maxIntrinsicHeight(Constraints.Infinity)
            assertTrue(two >= one * 2, "two hard lines are two lines tall at scale $fontScale")
        }
    }

    @Test
    fun intrinsicWidthsAreTheMeasuredWidths() {
        for (fontScale in fontScales) {
            for (text in samples) {
                val label = node(text, fontScale)
                val unwrapped = measured(text, fontScale, Constraints.Infinity)
                assertEquals(unwrapped.width, label.maxIntrinsicWidth(Constraints.Infinity), "\"$text\" at scale $fontScale")
                // Measured at its own maximum width, it stays on the lines it has unwrapped.
                val atOwnWidth = measured(text, fontScale, label.maxIntrinsicWidth(Constraints.Infinity))
                assertEquals(unwrapped.height, atOwnWidth.height, "\"$text\" wraps at its own width, scale $fontScale")
                assertTrue(label.minIntrinsicWidth(Constraints.Infinity) <= unwrapped.width, "\"$text\" at scale $fontScale")
            }
        }
    }

    @Test
    fun aLargerFontScaleAnswersLarger() {
        val small = node("Stamina restores", 1f)
        val large = node("Stamina restores", 2f)
        assertTrue(large.maxIntrinsicWidth(Constraints.Infinity) > small.maxIntrinsicWidth(Constraints.Infinity))
        assertTrue(large.maxIntrinsicHeight(Constraints.Infinity) > small.maxIntrinsicHeight(Constraints.Infinity))
    }

    private fun node(text: String, fontScale: Float): LayoutNode {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) { CompositionLocalProvider(LocalFontScale, fontScale) { Text(text) } }
        return root.children[0]
    }

    private fun measured(text: String, fontScale: Float, maxWidth: Int): LayoutNode {
        val root = LayoutNode(BoxMeasurePolicy())
        composeInto(root) { CompositionLocalProvider(LocalFontScale, fontScale) { Text(text) } }
        root.measure(Constraints.of(0, maxWidth, 0, Constraints.Infinity))
        return root.children[0]
    }
}
