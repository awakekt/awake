/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.EdgeFade
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class UiRasterizerEdgeFadeTest {
    @Test
    fun edgeFadeFeathersSmoothlyFromSolidInteriorToTransparentEdges() {
        val white = ImageBitmap(
            width = 1,
            height = 1,
            pixels = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
        )
        val fade = EdgeFade(left = 0.25f, top = 0f, right = 0.25f, bottom = 0.65f)
        val map = listOf(
            UiDrawPrimitive.Texture(
                x = 0f,
                y = 0f,
                w = 100f,
                h = 100f,
                material = white,
                edgeFade = fade,
            ),
        ).rasterizeToPixelMap(100, 100, background = Color.Black)

        // Solid top-center stays full white (255).
        assertTrue(map.sample(50, 10).r >= 250, "solid top-center expected ~255, got ${map.sample(50, 10).r}")
        // Left margin ramps monotonically from ~0 at x=0 to ~255 at x=25.
        val l0 = map.sample(0, 10).r
        val lMid = map.sample(12, 10).r
        val lIn = map.sample(26, 10).r
        assertTrue(l0 < 10 && lMid in 100..155 && lIn >= 250, "left fade expected smooth ramp, got $l0 -> $lMid -> $lIn")
        // Bottom margin ramps monotonically from ~255 at y=34 down to ~0 at y=99.
        val bTop = map.sample(50, 34).r
        val bMid = map.sample(50, 67).r
        val bFoot = map.sample(50, 99).r
        assertTrue(bTop >= 250 && bMid in 100..155 && bFoot < 10, "bottom fade expected smooth ramp, got $bTop -> $bMid -> $bFoot")
    }
}
