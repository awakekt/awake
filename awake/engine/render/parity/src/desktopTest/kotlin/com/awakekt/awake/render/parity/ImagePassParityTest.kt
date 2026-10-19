/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.effects.TintImageEffect
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.pipeline.EffectQuality
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that Vulkan and WebGPU produce identical pixel output when executing a multi-step
 * [com.awakekt.awake.render.imagepass.ImagePassChain] built by [TintImageEffect] mid-UI-pass.
 */
class ImagePassParityTest {

    @Test
    fun bothBackendsExecuteImagePassChainAndResumeUiPassIdentically() {
        val captures = BACKEND_ORDER.associateWith { backend ->
            openHeadlessUi(backend, SIZE).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.High
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    val context = renderer.imagePassTargetContext(target)
                    val bounds = ImagePassRect(16f, 16f, 32f, 32f)
                    val placement = TintImageEffect.placement(
                        primitiveIndex = 1,
                        bounds = bounds,
                        tintColor = Color(1f, 0f, 1f, 1f),
                        strength = 1f,
                        targetContext = context,
                    )
                    val primitives = listOf(
                        UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                        UiDrawPrimitive.Quad(28f, 28f, 8f, 8f, Color(0f, 1f, 1f, 1f)),
                    )
                    renderer.drawUiToTexture(
                        target = target,
                        primitives = primitives,
                        imagePasses = listOf(placement),
                    )
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
        }

        val vulkan = captures.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = captures.getValue(HeadlessUiBackend.WebGpu)

        var differingPixels = 0
        for (offset in vulkan.indices step 4) {
            val diff = (0..3).any { channel ->
                val v = vulkan[offset + channel].toInt() and 0xFF
                val w = webGpu[offset + channel].toInt() and 0xFF
                abs(v - w) > CHANNEL_TOLERANCE
            }
            if (diff) differingPixels++
        }

        assertTrue(
            differingPixels == 0,
            "Vulkan and WebGPU disagreed on $differingPixels pixels in ImagePassParityTest",
        )
    }

    private companion object {
        const val SIZE = 64
        const val CHANNEL_TOLERANCE = 4
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
    }
}
