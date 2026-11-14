/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.vulkan.renderer.renderUiToTexture
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #1545: a `RoundedQuad` with a stroke width draws its border band on the GPU, solid on a
 * pixel-aligned 1 px edge, and leaves its middle and the corner outside its arc empty.
 */
class RendererHeadlessAnalyticBorderTest {
    @Test
    fun aStrokedRoundedQuadDrawsOnlyItsBand() {
        RendererHeadlessUiGlyphBaselineTest.withSharedHeadlessRenderer { renderer ->
            val target = renderer.createRenderTarget(SIZE, SIZE)
            val border = UiDrawPrimitive.RoundedQuad(8f, 8f, 48f, 48f, Color(1f, 1f, 1f, 1f), radius = 10f, strokeWidth = 1f)
            renderer.renderUiToTexture(target, listOf(border), font = null)
            val pixels = runBlocking { renderer.readPixels(target) }.data
            fun alpha(x: Int, y: Int) = pixels[(y * SIZE + x) * 4 + 3].toInt() and 0xFF

            assertEquals(255, alpha(32, 8), "a pixel-aligned 1 px top edge is one solid pixel")
            assertEquals(0, alpha(32, 9), "and stops there")
            assertEquals(0, alpha(32, 32), "the middle stays empty")
            assertEquals(0, alpha(8, 8), "the box corner outside the 10 px arc stays empty")
            assertTrue(alpha(11, 11) in 1..254, "the corner arc is anti-aliased, got ${alpha(11, 11)}")
        }
    }

    private companion object {
        const val SIZE = 64
    }
}
