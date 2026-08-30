/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextLineBreakerTest {

    @Test
    fun softWrappedRepeatedWordsKeepTheirOwnSourceRanges() {
        val lines = breakIntoLineLayouts("one two one", maxWidthPx = 3f) { it.length.toFloat() }

        assertEquals(
            listOf(
                TextLineLayout("one", start = 0, endExclusive = 3),
                TextLineLayout("two", start = 4, endExclusive = 7),
                TextLineLayout("one", start = 8, endExclusive = 11),
            ),
            lines,
        )
    }

    @Test
    fun emptyHardLinesKeepTheirPositionInsteadOfBeingLost() {
        val lines = breakIntoLineLayouts("one\n\ntwo", maxWidthPx = 100f) { it.length.toFloat() }

        assertEquals(
            listOf(
                TextLineLayout("one", start = 0, endExclusive = 3),
                TextLineLayout("", start = 4, endExclusive = 4),
                TextLineLayout("two", start = 5, endExclusive = 8),
            ),
            lines,
        )
    }
}
