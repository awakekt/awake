/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [CanvasFillImageMode.Sweep] fills a Bar's box clockwise from 12 o'clock by its value, like a
 * cooldown overlay darkening an item or skill icon as it finishes.
 */
class CanvasSweepFillTest {

    private fun sweepWorld(value: Float, image: CanvasImage? = null): World = World().also { world ->
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.Bar
                offsetX = 0f
                offsetY = 0f
                width = SIZE.toFloat()
                height = SIZE.toFloat()
                this.value = value
                color = "#FFFFFF"
                style = CanvasStyle(fillImageMode = CanvasFillImageMode.Sweep, fillImage = image)
            },
        )
    }

    private fun pixels(world: World, images: Map<String, ImageBitmap> = emptyMap()): ByteArray =
        ComposeHost().frame(FrameInput(SIZE, SIZE)) { SceneCanvas(world, images = images) }.primitives.rasterize(SIZE, SIZE, Color.Black)

    private fun colorAt(pixels: ByteArray, x: Int, y: Int): Int {
        val offset = (y * SIZE + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    @Test
    fun atAQuarterOnlyTheTopRightQuadrantIsSwept() {
        val pixels = pixels(sweepWorld(0.25f))

        assertEquals(WHITE, colorAt(pixels, 7, 2), "top-right: swept clockwise from 12 o'clock to 3 o'clock")
        assertEquals(BLACK, colorAt(pixels, 2, 2), "top-left: not yet swept")
        assertEquals(BLACK, colorAt(pixels, 2, 7), "bottom-left: not yet swept")
        assertEquals(BLACK, colorAt(pixels, 7, 7), "bottom-right: not yet swept")
    }

    @Test
    fun zeroDrawsNothing() {
        val pixels = pixels(sweepWorld(0f))

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) assertEquals(BLACK, colorAt(pixels, x, y), "($x, $y) must stay the track colour")
        }
    }

    @Test
    fun oneFillsTheWholeBox() {
        val pixels = pixels(sweepWorld(1f))

        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) assertEquals(WHITE, colorAt(pixels, x, y), "($x, $y) must be swept at a full turn")
        }
    }

    @Test
    fun anOptionalFillImageIsCutToTheSameWedgeInsteadOfTheColour() {
        val pixels = pixels(sweepWorld(0.25f, CanvasImage("icon.png")), mapOf("icon.png" to GREEN_ICON))

        assertEquals(GREEN, colorAt(pixels, 7, 2), "the image fills the wedge the colour otherwise would")
        assertEquals(BLACK, colorAt(pixels, 2, 2), "the unswept three quarters still show the track")
    }

    private companion object {
        const val SIZE = 10
        const val WHITE = 0xFFFFFF
        const val BLACK = 0x000000
        const val GREEN = 0x00FF00

        /** 1 x 1, solid green. */
        val GREEN_ICON = ImageBitmap(1, 1, byteArrayOf(0, -1, 0, -1))
    }
}
