/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.capture.PixelMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A clipped emitter on a View or a Sweep is cut to the element's own mask, not only to a clip around
 * the overlay: the `CanvasView.mask` and sweep-mask branches of the particle draw.
 *
 * One still red particle `40 × 40` dp covers a `40 × 40` element, which a top-left anchor places at
 * `(16, 16)` in an `80 × 80` frame. Cut to a circle, the element's corner `(18, 18)` is empty and its
 * centre `(36, 36)` red; the control with a `Box` mask draws the corner red too.
 */
class CanvasParticlesMaskPixelTest {
    @Test
    fun aViewsCircleMaskCutsItsParticles() {
        val masked = render(viewElement(CanvasMask.Circle))
        val control = render(viewElement(CanvasMask.Box))

        assertRed(masked, CENTRE, CENTRE, "the particle shows at the View's centre")
        assertEquals(0, masked.sample(CORNER, CORNER).a, "a Circle-masked View cuts its particle at the corner")
        assertRed(control, CORNER, CORNER, "control: a Box-masked View leaves the corner")
    }

    @Test
    fun aSweepsCircleMaskCutsItsParticles() {
        val masked = render(sweepElement(CanvasMask.Circle))
        val control = render(sweepElement(CanvasMask.Box))

        assertRed(masked, CENTRE, CENTRE, "the particle shows at the Sweep's centre")
        assertEquals(0, masked.sample(CORNER, CORNER).a, "a Circle-masked Sweep cuts its particle at the corner")
        assertRed(control, CORNER, CORNER, "control: a Box-masked Sweep leaves the corner")
    }

    private fun viewElement(mask: CanvasMask) = element(CanvasElementKind.View).apply {
        view = CanvasView(transparent = true, mask = mask)
    }

    private fun sweepElement(mask: CanvasMask) = element(CanvasElementKind.Sweep).apply {
        sweep = CanvasSweep(mask = mask)
        value = 0f
    }

    private fun element(kind: CanvasElementKind) = CanvasElement().apply {
        this.kind = kind
        anchor = CanvasAnchor.TopLeft
        width = SIZE_DP
        height = SIZE_DP
        particles = CanvasParticles(maxParticles = 1, random = Random(3)).apply {
            spawnRate = 0f
            speed = 0f
            lifetime = 100f
            size = SIZE_DP
            tint = Color(1f, 0f, 0f, 1f)
        }
    }

    private fun render(element: CanvasElement): PixelMap {
        checkNotNull(element.particles).emit(1)
        val out = ComposeHost().frame(FrameInput(FRAME, FRAME, deltaSeconds = 0f)) {
            CanvasOverlay(listOf(CanvasItem(1, element)))
        }
        return out.primitives.rasterizeToPixelMap(FRAME, FRAME, background = Color.Transparent)
    }

    private fun assertRed(map: PixelMap, x: Int, y: Int, message: String) {
        val pixel = map.sample(x, y)
        assertTrue(pixel.r > RED_FLOOR && pixel.g < GREEN_CEILING && pixel.a > RED_FLOOR, "$message: $pixel")
    }

    private companion object {
        const val FRAME = 80
        const val SIZE_DP = 40f
        const val CORNER = 18
        const val CENTRE = 36
        const val RED_FLOOR = 200
        const val GREEN_CEILING = 40
    }
}
