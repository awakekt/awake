/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.LinearGradient
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Negative control: the old rectangular shadow fill gives alpha=255 at (27,17), expected 0.
 * The square case keeps a genuinely interior corner opaque, preventing a circle-only workaround.
 */
class RoundedShadowRasterTest {
    @Test
    fun roundShadowHasTransparentCornersAndAnOpaqueCenter() {
        val red = Color(1f, 0f, 0f, 1f)
        for (gradient in listOf(null, LinearGradient(Color.White, red, red, Color.White))) {
            val pixels = listOf(
                UiDrawPrimitive.ShadowQuad(10f, 10f, 40f, 40f, 20f, 20f, 10f, 0f, 5f, Color.White, gradient = gradient),
            ).rasterize(80, 80, Color.Transparent)
            fun alpha(x: Int, y: Int): Int = pixels[(y * 80 + x) * 4 + 3].toInt() and 255
            assertEquals(0, alpha(27, 17), "round shadow's outset corner, gradient=$gradient")
            assertTrue(alpha(50, 40) > 250, "round shadow's center, gradient=$gradient")
        }
    }

    @Test
    fun squareShadowRetainsItsCorner() {
        val pixels = listOf(UiDrawPrimitive.ShadowQuad(10f, 10f, 40f, 40f, 0f, 0f, 0f, 0f, 0f, Color.White))
            .rasterize(64, 64, Color.Transparent)
        assertEquals(255, pixels[(14 * 64 + 14) * 4 + 3].toInt() and 255)
    }
}
