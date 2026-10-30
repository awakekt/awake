/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue

class UiRasterizerTextureBlendTest {
    @Test
    fun premultipliedTextureBlendsWithoutDoubleDarkeningAndSupportsAdditivePlus() {
        // Premultiplied 50%-alpha white pixel: RGBA = (128, 128, 128, 128).
        val premulHalfWhite = ImageBitmap(
            width = 1,
            height = 1,
            pixels = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
        )
        val background = Color(0.5f, 0.5f, 0.5f, 1f) // 128 grey

        // SourceOver with premultiplied = true over 128 grey must brighten to ~192, never darken (< 128).
        val sourceOverMap = listOf(
            UiDrawPrimitive.Texture(
                x = 2f,
                y = 2f,
                w = 4f,
                h = 4f,
                material = premulHalfWhite,
                alpha = 0.5f,
                premultiplied = true,
                blendMode = BlendMode.SourceOver,
            ),
        ).rasterizeToPixelMap(8, 8, background = background)
        val overSample = sourceOverMap.sample(3, 3)
        assertTrue(overSample.r in 156..164, "expected ~160 without dark fringe, got ${overSample.r}")

        // Additive Plus stacking two premultiplied 40%-orange quads on black sums linearly to ~204.
        val premulOrange = ImageBitmap(
            width = 1,
            height = 1,
            pixels = byteArrayOf(102.toByte(), 51.toByte(), 0, 102.toByte()),
        )
        val additiveMap = listOf(
            UiDrawPrimitive.Texture(
                x = 1f,
                y = 1f,
                w = 6f,
                h = 6f,
                material = premulOrange,
                premultiplied = true,
                blendMode = BlendMode.Plus,
            ),
            UiDrawPrimitive.Texture(
                x = 1f,
                y = 1f,
                w = 6f,
                h = 6f,
                material = premulOrange,
                rotationDegrees = 90f,
                premultiplied = true,
                blendMode = BlendMode.Plus,
            ),
        ).rasterizeToPixelMap(8, 8, background = Color.Black)
        val addSample = additiveMap.sample(3, 3)
        assertTrue(addSample.r in 200..208, "expected ~204 additive red sum, got ${addSample.r}")
        assertTrue(addSample.g in 98..106, "expected ~102 additive green sum, got ${addSample.g}")
    }
}
