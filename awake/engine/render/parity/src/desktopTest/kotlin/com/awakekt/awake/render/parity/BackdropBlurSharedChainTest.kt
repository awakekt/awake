/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.effects.BackdropBlurReference
import com.awakekt.awake.render.effects.BackdropRegion
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Two blurred panels too far apart to see each other share one blur chain (#1512); each must still
 * blur its own area, as the CPU reference does for it alone, on both backends.
 */
class BackdropBlurSharedChainTest {
    private val blue = Color(0f, 0f, 1f, 1f)
    private val red = Color(1f, 0f, 0f, 1f)
    private val green = Color(0f, 1f, 0f, 1f)
    private val yellow = Color(1f, 1f, 0f, 1f)

    /** Four vertical stripes whose seams sit under the centres of the two panels (x = 32 and x = 416). */
    private val stripes = listOf(0f to blue, 32f to red, 256f to green, 416f to yellow)
    private val panels = listOf(16f, 400f)

    private fun primitives(): List<UiDrawPrimitive> = stripes.mapIndexed { index, (x, color) ->
        val end = stripes.getOrNull(index + 1)?.first ?: SIZE.toFloat()
        UiDrawPrimitive.Quad(x, 0f, end - x, SIZE.toFloat(), color)
    } + panels.map { x ->
        UiDrawPrimitive.BackdropBlur(x = x, y = 16f, w = 32f, h = 32f, blurRadius = 16f, tint = Color.Transparent)
    }

    private fun scene(): FloatArray {
        val pixels = FloatArray(SIZE * SIZE * 4)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val color = stripes.last { it.first <= x }.second
                val at = (y * SIZE + x) * 4
                pixels[at] = color.r
                pixels[at + 1] = color.g
                pixels[at + 2] = color.b
                pixels[at + 3] = 1f
            }
        }
        return pixels
    }

    @Test
    fun eachOfTwoPanelsSharingOneChainBlursItsOwnArea() {
        val blur = requireNotNull(BlurChain.forRadius(16f, EffectQuality.High))
        val base = scene()
        for (backend in BACKEND_ORDER) {
            val pixels = openHeadlessUi(backend, SIZE).use { session ->
                session.renderer.effectQuality = EffectQuality.High
                val target = session.renderer.createRenderTarget(SIZE, SIZE)
                try {
                    session.renderer.drawUiToTexture(target = target, primitives = primitives())
                    runBlocking { session.renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
            for (x in panels) {
                val region = BackdropRegion.around(x, 16f, 32f, 32f, blur.extent, SIZE, SIZE)
                val reference = FloatArray(4)
                BackdropBlurReference.blur(base, SIZE, SIZE, region, blur).sample((x + 16f).toInt(), 32, reference)
                val at = (32 * SIZE + (x + 16f).toInt()) * 4
                for (channel in 0..2) {
                    val got = pixels[at + channel].toInt() and 0xFF
                    val want = (reference[channel] * 255f).roundToInt()
                    assertTrue(abs(got - want) <= TOLERANCE, "$backend panel at x=$x channel $channel: $got, reference $want")
                }
            }
        }
    }

    private companion object {
        const val SIZE = 512
        const val TOLERANCE = 6
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
    }
}
