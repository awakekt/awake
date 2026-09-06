/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.text.TextMeasurePolicy
import com.awakekt.awake.compose.ui.node.LayoutNode
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Text reports what it wants to be, so anything sizing to its content has something to read.
 *
 * A `MeasurePolicy` with no children inherits `max over children`, which for a leaf is zero. Every
 * label therefore claimed it wanted no width at all, and `width(IntrinsicSize.Max)` above one
 * resolved to nothing.
 */
class TextIntrinsicWidthTest {

    @Test
    fun aLabelReportsTheWidthItWouldMeasureUnwrapped() {
        val label = "Billing and subscription settings"
        val node = textNode(label)
        val intrinsic = node.maxIntrinsicWidth(HEIGHT)
        val measured = node.also { it.measure(Constraints.of(0, WIDE, 0, HEIGHT)) }.width

        assertTrue(intrinsic > 0, "a label reported that it wanted no width at all")
        assertEquals(measured, intrinsic, "the intrinsic is what it measures to when nothing wraps it")
    }

    @Test
    fun aLongerLabelWantsMoreWidth() {
        assertTrue(
            textNode("Billing and subscription settings").maxIntrinsicWidth(HEIGHT) >
                textNode("Billing").maxIntrinsicWidth(HEIGHT),
            "intrinsic width must track the content",
        )
    }

    @Test
    fun theMinimumIsTheLongestWordNotTheWholeLine() {
        val phrase = textNode("Billing and subscription settings")
        assertTrue(
            phrase.minIntrinsicWidth(HEIGHT) < phrase.maxIntrinsicWidth(HEIGHT),
            "a multi-word label can wrap, so its floor is narrower than its unwrapped line",
        )
    }

    private fun textNode(text: String) =
        LayoutNode(TextMeasurePolicy(text, TextStyle(), UiFonts.default()))

    private companion object {
        const val WIDE = 10_000
        const val HEIGHT = 100
    }
}
