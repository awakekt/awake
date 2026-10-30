/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.engine.platform.lifecycle.DisplayInsets
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayInsetsInViewTest {
    @Test
    fun anEmbeddedViewReportsOnlyTheInsetsStillOverlappingIt() {
        val covered = DisplayInsets(left = 12f, top = 24f, right = 12f, bottom = 32f)
        assertEquals(covered, covered.inView(DisplayInsets.None), "full-window view")
        assertEquals(DisplayInsets(bottom = 16f), covered.inView(DisplayInsets(20f, 24f, 12f, 16f)))
        assertEquals(DisplayInsets.None, covered.inView(DisplayInsets(20f, 40f, 20f, 40f)), "already laid out clear of every edge")
    }

    @Test
    fun gestureAndVisualCoverageRemainIndependentWhenTheWindowRotates() {
        val margins = DisplayInsets(right = 8f)
        assertEquals(DisplayInsets(right = 16f), DisplayInsets(right = 24f).inView(margins))
        assertEquals(DisplayInsets(left = 12f, right = 4f, bottom = 20f), DisplayInsets(12f, 0f, 12f, 20f).inView(margins))
    }
}
