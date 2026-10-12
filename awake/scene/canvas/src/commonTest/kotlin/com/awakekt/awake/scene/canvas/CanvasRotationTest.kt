/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.graphics.drawscope.GraphicsLayerPlaceholder
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [CanvasStyle.rotation] turns an element's whole drawing -- here its picture -- about the box's
 * own centre through `Modifier.graphicsLayer`, the way a minimap arrow turns with a heading.
 */
class CanvasRotationTest {

    private fun world(rotation: Float?): World = World().also { world ->
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.Image
                offsetX = INSET.toFloat()
                offsetY = INSET.toFloat()
                width = PICTURE_WIDTH.toFloat()
                height = PICTURE_HEIGHT.toFloat()
                style = CanvasStyle(image = CanvasImage("arrow.png"), rotation = rotation)
            },
        )
    }

    private fun frame(rotation: Float?) = ComposeHost().frame(FrameInput(VIEWPORT, VIEWPORT)) {
        SceneCanvas(world(rotation), images = mapOf("arrow.png" to ARROW))
    }

    private fun colorAt(pixels: ByteArray, x: Int, y: Int): Int {
        val offset = (y * VIEWPORT + x) * 4
        return ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
    }

    @Test
    fun noRotationDrawsThePictureDirectlyWithNoLayer() {
        val output = frame(null)

        assertEquals(emptyList(), output.graphicsLayers)
        assertTrue(output.primitives.filterIsInstance<UiDrawPrimitive.Texture>().none { it.material is GraphicsLayerPlaceholder })
    }

    @Test
    fun a90DegreeRotationTurnsAnAsymmetricPictureClockwiseAboutItsCentre() {
        val output = frame(90f)
        val pixels = output.primitives.rasterize(VIEWPORT, VIEWPORT, Color.Black, layers = output.graphicsLayers)

        // The one red pixel starts at the picture's top-left corner. A clockwise quarter turn about
        // the (non-square) box's own centre carries it down past where the box's right edge was.
        assertEquals(RED, colorAt(pixels, INSET + 2, INSET - 1), "the red corner landed where a clockwise quarter turn puts it")
        assertEquals(BACKGROUND, colorAt(pixels, INSET, INSET), "its old corner is empty -- a square picture would hide this")
        assertEquals(BLUE, colorAt(pixels, INSET + 1, INSET + 1), "the rest of the turned picture is still blue")
    }

    @Test
    fun theCompositedTextureCarriesTheRequestedRotation() {
        val output = frame(45f)

        val composited = output.primitives.filterIsInstance<UiDrawPrimitive.Texture>().single { it.material is GraphicsLayerPlaceholder }
        assertEquals(45f, composited.rotationDegrees)
    }

    private companion object {
        const val VIEWPORT = 40
        const val INSET = 16
        const val PICTURE_WIDTH = 4
        const val PICTURE_HEIGHT = 2
        const val RED = 0xFF0000
        const val BLUE = 0x0000FF
        const val BACKGROUND = 0x000000

        /**
         * 4 x 2: a red pixel at the top-left corner, blue everywhere else. Asymmetric both in shape
         * (not square) and in pattern, so a rotation moves a feature instead of just a tint.
         */
        val ARROW = ImageBitmap(
            PICTURE_WIDTH,
            PICTURE_HEIGHT,
            byteArrayOf(
                -1, 0, 0, -1, 0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1,
                0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1, 0, 0, -1, -1,
            ),
        )
    }
}
