/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.capture.PixelMap
import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A name plate, a text follower scaled by distance, is as crisp as the same text drawn directly at that
 * size, as a plain element on a canvas drawn at that scale,
 * and holds still while the camera drifts (#1062), by the rules in [FollowerSizing].
 *
 * Tolerances, in the CPU raster's pixels:
 * - **Position:** the ink's centroid within [MAX_CENTROID_PX] of the directly drawn text's.
 * - **Blur:** no more fringe pixels (neither empty nor full) than the direct text has, plus
 *   [FRINGE_ALLOWANCE] of them.
 * - **Shimmer:** while the camera drifts slowly, every pixel inside a glyph's still interior changes
 *   by at most 1/255 from frame to frame, once the frame's whole-pixel move is taken out; and so does
 *   every pixel of its fringe, since the plate moves by whole pixels. Without the snap the fringe
 *   changes by far more, while the interiors alone would not show it.
 */
class NamePlateCrispnessTest {
    /** The scale a plate is drawn at by distance: a tenth of its distance. */
    private val byDistance = CanvasFollowRules(scale = CanvasCurve(listOf(CanvasCurvePoint(0f, 0f), CanvasCurvePoint(100f, 10f))))

    private fun plate(rules: CanvasFollowRules? = byDistance) = CanvasElement().apply {
        kind = CanvasElementKind.Text
        text = "Mira 42"
        color = "#FFFFFF"
        fontSize = FONT_SIZE
        width = WIDTH_DP
        height = HEIGHT_DP
        anchor = CanvasAnchor.BottomCenter
        offsetX = 0f
        offsetY = 0f
        follow = "Hero"
        followRules = rules
    }

    /** The plate's text as a plain element, not following anything, at ([x], [y]) dp of a canvas drawn at its scale. */
    private fun direct(x: Float, y: Float) = CanvasElement().apply {
        kind = CanvasElementKind.Text
        text = "Mira 42"
        color = "#FFFFFF"
        fontSize = FONT_SIZE
        width = WIDTH_DP
        height = HEIGHT_DP
        anchor = CanvasAnchor.TopLeft
        offsetX = x
        offsetY = y
    }

    private class At(var projection: CanvasProjection) : CanvasResolver {
        override fun project(target: String, offset: Vec3f): CanvasProjection = projection
    }

    private fun FrameOutput.pixels(): PixelMap = primitives.rasterizeToPixelMap(FRAME_W, FRAME_H, background = Color.Black)

    @Test
    fun aPlateAtHalfToTwiceItsSizeIsAsCrispAsTextDrawnAtThatSize() {
        for (wanted in listOf(0.5f, 0.71f, 1f, 1.37f, 2f)) {
            val element = plate()
            val out = ComposeHost().frame(FrameInput(FRAME_W, FRAME_H)) {
                CanvasOverlay(listOf(CanvasItem(1, element)), resolver = At(CanvasProjection(POINT_X, POINT_Y, distance = wanted * 10f)))
            }
            val box = assertNotNull(out.semantics.findTag("canvas-element-1"), "plate at $wanted drawn")
            val drawn = FollowerSizing.scaleOf(FollowerSizing.bucketOf(wanted, FollowerSizing.NO_BUCKET))
            assertTrue(abs(drawn / wanted - 1f) <= 0.022f, "plate wanted at $wanted is drawn at $drawn, within half a bucket")
            assertTrue(abs(box.width - WIDTH_DP * drawn) <= 1f, "plate box ${box.width} wide at $drawn")
            // Where the plate's box lands: standing on its point, its top left on whole pixels.
            val left = round(POINT_X - WIDTH_DP * drawn / 2f)
            val top = round(POINT_Y - HEIGHT_DP * drawn)
            val reference = ComposeHost().frame(FrameInput(FRAME_W, FRAME_H)) {
                CanvasOverlay(listOf(CanvasItem(1, direct(left / drawn, top / drawn))), scale = drawn)
            }
            val plate = Ink(out.pixels())
            val text = Ink(reference.pixels())

            println(
                "NamePlate $wanted drawn at $drawn: centroid plate (${plate.centroidX}, ${plate.centroidY}) text (${text.centroidX}, ${text.centroidY}), " +
                    "fringe plate ${plate.fringe} text ${text.fringe}, ink plate ${plate.total} text ${text.total}",
            )
            assertTrue(text.total > 0f && plate.total > 0f, "both draw ink at $wanted")
            assertTrue(abs(plate.centroidX - text.centroidX) <= MAX_CENTROID_PX, "x at $wanted: plate ${plate.centroidX}, text ${text.centroidX}")
            assertTrue(abs(plate.centroidY - text.centroidY) <= MAX_CENTROID_PX, "y at $wanted: plate ${plate.centroidY}, text ${text.centroidY}")
            assertTrue(plate.fringe <= text.fringe + FRINGE_ALLOWANCE, "blur at $wanted: plate ${plate.fringe} fringe pixels, text ${text.fringe}")
        }
    }

    @Test
    fun aSlowlyDriftingCameraDoesNotMakeAPlateShimmer() {
        val element = plate()
        val items = listOf(CanvasItem(1, element))
        val at = At(CanvasProjection(150f, 140f, distance = 10f))
        val host = ComposeHost()
        var first: PixelMap? = null
        var firstX = 0
        var firstY = 0
        for (frame in 0 until DRIFT_FRAMES) {
            // A tenth of a pixel a frame across, a twentieth down, and nearer by a little: within one size bucket.
            at.projection = CanvasProjection(150f + frame * 0.1f, 140f + frame * 0.05f, distance = 10f + frame * 0.01f)
            val out = host.frame(FrameInput(FRAME_W, FRAME_H)) { CanvasOverlay(items, resolver = at) }
            val box = assertNotNull(out.semantics.findTag("canvas-element-1"))
            val pixels = out.pixels()
            val reference = first
            if (reference == null) {
                first = pixels
                firstX = box.x
                firstY = box.y
            } else {
                val interior = change(reference, pixels, box.x - firstX, box.y - firstY, interiorOnly = true)
                val edges = change(reference, pixels, box.x - firstX, box.y - firstY, interiorOnly = false)
                assertTrue(interior <= 1, "frame $frame: a still glyph interior changed by $interior/255")
                assertTrue(edges <= 1, "frame $frame: the plate's fringe changed by $edges/255: it did not move by whole pixels")
            }
        }
    }

    @Test
    fun aCameraBobbingAtASizeEdgeDoesNotMakeAPlateFlicker() {
        // At 1.0357 times, 14 sp text is 14.5 px: a plate laid out at its exact scale would flick
        // between 14 and 15 px glyphs as the camera bobs a few centimetres in and out.
        val items = listOf(CanvasItem(1, plate()))
        val at = At(CanvasProjection(150f, 140f, distance = BOB_DISTANCE))
        val host = ComposeHost()
        var previous: PixelMap? = null
        for (frame in 0 until DRIFT_FRAMES) {
            val bob = if (frame % 2 == 0) BOB else -BOB
            at.projection = CanvasProjection(150f, 140f, distance = BOB_DISTANCE + bob)
            val pixels = host.frame(FrameInput(FRAME_W, FRAME_H)) { CanvasOverlay(items, resolver = at) }.pixels()
            previous?.let {
                val worst = change(it, pixels, 0, 0, interiorOnly = false)
                assertTrue(worst <= 1, "frame $frame: the plate changed by $worst/255 as the camera bobbed")
            }
            previous = pixels
        }
    }

    /**
     * The largest change, in 1/255, of a pixel [after] moved back by ([dx], [dy]): of one fully inside
     * [before]'s glyphs when [interiorOnly], or of any pixel either frame inks.
     */
    private fun change(before: PixelMap, after: PixelMap, dx: Int, dy: Int, interiorOnly: Boolean): Int {
        var worst = 0
        for (y in 1 until FRAME_H - 1) {
            for (x in 1 until FRAME_W - 1) {
                if (after.contains(x + dx, y + dy) && inked(before, after, x, y, dx, dy, interiorOnly)) {
                    worst = maxOf(worst, abs(before.red(x, y) - after.red(x + dx, y + dy)))
                }
            }
        }
        return worst
    }

    @Suppress("LongParameterList") // Two frames, a pixel, the move between them and which pixels count.
    private fun inked(before: PixelMap, after: PixelMap, x: Int, y: Int, dx: Int, dy: Int, interiorOnly: Boolean): Boolean =
        if (interiorOnly) before.red(x, y) >= FULL else before.red(x, y) > 0 || after.red(x + dx, y + dy) > 0

    private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
        firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }

    /** The red channel of white text on black: how much of the pixel the ink covers, 0 to 255. */
    private fun PixelMap.red(x: Int, y: Int): Int = pixels[offsetOf(x, y)].toInt() and 0xFF

    /** Where a frame's ink is and how much of it is fringe. */
    private inner class Ink(map: PixelMap) {
        var total = 0f
        var centroidX = 0f
        var centroidY = 0f
        var fringe = 0

        init {
            var sumX = 0f
            var sumY = 0f
            for (y in 0 until FRAME_H) {
                for (x in 0 until FRAME_W) {
                    val value = map.red(x, y) / 255f
                    if (value <= 0f) continue
                    total += value
                    sumX += value * x
                    sumY += value * y
                    if (value > FRINGE_LOW && value < FRINGE_HIGH) fringe++
                }
            }
            if (total > 0f) {
                centroidX = sumX / total
                centroidY = sumY / total
            }
        }
    }

    private companion object {
        const val POINT_X = 150.3f
        const val POINT_Y = 140.6f
        const val FRAME_W = 300
        const val FRAME_H = 200
        const val FONT_SIZE = 14f
        const val WIDTH_DP = 80f
        const val HEIGHT_DP = 20f
        const val DRIFT_FRAMES = 20
        const val FULL = 254
        const val BOB_DISTANCE = 10.357f
        const val BOB = 0.03f
        const val MAX_CENTROID_PX = 0.35f
        const val FRINGE_ALLOWANCE = 4
        const val FRINGE_LOW = 0.1f
        const val FRINGE_HIGH = 0.9f
    }
}
