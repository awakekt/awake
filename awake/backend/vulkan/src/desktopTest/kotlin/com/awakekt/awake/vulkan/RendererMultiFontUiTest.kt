/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.FontWeight
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Glyphs of two fonts staged by one `drawUi` and recorded by the swapchain UI pass.
 *
 * Each font's glyph batch binds that font's atlas, and frame after frame of switching fonts neither
 * rebuilds the glyph pipeline nor uploads an atlas again: the pipeline, both atlases and both
 * descriptor sets are the objects the first frame built.
 *
 * Positive control, measured by binding only the first font a frame draws: the bitmap glyph's
 * columns differed from its one-font reference in 138 px, while the draw count stayed at 3.
 */
class RendererMultiFontUiTest {
    private val roboto = UiFonts.default()
    private val bitmap = UiFonts.bitmap()

    @Test
    fun eachGlyphBatchBindsItsOwnFontAndNothingIsRebuiltAfterTheFirstFrame() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val middle = glyph(bitmap, x = 22f)
        val frame = listOf(glyph(roboto, x = 2f), middle, glyph(roboto, x = 42f))

        // The bitmap glyph alone, the one-font way: no glyph font, the font passed to drawUi.
        renderer.drawUi(listOf(middle.copy(font = null)), bitmap)
        renderer.presentWithoutScene()
        val reference = presented(renderer)

        renderer.drawUi(frame, roboto)
        renderer.presentWithoutScene()
        assertEquals(3, renderer.statsCounter.latest?.drawCalls, "one draw per font batch")
        val columns = floor(middle.x).toInt() until ceil(middle.x + middle.w).toInt()
        assertTrue(frame.first().let { it.x + it.w } <= columns.first && frame.last().x >= columns.last + 1, "the glyphs overlap")
        val differing = differingPixels(presented(renderer), reference, columns)
        assertEquals(0, differing, "the bitmap glyph differs from itself drawn alone in its own font")

        val pipeline = checkNotNull(renderer.uiGlyphRenderPipeline)
        val atlases = renderer.fontAtlases.toMap()
        repeat(FRAMES_IN_FLIGHT * 2) {
            renderer.drawUi(frame, roboto)
            renderer.presentWithoutScene()
            assertEquals(3, renderer.statsCounter.latest?.drawCalls)
        }

        assertSame(pipeline, renderer.uiGlyphRenderPipeline, "the glyph pipeline was rebuilt")
        assertEquals(2, renderer.fontAtlases.size)
        atlases.forEach { (font, atlas) -> assertSame(atlas, renderer.fontAtlases[font], "an atlas was uploaded again") }
        assertEquals(2, pipeline.fontSlotCount, "a font's descriptor set was built again")
    }

    private fun glyph(font: UiFont, x: Float): UiDrawPrimitive.Glyph {
        val rect = checkNotNull(font.glyphFor('H', FontWeight.Normal))
        return UiDrawPrimitive.Glyph(
            x = x,
            y = 2f,
            w = GLYPH_PX * rect.widthEm,
            h = GLYPH_PX * rect.heightEm,
            u0 = rect.u0,
            v0 = rect.v0,
            u1 = rect.u1,
            v1 = rect.v1,
            color = Color.White,
            font = font,
        )
    }

    private fun presented(renderer: Renderer): ByteArray = runBlocking { renderer.readPresentedPixels() }.data

    private fun differingPixels(actual: ByteArray, expected: ByteArray, columns: IntRange): Int {
        var count = 0
        for (y in 0 until SIZE) {
            for (x in columns) {
                val offset = (y * SIZE + x) * 4
                if ((0 until 4).any { kotlin.math.abs(actual.channel(offset + it) - expected.channel(offset + it)) > 2 }) count++
            }
        }
        return count
    }

    private fun ByteArray.channel(index: Int): Int = this[index].toInt() and 0xFF

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val GLYPH_PX = 18f
    }
}
