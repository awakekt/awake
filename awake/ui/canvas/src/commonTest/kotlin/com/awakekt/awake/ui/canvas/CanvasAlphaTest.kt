/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Verifies [CanvasElement.alpha] / [CanvasAppearance.alpha]: clamping, revision accounting,
 * multiplicative combination with [CanvasStyle.alpha], and preservation of [CanvasElement.style]
 * without allocating a copy.
 */
class CanvasAlphaTest {
    @Test
    fun defaultAlphaAllocatesNoAppearanceAndClampsRange() {
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            width = 40f
            height = 20f
        }
        assertEquals(1f, element.alpha)
        assertNull(element.get<CanvasAppearance>(), "default alpha should not create CanvasAppearance")

        val rev0 = element.contentRevision
        element.alpha = 1f
        element.alpha = 1.5f
        element.alpha = Float.NaN
        assertEquals(1f, element.alpha)
        assertEquals(rev0, element.contentRevision, "clamped no-op writes must not move contentRevision")
        assertNull(element.get<CanvasAppearance>())

        element.alpha = 0.4f
        assertEquals(0.4f, element.alpha)
        val rev1 = element.contentRevision
        assertTrue(rev1 != rev0, "changing alpha must advance contentRevision")

        element.alpha = 0.4f
        assertEquals(rev1, element.contentRevision, "repeating the same alpha must not advance contentRevision")

        element.alpha = -0.25f
        assertEquals(0f, element.alpha)
        assertTrue(element.contentRevision != rev1)

        element.alpha = 1f
        assertEquals(1f, element.alpha)
    }

    @Test
    fun elementAlphaMultipliesStyleAlphaWithoutReplacingStyleObject() {
        val baseStyle = CanvasStyle(alpha = 0.5f)
        val element = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            width = 40f
            height = 20f
            background = "#FF0000FF"
            style = baseStyle
        }
        val items = listOf(CanvasItem(1, element))
        val host = ComposeHost()
        val input = FrameInput(200, 100)

        fun quadAlpha(): Float {
            val quads = host.frame(input) { CanvasOverlay(items) }.primitives.filterIsInstance<UiDrawPrimitive.Quad>()
            assertTrue(quads.isNotEmpty(), "expected panel background quad")
            return quads.first().color.a
        }

        assertApprox(0.5f, quadAlpha())

        element.alpha = 0.5f
        assertSame(baseStyle, element.style, "mutating element.alpha must not copy CanvasStyle")
        assertApprox(0.25f, quadAlpha())

        element.alpha = 0f
        assertApprox(0f, quadAlpha())

        element.alpha = 1f
        assertApprox(0.5f, quadAlpha())
    }

    private fun assertApprox(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= EPSILON, "expected $expected +- $EPSILON, got $actual")
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}
