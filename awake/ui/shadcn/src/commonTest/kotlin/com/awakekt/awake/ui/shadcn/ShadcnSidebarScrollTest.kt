/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.fillMaxWidth
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.ShadcnSidebar
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [ShadcnSidebar] taller than its frame must be reachable.
 *
 * The panel clipped and did not scroll, which a scene outliner and a component inspector are the
 * two panels guaranteed to hit -- and which no fixture small enough to fit ever demonstrates. The
 * assertion is on the last row's position rather than on a scroll offset: an offset can advance
 * while the content stays where it was, and what broke was that the row could not be brought into
 * view.
 */
class ShadcnSidebarScrollTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun contentTallerThanThePanelScrollsItsLastRowIntoView() {
        val session = composeTestSession(width = 200, height = 300) {
            provideShadcnTheme(theme) {
                ShadcnSidebar {
                    // A Column, because the content slot is a Box: two Spacers dropped straight in
                    // would stack at the same origin and the "last" row would never be below the
                    // fold. Real callers reach this through shadcnSidebarMenu, which is a Column.
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.fillMaxWidth().height(600.dp))
                        Spacer(Modifier.fillMaxWidth().height(20.dp).testTag("sidebar.last"))
                    }
                }
            }
        }

        val settled = session.frame()
        val before = settled.onNodeWithTag("sidebar.last").getBoundsInRoot()
        assertTrue(
            before.top >= 300,
            "the last row should start below a 300px panel before scrolling, was ${before.top}",
        )

        // Wheel over the panel's middle. One frame per notch: the node consumes a wheel event per
        // dispatch, and a single large delta would not prove the offset accumulates.
        repeat(WHEEL_NOTCHES) {
            session.frame(FrameInput(200, 300, pointerX = 100, pointerY = 150, scrollDeltaY = -1f))
        }
        val scrolled = session.frame(FrameInput(200, 300, pointerX = 100, pointerY = 150))

        val after = scrolled.onNodeWithTag("sidebar.last").getBoundsInRoot()
        assertTrue(
            after.top < before.top,
            "scrolling should move the last row up, was ${before.top} then ${after.top}",
        )
        assertEquals(before.height, after.height, "scrolling must not resize the row")
        assertTrue(
            after.top < 300,
            "the last row should be inside a 300px panel after scrolling, was ${after.top}",
        )
    }
}

/** 620dp of content in a 300px panel needs more than 300px of travel at 40px a notch. */
private const val WHEEL_NOTCHES = 12
