// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import kotlin.test.Test
import kotlin.test.assertEquals

/** The parts of `Constraints` the layout tests use indirectly and never assert on directly. */
class ConstraintsApiTest {

    @Test
    fun copyReplacesOnlyWhatIsNamed() {
        val original = Constraints.of(1, 2, 3, 4)

        val changed = original.copy(maxWidth = 20)

        assertEquals(listOf(1, 20, 3, 4), listOf(changed.minWidth, changed.maxWidth, changed.minHeight, changed.maxHeight))
    }

    @Test
    fun copyCanChangeEveryField() {
        val changed = Constraints.of(1, 2, 3, 4).copy(minWidth = 5, maxWidth = 6, minHeight = 7, maxHeight = 8)

        assertEquals(listOf(5, 6, 7, 8), listOf(changed.minWidth, changed.maxWidth, changed.minHeight, changed.maxHeight))
    }

    @Test
    fun toStringShowsBothRanges() {
        // The debug surface a failing layout is read through, so the shape is worth pinning.
        assertEquals("Constraints(w=1..2, h=3..4)", Constraints.of(1, 2, 3, 4).toString())
    }

    @Test
    fun anUnboundedAxisPrintsAsInfinite() {
        val unbounded = Constraints.of(0, Constraints.Infinity, 0, 5)

        assertEquals("Constraints(w=0..∞, h=0..5)", unbounded.toString())
    }
}
