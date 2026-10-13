/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Glyphs of two fonts staged by one `drawUi` and recorded by the swapchain UI pass: every font
 * batch is drawn, and frame after frame of switching fonts neither rebuilds the glyph pipeline nor
 * uploads an atlas again. The pixels are `UiMultiFontAtlasTest`'s, on the offscreen path; this
 * surface is 1x1.
 */
class WebGpuMultiFontUiTest {
    private val roboto = UiFonts.default()
    private val bitmap = UiFonts.bitmap()

    @Test
    fun everyFontBatchDrawsAndNothingIsRebuiltAfterTheFirstFrame() = withPresentingUi { renderer, frame ->
        val ui = listOf(glyph(roboto), glyph(bitmap), glyph(roboto))
        renderer.drawUi(ui, roboto)
        frame()
        assertEquals(3, renderer.statsCounter.latest?.drawCalls, "one draw per font batch")

        val pipeline = checkNotNull(renderer.uiGlyphRenderPipeline)
        val atlases = pipeline.fontBindings.mapValues { it.value.texture }
        repeat(3) {
            renderer.drawUi(ui, roboto)
            frame()
            assertEquals(3, renderer.statsCounter.latest?.drawCalls)
        }

        assertSame(pipeline, renderer.uiGlyphRenderPipeline, "the glyph pipeline was rebuilt")
        assertEquals(2, pipeline.fontBindings.size)
        atlases.forEach { (font, texture) ->
            assertSame(texture, pipeline.fontBindings[font]?.texture, "an atlas was uploaded again")
        }
    }

    private fun glyph(font: UiFont): UiDrawPrimitive.Glyph {
        val rect = checkNotNull(font.glyphFor('H', FontWeight.Normal))
        return UiDrawPrimitive.Glyph(0f, 0f, 1f, 1f, rect.u0, rect.v0, rect.u1, rect.v1, Color.White, font = font)
    }
}
