// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.foundation

import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToggleableTest {
    private fun interaction(clicked: Boolean) = UiInteraction(
        slot = Rectangle(0f, 0f, 0f, 0f),
        hovered = false,
        active = false,
        clicked = clicked,
    )

    private val clicked = interaction(clicked = true)
    private val idle = interaction(clicked = false)

    @Test
    fun toggleFlipsOnlyOnClick() {
        assertEquals(true, clicked.toggled(false))
        assertEquals(false, clicked.toggled(true))
        assertEquals(false, idle.toggled(false))
        assertEquals(true, idle.toggled(true))
    }

    @Test
    fun indeterminateResolvesToOnRatherThanCycling() {
        // A partially-checked parent checks everything; it does not clear it, and it does not
        // walk Off -> Indeterminate -> On. Matches Compose's own ToggleableState transition,
        // and it is the one case the three controls had each written by hand.
        assertEquals(UiToggleableState.On, clicked.toggled(UiToggleableState.Indeterminate))
        assertEquals(UiToggleableState.On, clicked.toggled(UiToggleableState.Off))
        assertEquals(UiToggleableState.Off, clicked.toggled(UiToggleableState.On))
    }

    @Test
    fun triStateIsUnchangedWithoutAClick() {
        UiToggleableState.entries.forEach { state ->
            assertEquals(state, idle.toggled(state), "$state must survive a frame with no click")
        }
    }

    @Test
    fun selectionIsIdempotent() {
        // Unlike toggling: clicking an already-selected radio leaves it selected. Deselection
        // is the enclosing group's call, not the row's.
        assertTrue(clicked.selected(selected = true))
        assertTrue(clicked.selected(selected = false))
        assertTrue(idle.selected(selected = true))
        assertEquals(false, idle.selected(selected = false))
    }
}
