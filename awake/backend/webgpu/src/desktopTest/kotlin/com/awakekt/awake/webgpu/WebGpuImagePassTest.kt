/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.effects.TintImageEffect
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headless WebGPU verification for generic [com.awakekt.awake.render.imagepass.ImagePassChain]
 * execution, mid-pass snapshot/resume ordering, quality-tier fallback, and transient target pooling.
 */
class WebGpuImagePassTest {

    @Test
    @Suppress("LongMethod")
    fun offscreenImagePassMatchesReferenceAndPreservesPostPassUiOrder() {
        val errors = mutableListOf<String>()
        webGpuHeadlessUi(onUncapturedError = { errors += it }).use { session ->
            val renderer = session.renderer as Renderer
            renderer.effectQuality = EffectQuality.High
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val context = renderer.imagePassTargetContext(target)
                val bounds = ImagePassRect(16f, 16f, 32f, 32f)
                val tintColor = Color(1f, 0f, 1f, 1f)
                val strength = 1f

                val backgroundOnly = listOf(
                    UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                )
                renderer.drawUiToTexture(target, backgroundOnly)
                val baseCapture = runBlocking { renderer.readPixels(target) }
                val expectedTinted = TintImageEffect.applyReference(
                    source = TextureAsset(baseCapture.data, baseCapture.width, baseCapture.height),
                    bounds = bounds,
                    tintColor = tintColor,
                    strength = strength,
                )

                val primitives = listOf(
                    UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                    UiDrawPrimitive.Quad(28f, 28f, 8f, 8f, Color(0f, 1f, 0f, 1f)),
                )
                val placement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = bounds,
                    tintColor = tintColor,
                    strength = strength,
                    targetContext = context,
                )
                renderer.drawUiToTexture(
                    target = target,
                    primitives = primitives,
                    imagePasses = listOf(placement),
                )
                val actual = runBlocking { renderer.readPixels(target) }.data

                assertPixelMatches(actual, expectedTinted.data, x = 8, y = 8, tolerance = 2)
                assertPixelMatches(actual, expectedTinted.data, x = 20, y = 20, tolerance = 2)
                assertPixelRgba(actual, x = 32, y = 32, r = 0, g = 255, b = 0, a = 255, tolerance = 2)

                val halfStrengthPlacement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = bounds,
                    tintColor = Color(1f, 0f, 0f, 1f),
                    strength = 0.5f,
                    targetContext = context,
                )
                renderer.drawUiToTexture(
                    target = target,
                    primitives = backgroundOnly,
                    imagePasses = listOf(halfStrengthPlacement),
                )
                val blended = runBlocking { renderer.readPixels(target) }.data
                val blendedOffset = (20 * SIZE + 20) * 4
                val blendedR = blended[blendedOffset].toInt() and 0xFF
                val blendedG = blended[blendedOffset + 1].toInt() and 0xFF
                val blendedB = blended[blendedOffset + 2].toInt() and 0xFF
                assertTrue(
                    blendedR in HALF_BLEND_RANGE && blendedB in HALF_BLEND_RANGE && abs(blendedR - blendedB) <= 3 && blendedG <= 2,
                    "Half-strength red tint over blue background expected symmetric purple in $HALF_BLEND_RANGE, was RGBA($blendedR, $blendedG, $blendedB)",
                )
            } finally {
                target.destroy()
            }
        }
        assertEquals(emptyList(), errors)
    }

    @Test
    fun presentingSwapchainImagePassExecutesWithoutValidationErrors() =
        withPresentingUi { renderer, frame ->
            renderer.effectQuality = EffectQuality.Medium
            val bounds = ImagePassRect(0f, 0f, 1f, 1f)
            val placement = TintImageEffect.placement(
                primitiveIndex = 1,
                bounds = bounds,
                tintColor = Color(1f, 0f, 0f, 1f),
                strength = 0.5f,
                targetContext = renderer.imagePassTargetContext(),
            )
            renderer.drawUi(
                primitives = listOf(
                    UiDrawPrimitive.Quad(0f, 0f, 1f, 1f, Color(0f, 0f, 1f, 1f)),
                ),
                imagePasses = listOf(placement),
            )
            frame()
            assertTrue((renderer.statsCounter.latest?.drawCalls ?: 0) >= 3)
        }

    @Test
    fun offQualityTierDrawsFallbackWithoutAllocatingTransientTargets() {
        val errors = mutableListOf<String>()
        webGpuHeadlessUi(onUncapturedError = { errors += it }).use { session ->
            val renderer = session.renderer as Renderer
            renderer.effectQuality = EffectQuality.Off
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val context = renderer.imagePassTargetContext(target)
                val bounds = ImagePassRect(16f, 16f, 32f, 32f)
                val placement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = bounds,
                    tintColor = Color(1f, 0f, 0f, 1f),
                    strength = 0.5f,
                    targetContext = context,
                )
                assertTrue(placement.chain.isEmpty)

                renderer.drawUiToTexture(
                    target = target,
                    primitives = listOf(
                        UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                    ),
                    imagePasses = listOf(placement),
                )
                val actual = runBlocking { renderer.readPixels(target) }.data
                assertEquals(0, renderer.imagePassExecutor.totalTargetAllocations)
                assertEquals(0, renderer.imagePassExecutor.pooledTargetCount)
                assertPixelRgba(actual, x = 8, y = 8, r = 0, g = 0, b = 255, a = 255, tolerance = 2)
                val centerOffset = (24 * SIZE + 24) * 4
                val centerR = actual[centerOffset].toInt() and 0xFF
                val centerB = actual[centerOffset + 2].toInt() and 0xFF
                assertTrue(
                    centerR in HALF_BLEND_RANGE && centerB in HALF_BLEND_RANGE,
                    "Fallback translucent quad expected purple in $HALF_BLEND_RANGE, was R=$centerR B=$centerB",
                )
            } finally {
                target.destroy()
            }
        }
        assertEquals(emptyList(), errors)
    }

    @Test
    @Suppress("LongMethod")
    fun transientTargetsReuseAcrossFramesRebuildOnResizeAndFreeOnRelease() {
        val errors = mutableListOf<String>()
        webGpuHeadlessUi(onUncapturedError = { errors += it }).use { session ->
            val renderer = session.renderer as Renderer
            renderer.effectQuality = EffectQuality.Medium
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val executor = renderer.imagePassExecutor
                val basePrimitives = listOf(
                    UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(0f, 0f, 1f, 1f)),
                )
                val initialBounds = ImagePassRect(16f, 16f, 32f, 32f)
                val firstPlacement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = initialBounds,
                    tintColor = Color(1f, 0f, 0f, 1f),
                    strength = 0.5f,
                    targetContext = renderer.imagePassTargetContext(target),
                )

                renderer.drawUiToTexture(target, basePrimitives, imagePasses = listOf(firstPlacement))
                assertEquals(1, executor.pooledTargetCount)
                assertEquals(1, executor.totalTargetAllocations)
                assertEquals(0, executor.totalTargetReuses)
                assertEquals(32 to 32, executor.pooledTargetDimensions(TintImageEffect.TRANSIENT_TARGET_ID))

                renderer.drawUiToTexture(target, basePrimitives, imagePasses = listOf(firstPlacement))
                renderer.drawUiToTexture(target, basePrimitives, imagePasses = listOf(firstPlacement))
                assertEquals(1, executor.pooledTargetCount)
                assertEquals(1, executor.totalTargetAllocations)
                assertEquals(2, executor.totalTargetReuses)

                val resizedBounds = ImagePassRect(8f, 8f, 48f, 48f)
                val resizedPlacement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = resizedBounds,
                    tintColor = Color(1f, 0f, 0f, 1f),
                    strength = 0.5f,
                    targetContext = renderer.imagePassTargetContext(target),
                )
                renderer.drawUiToTexture(target, basePrimitives, imagePasses = listOf(resizedPlacement))
                assertEquals(1, executor.pooledTargetCount)
                assertEquals(2, executor.totalTargetAllocations)
                assertEquals(2, executor.totalTargetReuses)
                assertEquals(48 to 48, executor.pooledTargetDimensions(TintImageEffect.TRANSIENT_TARGET_ID))

                renderer.effectQuality = EffectQuality.Low
                val lowQualityPlacement = TintImageEffect.placement(
                    primitiveIndex = 1,
                    bounds = resizedBounds,
                    tintColor = Color(1f, 0f, 0f, 1f),
                    strength = 0.5f,
                    targetContext = renderer.imagePassTargetContext(target),
                )
                renderer.drawUiToTexture(target, basePrimitives, imagePasses = listOf(lowQualityPlacement))
                assertEquals(1, executor.pooledTargetCount)
                assertEquals(3, executor.totalTargetAllocations)
                assertEquals(24 to 24, executor.pooledTargetDimensions(TintImageEffect.TRANSIENT_TARGET_ID))

                executor.releaseTransientTargets()
                assertEquals(0, executor.pooledTargetCount)
            } finally {
                target.destroy()
            }
        }
        assertEquals(emptyList(), errors)
    }

    private fun assertPixelMatches(
        actual: ByteArray,
        expected: ByteArray,
        x: Int,
        y: Int,
        tolerance: Int,
    ) {
        val offset = (y * SIZE + x) * 4
        for (channel in 0 until 4) {
            val a = actual[offset + channel].toInt() and 0xFF
            val e = expected[offset + channel].toInt() and 0xFF
            assertTrue(
                abs(a - e) <= tolerance,
                "Pixel ($x, $y) channel $channel expected $e ± $tolerance, but was $a",
            )
        }
    }

    @Suppress("LongParameterList")
    private fun assertPixelRgba(
        pixels: ByteArray,
        x: Int,
        y: Int,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        tolerance: Int,
    ) {
        val offset = (y * SIZE + x) * 4
        val actualR = pixels[offset].toInt() and 0xFF
        val actualG = pixels[offset + 1].toInt() and 0xFF
        val actualB = pixels[offset + 2].toInt() and 0xFF
        val actualA = pixels[offset + 3].toInt() and 0xFF
        assertTrue(
            abs(actualR - r) <= tolerance &&
                abs(actualG - g) <= tolerance &&
                abs(actualB - b) <= tolerance &&
                abs(actualA - a) <= tolerance,
            "Pixel ($x, $y) expected RGBA($r, $g, $b, $a) ± $tolerance, but was RGBA($actualR, $actualG, $actualB, $actualA)",
        )
    }

    private companion object {
        const val SIZE = 64

        /**
         * A 0.5 blend reads back as 128 from the UNORM target. The range no longer admits the 188 an
         * sRGB target stored, which encoded the shader's output a second time (#1011).
         */
        val HALF_BLEND_RANGE = 124..132
    }
}
