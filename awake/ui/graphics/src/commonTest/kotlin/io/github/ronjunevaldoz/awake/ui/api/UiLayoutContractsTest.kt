// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ui.api

import io.github.ronjunevaldoz.awake.core.math2d.dp
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.api.layout.Dimension
import io.github.ronjunevaldoz.awake.ui.api.layout.LayoutWeight
import io.github.ronjunevaldoz.awake.ui.api.layout.toDimension
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The UI-side half of what was `RectangleTest`. `Rectangle` moved to `core:math`, taking its own
 * `intersect`/`contains` cases with it; these two never tested the rectangle -- they test that
 * the layout and popup contracts stay value types.
 */
class UiLayoutContractsTest {
    @Test
    fun dimensionContractsRemainRuntimeFree() {
        assertEquals(Dimension.Fixed(12.dp), 12.dp.toDimension())
        assertEquals(Dimension.FillMax, 0.dp.toDimension())
        assertEquals(LayoutWeight(weight = 2f, fill = false), LayoutWeight(2f, fill = false))
    }

    @Test
    fun popupContractsAreRuntimeFree() {
        val provider = UiPopupPositionProvider { _, _, size -> Rectangle(1f, 2f, size.width, size.height) }

        assertEquals(
            Rectangle(1f, 2f, 30f, 40f),
            provider.calculatePosition(Rectangle(0f, 0f, 1f, 1f), Rectangle(0f, 0f, 100f, 100f), UiPopupSize(30f, 40f)),
        )
        assertEquals(UiPopupProperties(), UiPopupProperties())
    }
}
