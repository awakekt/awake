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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless parity verification for [UiDrawPrimitive.BackdropBlur] across Vulkan and WebGPU.
 */
class BackdropBlurParityTest {

    @Test
    @Suppress("LongMethod")
    fun bothBackendsExecuteBackdropBlurWithParityAndMatchReference() {
        val captures = BACKEND_ORDER.associateWith { backend ->
            openHeadlessUi(backend, SIZE).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.High
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    val primitives = listOf(
                        // Left half blue, right half red
                        UiDrawPrimitive.Quad(0f, 0f, 32f, 64f, Color(0f, 0f, 1f, 1f)),
                        UiDrawPrimitive.Quad(32f, 0f, 32f, 64f, Color(1f, 0f, 0f, 1f)),
                        // Centered backdrop blur
                        UiDrawPrimitive.BackdropBlur(
                            x = 16f,
                            y = 16f,
                            w = 32f,
                            h = 32f,
                            blurRadius = 16f,
                            tint = Color.Transparent,
                        ),
                    )
                    renderer.drawUiToTexture(target = target, primitives = primitives)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
        }

        val vulkan = captures.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = captures.getValue(HeadlessUiBackend.WebGpu)

        val webGpuLinear = ByteArray(webGpu.size)
        for (offset in webGpu.indices step 4) {
            webGpuLinear[offset] = srgbToLinearByte(webGpu[offset].toInt() and 0xFF).toByte()
            webGpuLinear[offset + 1] = srgbToLinearByte(webGpu[offset + 1].toInt() and 0xFF).toByte()
            webGpuLinear[offset + 2] = srgbToLinearByte(webGpu[offset + 2].toInt() and 0xFF).toByte()
            webGpuLinear[offset + 3] = webGpu[offset + 3]
        }

        var differingPixels = 0
        for (offset in vulkan.indices step 4) {
            val diff = (0..3).any { channel ->
                val v = vulkan[offset + channel].toInt() and 0xFF
                val w = webGpuLinear[offset + channel].toInt() and 0xFF
                abs(v - w) > CHANNEL_TOLERANCE
            }
            if (diff) differingPixels++
        }

        assertTrue(
            differingPixels == 0,
            "Vulkan and WebGPU disagreed on $differingPixels pixels in BackdropBlurParityTest",
        )

        // At x = 32, y = 32 (center of blur on the split seam), color should be a smooth blend of blue and red (purple)
        val centerOffset = (32 * SIZE + 32) * 4
        val r = vulkan[centerOffset].toInt() and 0xFF
        val g = vulkan[centerOffset + 1].toInt() and 0xFF
        val b = vulkan[centerOffset + 2].toInt() and 0xFF
        assertTrue(r > 50, "Expected red contribution from blurred right half, but r=$r")
        assertTrue(b > 50, "Expected blue contribution from blurred left half, but b=$b")
        assertEquals(0, g, "Expected green channel to remain near 0, but g=$g")

        // Verify against pure CPU BackdropBlurReference
        val basePixels = FloatArray(SIZE * SIZE * 4)
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val idx = (y * SIZE + x) * 4
                if (x < 32) {
                    basePixels[idx + 2] = 1f // Blue
                    basePixels[idx + 3] = 1f
                } else {
                    basePixels[idx] = 1f // Red
                    basePixels[idx + 3] = 1f
                }
            }
        }
        val region = BackdropRegion(16, 16, 48, 48)
        val blurChain = requireNotNull(BlurChain.forRadius(16f, EffectQuality.High))
        val reference = BackdropBlurReference.blur(basePixels, SIZE, SIZE, region, blurChain)
        val refSample = FloatArray(4)
        reference.sample(32, 32, refSample)
        val refR = (refSample[0] * 255f).roundToInt()
        val refB = (refSample[2] * 255f).roundToInt()
        assertTrue(abs(r - refR) <= 6, "Vulkan R=$r did not match CPU reference R=$refR ± 6")
        assertTrue(abs(b - refB) <= 6, "Vulkan B=$b did not match CPU reference B=$refB ± 6")
    }

    @Test
    fun offQualityTierDrawsFallbackColor() {
        val captures = BACKEND_ORDER.associateWith { backend ->
            openHeadlessUi(backend, SIZE).use { session ->
                val renderer = session.renderer
                renderer.effectQuality = EffectQuality.Off
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    val fallbackColor = Color(0f, 1f, 0f, 1f)
                    val primitives = listOf(
                        UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                        UiDrawPrimitive.BackdropBlur(
                            x = 16f,
                            y = 16f,
                            w = 32f,
                            h = 32f,
                            blurRadius = 16f,
                            fallback = fallbackColor,
                        ),
                    )
                    renderer.drawUiToTexture(target = target, primitives = primitives)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
        }

        val vulkan = captures.getValue(HeadlessUiBackend.Vulkan)
        val webGpu = captures.getValue(HeadlessUiBackend.WebGpu)

        // Center pixel (x=24, y=24) must match fallback (Green: 0, 255, 0, 255)
        for (pixels in listOf(vulkan, webGpu)) {
            val centerOffset = (24 * SIZE + 24) * 4
            val r = pixels[centerOffset].toInt() and 0xFF
            val g = pixels[centerOffset + 1].toInt() and 0xFF
            val b = pixels[centerOffset + 2].toInt() and 0xFF
            val a = pixels[centerOffset + 3].toInt() and 0xFF
            assertEquals(0, r, "Fallback red should be 0")
            assertEquals(255, g, "Fallback green should be 255")
            assertEquals(0, b, "Fallback blue should be 0")
            assertEquals(255, a, "Fallback alpha should be 255")
        }
    }

    private companion object {
        const val SIZE = 64
        const val CHANNEL_TOLERANCE = 4
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)

        fun srgbToLinearByte(srgbByte: Int): Int {
            val s = srgbByte / 255.0
            val linear = if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            return (linear * 255.0).roundToInt().coerceIn(0, 255)
        }
    }
}
