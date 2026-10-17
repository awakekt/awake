/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.Canvas
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawPath
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Stroked closed circles and a closed arc, drawn as rings: an outline with nothing inside.
 *
 * A full arc closed with `close()` used to fill as a disc, because the closing segment is a
 * rounding error long. The baseline pins the rings, and the probe says in words what the baseline
 * shows: the middle of every shape is the background.
 */
class ClosedPathStrokeBaselineTest {

    @Test
    fun strokedClosedCirclesAndArcsDrawAsOutlines() {
        val frame = composeFrame(WIDTH, HEIGHT) {
            Canvas(Modifier.size(WIDTH.dp, HEIGHT.dp)) {
                shapes.forEachIndexed { index, (path, width) ->
                    drawStrokedPath(path, DrawStroke(width.dp, join = if (index % 2 == 0) StrokeJoin.Miter else StrokeJoin.Round), Ink)
                }
            }
        }

        val pixels = frame.primitives.rasterize(WIDTH, HEIGHT, background = Paper)
        for (index in shapes.indices) {
            val x = centreX(index).toInt()
            val y = CENTRE_Y.toInt()
            val red = pixels[(y * WIDTH + x) * 4].toInt() and 0xFF
            assertEquals(0xFF, red, "the middle of shape $index is filled")
        }
        frame.assertMatchesBaseline("closed-path-stroke", WIDTH, HEIGHT, background = Paper)
    }

    private val shapes: List<Pair<DrawPath, Float>> = listOf(
        fullArc(0) to 2f,
        fullArc(1) to 6f,
        DrawShape.Circle.toPath(Rectangle(centreX(2) - RADIUS, CENTRE_Y - RADIUS, RADIUS * 2f, RADIUS * 2f)) to 3f,
        // Three quarters of a circle, closed back to its start: the arc and its chord.
        drawPath {
            val cx = centreX(3)
            moveTo(cx + RADIUS, CENTRE_Y)
            arcTo(cx - RADIUS, CENTRE_Y - RADIUS, cx + RADIUS, CENTRE_Y + RADIUS, 0f, 270f)
            close()
        } to 4f,
        // An ellipse: a full arc in a wide box.
        drawPath {
            val cx = centreX(4)
            moveTo(cx + RADIUS, CENTRE_Y)
            arcTo(cx - RADIUS, CENTRE_Y - RADIUS / 2f, cx + RADIUS, CENTRE_Y + RADIUS / 2f, 0f, 360f)
            close()
        } to 2f,
    )

    private fun fullArc(index: Int): DrawPath = drawPath {
        val cx = centreX(index)
        moveTo(cx + RADIUS, CENTRE_Y)
        arcTo(cx - RADIUS, CENTRE_Y - RADIUS, cx + RADIUS, CENTRE_Y + RADIUS, 0f, 360f)
        close()
    }

    private fun centreX(index: Int): Float = SPACING / 2f + SPACING * index

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 64
        const val SPACING = 64f
        const val RADIUS = 24f
        const val CENTRE_Y = 32f
        val Ink = Color(0.1f, 0.1f, 0.12f, 1f)
        val Paper = Color(1f, 1f, 1f, 1f)
    }
}
