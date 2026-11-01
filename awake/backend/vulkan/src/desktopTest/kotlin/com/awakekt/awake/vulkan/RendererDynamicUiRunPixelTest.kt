/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.vulkan.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pixel-level guard for #1224's staging-array reuse against #1221's identity-skip.
 *
 * `drawUi` stages the swapchain overlay through the renderer's `DynamicRunArrayPool`, so a UI run
 * whose contents change every frame reuses pooled buffers. If the pool ever handed a mesh slot the
 * same array instance it saw last frame while the contents changed, `DynamicMesh.update` would skip
 * the upload and present a stale buffer. Here a solid quad moves across the surface every presented
 * frame; the presented pixels must change each frame, which they cannot if a changed run was
 * skipped.
 *
 * Negative control (measured 2026-11-01): forcing the staging pool to hand one fixed buffer every
 * frame (a single-generation pool, defeating the ping-pong) froze the quad -- `movedPixels` fell
 * from ~1,500 to **0** between consecutive frames and both assertions failed. Reverted. This is the
 * pixel-level counterpart to `UiRunStagingIdentityTest`'s unit control (uploads=1, skips=4).
 */
class RendererDynamicUiRunPixelTest {

    @Test
    fun aMovingUiRunReUploadsEveryPresentedFrame() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        // Warm up so the span is past its first sight and genuinely staged through the pool.
        present(renderer, quadAt(2f))
        present(renderer, quadAt(2f))

        var previous = present(renderer, quadAt(FIRST_X))
        for (step in 1 until STEPS) {
            val current = present(renderer, quadAt(FIRST_X + step * STRIDE))
            val moved = movedPixels(previous, current)
            assertTrue(
                moved > MIN_MOVED_PIXELS,
                "a moving quad changed only $moved pixels between presented frames -- a skipped " +
                    "upload presented a stale buffer (step $step)",
            )
            previous = current
        }
    }

    @Test
    fun anUnchangedUiRunPresentsIdenticalPixels() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        present(renderer, quadAt(20f))
        val first = present(renderer, quadAt(20f))
        val second = present(renderer, quadAt(20f))
        assertEquals(0, movedPixels(first, second), "an unchanged UI frame must present identical pixels")
    }

    private fun quadAt(x: Float): List<UiDrawPrimitive> =
        listOf(UiDrawPrimitive.Quad(x, 0f, QUAD_W, SIZE.toFloat(), Color.White))

    private fun present(renderer: Renderer, primitives: List<UiDrawPrimitive>): ByteArray {
        renderer.drawUi(primitives, null)
        renderer.presentWithoutScene()
        return runBlocking { renderer.readPresentedPixels() }.data
    }

    private fun movedPixels(a: ByteArray, b: ByteArray): Int {
        var count = 0
        var offset = 0
        while (offset < a.size) {
            if ((0 until 4).any { abs((a[offset + it].toInt() and 0xFF) - (b[offset + it].toInt() and 0xFF)) > CHANNEL_TOLERANCE }) {
                count += 1
            }
            offset += 4
        }
        return count
    }

    private companion object {
        const val SIZE = 64
        const val FRAMES_IN_FLIGHT = 1
        const val QUAD_W = 12f
        const val FIRST_X = 6f
        const val STRIDE = 14f
        const val STEPS = 4
        const val CHANNEL_TOLERANCE = 2
        const val MIN_MOVED_PIXELS = 200
    }
}
