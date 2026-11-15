/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** [flattened] draws a list as its consumer stages it: each translate range moved, its markers gone. */
class DrawTranslateTest {
    private val quad = DrawCommand.Quad(1f, 2f, 3f, 4f, Color.White)
    private val glyph = DrawCommand.Glyph(5f, 6f, 7f, 8f, 0f, 0f, 1f, 1f, Color.White)
    private val unbounded = Rectangle(-1e9f, -1e9f, 2e9f, 2e9f)

    @Test
    fun aListWithNoRangeIsItself() {
        val list = listOf<UiDrawPrimitive>(quad, glyph)

        assertSame(list, list.flattened())
    }

    @Test
    fun aRangeMovesWhatIsInsideItAndDropsItsMarkers() {
        val list = listOf(quad, TranslatePush(10f, 20f), glyph, DrawCommand.ClipPush(Rectangle(0f, 0f, 5f, 5f)), DrawCommand.ClipPop(unbounded), TranslatePop, quad)

        assertEquals(
            listOf(quad, glyph.copy(x = 15f, y = 26f), DrawCommand.ClipPush(Rectangle(10f, 20f, 5f, 5f)), DrawCommand.ClipPop(unbounded), quad),
            list.flattened(),
        )
    }

    @Test
    fun nestedRangesAddAndRestoreTheirOffsets() {
        val list = listOf(TranslatePush(1f, 1f), TranslatePush(10f, 10f), quad, TranslatePop, quad, TranslatePop, quad)

        assertEquals(listOf(quad.copy(x = 12f, y = 13f), quad.copy(x = 2f, y = 3f), quad), list.flattened())
    }

    @Test
    fun anUnbalancedListIsRefused() {
        assertFailsWith<IllegalStateException> { listOf(TranslatePush(1f, 1f), quad).flattened() }
        assertFailsWith<IllegalStateException> { listOf(quad, TranslatePop).flattened() }
        assertFailsWith<IllegalStateException> { listOf(TranslatePush(1f, 1f), TranslatePop, TranslatePop).flattened() }
    }

    @Test
    fun aPrimitiveThatBakesItsPositionInIsRefusedInsideARange() {
        val scaled = quad.copy(transform = DrawTransform(scaleX = 2f, scaleY = 2f, pivotX = 1f, pivotY = 2f))

        assertFailsWith<IllegalStateException> { listOf(TranslatePush(1f, 1f), scaled, TranslatePop).flattened() }
        assertEquals(listOf(scaled), listOf(scaled).flattened(), "outside a range it is drawn as it is")
    }
}
