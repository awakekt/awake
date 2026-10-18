/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.testing.BackdropBlurProbe
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Frosted glass on Vulkan, through the swapchain UI pass and an offscreen target, compared pixel
 * for pixel with [BackdropBlurProbe], the CPU twin of the same passes. Validation is on, so a wrong
 * layout or barrier around the pass break fails the run too.
 *
 * Measured: the GPU glass lands within 2 steps of the CPU twin, and the fallback within 0.
 *
 * Negative controls, measured before committing, each failing [TOLERANCE]: taps at offset 0 put
 * the glass 122 to 141 steps off; skipping the last upsample left it sampling a level that still
 * held the downsample, 75 to 141 steps off; and letting the inner of two nested panels share the
 * outer one's blur, ignoring that the outer panel was painted over what it reads, put it 200 steps
 * off.
 */
class RendererHeadlessBackdropBlurTest {

    /** A hard white/black edge and a red bar: blurring them leaves a measurable ramp. */
    private fun backdrop(quality: BackdropBlurQuality = BackdropBlurQuality.High) = BackdropBlurProbe(SIZE, SIZE, quality).apply {
        fill(0, 0, SIZE / 2, SIZE, Color.White)
        fill(SIZE / 2, 0, SIZE / 2, SIZE, Color.Black)
        fill(0, 56, SIZE, 12, Color(1f, 0f, 0f, 1f))
    }

    private fun panel(x: Float, y: Float, w: Float, h: Float, radius: Float, tint: Color = Color.Transparent, saturation: Float = 1f) =
        UiDrawPrimitive.BackdropBlur(x, y, w, h, blurRadius = radius, tint = tint, saturation = saturation, cornerRadius = 12f)

    @Test
    fun glassBlursWhatTheSwapchainPassDrewBehindIt() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val probe = backdrop().apply { glass(panel(24f, 24f, 80f, 80f, radius = 6f)) }
        assertMatches(probe, presented(renderer, probe))
        // Outside the glass the edge stays hard; inside it is a ramp.
        assertTrue(probe.expected(63, 10, 0) == 255 && probe.expected(64, 10, 0) == 0)
        assertTrue(probe.expected(64, 40, 0) in 60..200, "the probe itself blurs the edge: ${probe.expected(64, 40, 0)}")
    }

    @Test
    fun glassInsideGlassSeesTheOuterPanel() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val probe = backdrop().apply {
            glass(panel(8f, 8f, 112f, 112f, radius = 4f, tint = Color(0.2f, 0.4f, 1f, 0.5f)))
            glass(panel(40f, 40f, 48f, 48f, radius = 4f, saturation = 1.6f))
        }
        assertMatches(probe, presented(renderer, probe))
    }

    /** Two panels that cannot see each other share one blur, and each still shows its own backdrop. */
    @Test
    fun sideBySidePanelsShareABlur() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val probe = backdrop().apply {
            glass(panel(40f, 4f, 48f, 36f, radius = 3f))
            glass(panel(40f, 88f, 48f, 36f, radius = 3f))
        }
        assertMatches(probe, presented(renderer, probe))
        assertTrue(renderer.uiBlurCount == 1, "one blur served both panels, ran ${renderer.uiBlurCount}")
    }

    @Test
    fun glassBlursAnOffscreenTarget() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        renderer.backdropBlurQuality = BackdropBlurQuality.Medium
        val probe = backdrop(BackdropBlurQuality.Medium).apply { glass(panel(16f, 30f, 96f, 60f, radius = 8f, tint = Color(1f, 1f, 1f, 0.2f))) }
        val target = renderer.createRenderTarget(SIZE, SIZE)
        try {
            renderer.drawUiToTexture(target, probe.primitives)
            assertMatches(probe, runBlocking { renderer.readPixels(target) }.data)
        } finally {
            target.destroy()
        }
    }

    @Test
    fun offFillsThePanelWithItsFallback() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        renderer.backdropBlurQuality = BackdropBlurQuality.Off
        val probe = backdrop(BackdropBlurQuality.Off).apply { glass(panel(24f, 24f, 80f, 80f, radius = 6f, tint = Color(0f, 0f, 0f, 0.3f))) }
        assertMatches(probe, presented(renderer, probe))
        assertTrue(renderer.uiBlurCount == 0)
    }

    private fun presented(renderer: Renderer, probe: BackdropBlurProbe): ByteArray {
        if (probe.quality != BackdropBlurQuality.Off) renderer.backdropBlurQuality = probe.quality
        renderer.drawUi(probe.primitives)
        renderer.presentWithoutScene()
        return runBlocking { renderer.readPresentedPixels() }.data
    }

    private fun assertMatches(probe: BackdropBlurProbe, rgba: ByteArray) {
        val difference = probe.compare(rgba)
        assertTrue(difference.steps <= TOLERANCE, "GPU glass is ${difference.steps} steps off the CPU twin at (${difference.x}, ${difference.y})")
    }

    private companion object {
        const val SIZE = 128
        const val FRAMES_IN_FLIGHT = 2

        /** Each level is stored in 8 bits, so every pass rounds; the measured worst is 2. */
        const val TOLERANCE = 3
    }
}
