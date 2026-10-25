/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.effects.BloomEffect
import com.awakekt.awake.render.effects.FxaaEffect
import com.awakekt.awake.render.effects.PostProcessingEffect
import com.awakekt.awake.render.effects.PostProcessingStack
import com.awakekt.awake.render.effects.ToneMappingGradeEffect
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassPlacement
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A step that reads [ImagePassSource.DestinationSnapshot] and writes the active target must read a
 * copy, not sample the image it draws into (#1010). Tone-only and bloom-only chains end with such
 * a step. Validation is on in both sessions and any error fails the run as the device is destroyed,
 * which is the gate: lavapipe executes in order, so the hazard need not show in the pixels.
 */
class VulkanDestinationSnapshotTest {

    @Test
    fun toneOnlyChainReadsACopyOffscreenAndMatchesTheCpuTwin() =
        vulkanHeadlessUi(SIZE, SIZE).use { session ->
            val renderer = session.renderer as Renderer
            renderer.effectQuality = EffectQuality.High
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val tone = ToneMappingGradeEffect(exposure = TONE_EXPOSURE)
                val chain = PostProcessingStack.buildChain(listOf(tone), renderer.imagePassTargetContext(target))
                assertDrawsIntoTheTargetFromACopy(chain)

                renderer.drawUiToTexture(target, Scene, imagePasses = listOf(ImagePassPlacement(Scene.size, chain)))
                val actual = runBlocking { renderer.readPixels(target) }.data

                assertMatchesToneTwin(actual, x = 8, y = 8, source = Background)
                assertMatchesToneTwin(actual, x = 32, y = 32, source = Square)
            } finally {
                target.destroy()
            }
        }

    @Test
    fun bloomOnlyChainReadsACopyOffscreen() =
        vulkanHeadlessUi(SIZE, SIZE).use { session ->
            val renderer = session.renderer as Renderer
            renderer.effectQuality = EffectQuality.High
            val target = renderer.createRenderTarget(SIZE, SIZE)
            try {
                val chain = PostProcessingStack.buildChain(listOf(Bloom), renderer.imagePassTargetContext(target))
                assertDrawsIntoTheTargetFromACopy(chain)

                renderer.drawUiToTexture(target, Scene, imagePasses = listOf(ImagePassPlacement(Scene.size, chain)))
                val actual = runBlocking { renderer.readPixels(target) }.data

                // Bloom only adds light: the square stays at least as bright, and its glow reaches
                // the background beside it, which a pass that lost the scene would not show.
                assertTrue(channel(actual, 32, 32, 0) >= SQUARE_RED - 1, "the bright square lost its colour")
                assertTrue(channel(actual, 22, 32, 0) > channel(actual, 2, 2, 0), "no glow next to the bright square")
            } finally {
                target.destroy()
            }
        }

    @Test
    fun toneOnlyAndBloomOnlyChainsOnTheSwapchainRaiseNoValidationErrors() {
        for (effect in listOf<PostProcessingEffect>(ToneMappingGradeEffect(exposure = TONE_EXPOSURE), Bloom)) {
            withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
                renderer.effectQuality = EffectQuality.High
                val chain = PostProcessingStack.buildChain(listOf(effect), renderer.imagePassTargetContext())
                assertDrawsIntoTheTargetFromACopy(chain)

                renderer.drawUi(primitives = Scene, imagePasses = listOf(ImagePassPlacement(Scene.size, chain)))
                renderer.presentWithoutScene()
                runBlocking { renderer.readPresentedPixels() }
            }
        }
    }

    @Test
    fun fxaaOnlyOnTheSwapchainChangesOnlyEdgePixels() {
        val plain = presentedFrame(effects = emptyList())
        val smoothed = presentedFrame(effects = listOf(FxaaEffect(quality = EffectQuality.High)))

        var edgesChanged = 0
        for (index in 0 until SIZE * SIZE * 3) {
            val x = index / 3 % SIZE
            val y = index / 3 / SIZE
            val c = index % 3
            val change = abs(channel(smoothed, x, y, c) - channel(plain, x, y, c))
            val nearEdge = SQUARE_EDGES.any { abs(x - it) <= EDGE_REACH || abs(y - it) <= EDGE_REACH }
            if (nearEdge && change > 0) edgesChanged++
            assertTrue(nearEdge || change <= 1, "FXAA changed a flat pixel ($x, $y) channel $c by $change")
        }
        assertTrue(edgesChanged > 0, "FXAA left every edge pixel as it was")
    }

    private fun presentedFrame(effects: List<PostProcessingEffect>): ByteArray {
        var pixels = ByteArray(0)
        withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
            renderer.effectQuality = EffectQuality.High
            val chain = PostProcessingStack.buildChain(effects, renderer.imagePassTargetContext())
            val passes = if (chain.isEmpty) emptyList() else listOf(ImagePassPlacement(Scene.size, chain))
            renderer.drawUi(primitives = Scene, imagePasses = passes)
            renderer.presentWithoutScene()
            pixels = runBlocking { renderer.readPresentedPixels() }.data
        }
        return pixels
    }

    private fun assertMatchesToneTwin(actual: ByteArray, x: Int, y: Int, source: Color) {
        val expected = PostProcessingStack.evaluateToneMappingGradeReference(
            radiance = floatArrayOf(source.r, source.g, source.b),
            exposure = TONE_EXPOSURE,
        )
        for (c in 0 until 3) {
            val want = (expected[c].coerceIn(0f, 1f) * MAX_CHANNEL).roundToInt()
            val got = channel(actual, x, y, c)
            assertTrue(abs(want - got) <= TWIN_TOLERANCE, "pixel ($x, $y) channel $c: $got, the CPU twin gives $want")
        }
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 2
        const val MAX_CHANNEL = 255
        const val TONE_EXPOSURE = 1.5f
        const val TWIN_TOLERANCE = 3
        const val SQUARE_RED = 230
        const val EDGE_REACH = 2

        /** The square spans 24 until 40 on both axes; FXAA may only touch pixels near those lines. */
        val SQUARE_EDGES = listOf(24, 40)
        val Background = Color(0.2f, 0.3f, 0.5f, 1f)
        val Square = Color(SQUARE_RED / 255f, 0.9f, 0.85f, 1f)
        val Scene = listOf(
            UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Background),
            UiDrawPrimitive.Quad(24f, 24f, 16f, 16f, Square),
        )
        val Bloom = BloomEffect(intensity = 1f, threshold = 0.5f, knee = 0.5f, radius = 8f)
    }
}

/** The case under test: the chain's last step draws into the target, and reads a copy to do it. */
private fun assertDrawsIntoTheTargetFromACopy(chain: ImagePassChain) {
    assertTrue(chain.steps.last().destination is ImagePassDestination.ActiveTarget, "the chain no longer ends by drawing into the target")
    val sampling = chain.steps.filter { step ->
        step.destination is ImagePassDestination.ActiveTarget && step.inputs.any { it.source == ImagePassSource.DestinationSnapshot }
    }
    assertTrue(sampling.isEmpty(), "steps sample the target they draw into: ${sampling.map { it.shader.id }}")
}

private fun channel(pixels: ByteArray, x: Int, y: Int, c: Int): Int = pixels[(y * 64 + x) * 4 + c].toInt() and 0xFF
