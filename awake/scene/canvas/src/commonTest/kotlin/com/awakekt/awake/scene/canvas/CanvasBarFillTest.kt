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
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import kotlinx.serialization.json.Json
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Bar's fill image is cut at its value by default, and with [CanvasFillImageMode.Squeeze] drawn at
 * the filled width, so both of its end caps stay and only the middle stretches.
 */
class CanvasBarFillTest {

    /** A world with one 100 x 10 Bar filled to [value], its fill [image] and the [mode] it meets the value in; the default mode when null. */
    private fun barWorld(
        value: Float,
        mode: CanvasFillImageMode? = null,
        image: CanvasImage? = CanvasImage("gauge.png", sliceLeft = 1, sliceRight = 1),
    ): World = World().also { world ->
        val style = if (mode == null) CanvasStyle(fillImage = image) else CanvasStyle(fillImage = image, fillImageMode = mode)
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.Bar
                offsetX = 0f
                offsetY = 0f
                width = 100f
                height = 10f
                this.value = value
                color = "#00FF00"
                this.style = style
            },
        )
    }

    private fun primitives(world: World, images: Map<String, ImageBitmap> = mapOf("gauge.png" to GAUGE), scale: Float = 1f): List<UiDrawPrimitive> =
        ComposeHost().frame(FrameInput((100 * scale).toInt(), (10 * scale).toInt())) { SceneCanvas(world, images = images, scale = scale) }.primitives

    /** The Bar's middle row as 0xRRGGBB, over [BACKGROUND]. */
    private fun row(world: World, images: Map<String, ImageBitmap> = mapOf("gauge.png" to GAUGE)): List<Int> {
        val pixels = primitives(world, images).rasterize(100, 10, Color.Black)
        return List(100) { x ->
            val offset = (5 * 100 + x) * 4
            ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
        }
    }

    @Test
    fun squeezedAFillKeepsBothCapsAtTheValue() {
        // Red left cap, green middle, blue right cap, drawn at the 50 of 100 that are filled.
        val row = row(barWorld(0.5f, CanvasFillImageMode.Squeeze))

        assertEquals(RED, row[0])
        assertEquals(List(48) { GREEN }, row.subList(1, 49), "the middle stretches")
        assertEquals(BLUE, row[49], "the right cap ends the fill")
        assertEquals(BACKGROUND, row[50], "the empty half shows the track")
    }

    @Test
    fun cutIsTheDefaultAndHidesTheRightCapUntilTheBarIsFull() {
        val byDefault = row(barWorld(0.5f))
        val cut = row(barWorld(0.5f, CanvasFillImageMode.Cut))

        assertEquals(byDefault, cut, "leaving the mode out is cutting")
        assertEquals(RED, cut[0])
        assertEquals(List(49) { GREEN }, cut.subList(1, 50), "the pattern is cut where the value ends")
        assertFalse(BLUE in cut, "the right cap is past the value")
        assertEquals(BLUE, row(barWorld(1f))[99], "and shows at 100%")
    }

    @Test
    fun aFullBarLooksTheSameEitherWay() {
        val squeezed = row(barWorld(1f, CanvasFillImageMode.Squeeze))
        val cut = row(barWorld(1f, CanvasFillImageMode.Cut))

        assertEquals(listOf(RED) + List(98) { GREEN } + BLUE, squeezed)
        assertEquals(cut, squeezed)
    }

    @Test
    fun belowTheCapsCombinedWidthTheyShrinkTogetherInsteadOfOverlapping() {
        // Caps of 3 pixels each make 6, and the fill is 4 wide: each cap shrinks to 2 and none of the middle shows.
        val image = CanvasImage("gauge.png", sliceLeft = 3, sliceRight = 3)

        val row = row(barWorld(0.04f, CanvasFillImageMode.Squeeze, image), mapOf("gauge.png" to WIDE_CAPS))

        assertEquals(listOf(RED, RED, BLUE, BLUE), row.subList(0, 4))
        assertEquals(BACKGROUND, row[4])
    }

    @Test
    fun theShrunkCapsTileTheFilledWidthWithoutGapOrOverlap() {
        val image = CanvasImage("gauge.png", sliceLeft = 3, sliceRight = 3)
        for (filled in 1..8) {
            val world = barWorld(filled / 100f, CanvasFillImageMode.Squeeze, image)

            val pieces = primitives(world, mapOf("gauge.png" to WIDE_CAPS)).filterIsInstance<UiDrawPrimitive.Texture>().filter { it.w > 0f }.sortedBy { it.x }

            assertEquals(0f, pieces.first().x, "$filled wide: starts at the bar's edge")
            assertEquals(filled.toFloat(), pieces.last().x + pieces.last().w, "$filled wide: ends at the value")
            pieces.zipWithNext { a, b -> assertEquals(b.x, a.x + a.w, "$filled wide: no gap or overlap between the pieces") }
        }
    }

    @Test
    fun anEmptyBarDrawsNoFill() {
        val world = barWorld(0f, CanvasFillImageMode.Squeeze)

        assertTrue(primitives(world).filterIsInstance<UiDrawPrimitive.Texture>().isEmpty())
        assertEquals(List(100) { BACKGROUND }, row(world))
    }

    @Test
    fun aSqueezedFillAtAFractionalScaleLandsOnWholePixelsAndEndsWhereAColourFillDoes() {
        val squeezed = primitives(barWorld(0.37f, CanvasFillImageMode.Squeeze), scale = 1.5f)
        val colour = primitives(barWorld(0.37f, image = null), scale = 1.5f).filterIsInstance<UiDrawPrimitive.Quad>().single { it.color == Color.fromHex("#00FF00") }

        val pieces = squeezed.filterIsInstance<UiDrawPrimitive.Texture>()
        assertTrue(pieces.isNotEmpty())
        val offGrid = pieces.filter { piece -> listOf(piece.x, piece.y, piece.w, piece.h).any { it != round(it) } }
        assertEquals(emptyList(), offGrid)
        assertEquals(colour.x + colour.w, pieces.maxOf { it.x + it.w }, "the fill ends at the value, in pixels")
    }

    @Test
    fun theModeLeavesABarWithoutAnImageAlone() {
        for (mode in CanvasFillImageMode.entries) {
            val pixels = primitives(barWorld(0.5f, mode, image = null)).rasterize(100, 10, Color.Black)
            val green = (0 until 100).count { x -> (pixels[(5 * 100 + x) * 4 + 1].toInt() and 0xFF) > 200 }

            assertEquals(50, green, "$mode: the colour fill is half the bar")
        }
    }

    @Test
    fun theModeIsSavedAndOnlySqueezeIsWritten() {
        val squeezed = CanvasStyle(fillImage = CanvasImage("gauge.png"), fillImageMode = CanvasFillImageMode.Squeeze)
        val cut = CanvasStyle(fillImage = CanvasImage("gauge.png"))

        assertEquals(squeezed, SceneCanvasElement(style = squeezed).toComponent().toSceneComponent().style)
        assertTrue("\"fillImageMode\":\"Squeeze\"" in Json.encodeToString(CanvasStyle.serializer(), squeezed))
        assertFalse("fillImageMode" in Json.encodeToString(CanvasStyle.serializer(), cut), "Cut is the default, so it is not written")
        assertEquals(CanvasFillImageMode.Squeeze, Json.decodeFromString(CanvasStyle.serializer(), """{"fillImageMode":"Squeeze"}""").fillImageMode)
        assertEquals(CanvasFillImageMode.Cut, Json.decodeFromString(CanvasStyle.serializer(), """{"fillImage":{"path":"gauge.png"}}""").fillImageMode)
    }

    private companion object {
        const val RED = 0xFF0000
        const val GREEN = 0x00FF00
        const val BLUE = 0x0000FF
        const val BACKGROUND = 0x000000

        /** 4 x 1: a red left cap, two green pixels, a blue right cap. */
        val GAUGE = ImageBitmap(4, 1, rgba(RED, GREEN, GREEN, BLUE))

        /** 7 x 1: red and blue caps of 3 pixels with one green pixel between. */
        val WIDE_CAPS = ImageBitmap(7, 1, rgba(RED, RED, RED, GREEN, BLUE, BLUE, BLUE))

        fun rgba(vararg pixels: Int): ByteArray = pixels.flatMap { listOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte(), -1) }.toByteArray()
    }
}
