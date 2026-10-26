/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where a paragraph wraps (#1021): a line wrapping makes fits when it is no wider than the box, as Chromium
 * decides it, and text laid out at the width it measured to keeps the lines it measured.
 */
class TextWrapFitTest {
    private val font = UiFonts.default()

    private val samples = listOf(
        "Mesh terrain texture. Save changes and open the next scene.",
        "Billing and subscription settings",
        "Stamina restores slowly over time",
        "Health",
        "A first hard line\nand a second, longer one that wraps",
        "Harbor Town, Lantern Quay and the Salt Market",
        "Wi Te AV To Yo - kerned capitals, mixed with lower case",
    )

    /** Ten and a third pixels a character, so "aa bb" is 51.67 px and "aa bb cc" 82.67. */
    private val thirds = { text: String -> text.length * (31f / 3f) }

    /** A line wrapping makes that is a fraction of a pixel wider than the box wraps: the old `< limit + 0.5` kept it. */
    @Test
    fun aWrappedLineWiderThanTheBoxWrapsEvenByAFractionOfAPixel() {
        assertEquals(listOf("aa bb", "cc"), breakIntoLines("aa bb cc", 52f, thirds))
        assertEquals(listOf("aa", "bb", "cc"), breakIntoLines("aa bb cc", 51.5f, thirds))
        assertEquals(listOf("aa", "bb", "cc"), breakIntoLines("aa bb cc", 51f, thirds))
    }

    /** A wrapped line exactly as wide as the box fits. */
    @Test
    fun aWrappedLineAsWideAsTheBoxFits() {
        assertEquals(listOf("aa bb", "cc"), breakIntoLines("aa bb cc", 50f) { it.length * 10f })
    }

    /** The width is compared in Chromium's 1/64 px `LayoutUnit`: a line snaps up to it, the limit snaps down. */
    @Test
    fun widthsAreComparedInLayoutUnits() {
        val width = { text: String ->
            when (text.length) {
                5 -> 50f + 1f / 128f
                2 -> 20f
                else -> 80f
            }
        }
        assertEquals(listOf("aa", "bb", "cc"), breakIntoLines("aa bb cc", 50f, width))
        assertEquals(listOf("aa bb", "cc"), breakIntoLines("aa bb cc", 50f + 1f / 64f, width))
    }

    /**
     * A whole hard line is the one thing a box is sized to by rounding, so it is judged as the box holds it: a
     * line 51.25 px wide stays whole in a 51 px box, which is what a Text measured to that line is.
     */
    @Test
    fun aWholeHardLineIsJudgedOnThePixelItsBoxIsSizedTo() {
        val quarters = { text: String -> text.length * 10.25f }
        assertEquals(listOf("aa bb"), breakIntoLines("aa bb", 51f, quarters))
        assertEquals(listOf("aa", "bb"), breakIntoLines("aa bb", 50f, quarters))
        assertEquals(listOf("aa", "bb", "cc"), breakIntoLines("aa bb cc", 51f, quarters))
    }

    @Test
    fun aBoxHoldsItsLineAtTheWidthItIsSizedTo() {
        assertEquals(52, lineBoxWidthPx(51.67f, wholeHardLine = true))
        assertEquals(51, lineBoxWidthPx(51.25f, wholeHardLine = true))
        assertEquals(52, lineBoxWidthPx(51.25f, wholeHardLine = false))
        assertEquals(51, lineBoxWidthPx(51f, wholeHardLine = false))
    }

    /**
     * The invariant that lets the allowance go from wrapped lines: text laid out at its own measured width stays
     * on the lines it measured, whether it was measured unwrapped (its intrinsic width) or wrapped to a narrower
     * limit and then laid out again at the width it measured to. Swept across font sizes and the densities whose
     * em rounds, so the box is sometimes a fraction of a pixel narrower than its text before rounding.
     */
    @Test
    fun textLaidOutAtItsOwnMeasuredWidthDoesNotWrapDifferently() {
        val densities = listOf(1f, 1.25f, 1.5f, 2f)
        val sizes = listOf(9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 18f)
        val cases = densities.flatMap { density -> sizes.flatMap { size -> samples.map { Triple(it, size, density) } } }
        assertEquals(4 * 9 * samples.size, cases.size)
        cases.forEach { (text, size, density) -> keepsItsLinesAtItsOwnWidth(node(text, size, density), "\"$text\" at ${size}sp, density $density") }
    }

    private fun keepsItsLinesAtItsOwnWidth(node: LayoutNode, case: String) {
        node.measure(Constraints.of(0, Constraints.Infinity, 0, Constraints.Infinity))
        val unwrappedWidth = node.width
        val unwrappedHeight = node.height

        val intrinsic = node.maxIntrinsicWidth(Constraints.Infinity)
        assertEquals(unwrappedWidth, intrinsic, "intrinsic width is the measured width, $case")
        node.measure(Constraints.of(0, intrinsic, 0, Constraints.Infinity))
        assertEquals(unwrappedHeight, node.height, "wrapped at its own width $intrinsic, $case")
        assertEquals(unwrappedWidth, node.width, "narrowed at its own width $intrinsic, $case")
        assertEquals(unwrappedHeight, node.maxIntrinsicHeight(intrinsic), "intrinsic height at $intrinsic, $case")

        for (limit in intrinsic / 3..intrinsic) {
            node.measure(Constraints.of(0, limit, 0, Constraints.Infinity))
            val (width, height) = node.width to node.height
            node.measure(Constraints.of(0, width, 0, Constraints.Infinity))
            assertEquals(height, node.height, "wrapped at $limit, then at the $width it measured, $case")
            assertEquals(width, node.width, "wrapped at $limit, then narrowed from $width, $case")
        }
    }

    private fun node(text: String, sizeSp: Float, density: Float): LayoutNode =
        LayoutNode(TextMeasurePolicy(text, TextStyle(size = sizeSp.sp), font), density = density)
}
