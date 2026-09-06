/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawStroke
import com.awakekt.awake.core.graphics2d.StrokeCap
import com.awakekt.awake.core.graphics2d.StrokeJoin
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.drawPath
import com.awakekt.awake.core.graphics2d.tessellateStrokeAa
import com.awakekt.awake.core.math2d.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterGeometryTest {

    @Test
    fun trianglePixelsAreClippedToTheRaster() {
        val pixels = buildList {
            rasterizeTrianglePixels(
                ax = -4f,
                ay = 2f,
                bx = 6f,
                by = 12f,
                cx = 12f,
                cy = -3f,
                clipX0 = 0f,
                clipY0 = 0f,
                clipX1 = 10f,
                clipY1 = 10f,
                width = 10,
                height = 10,
                pathClip = { _, _ -> true },
                drawPixel = { x, y, _, _, _, _ -> add(x to y) },
            )
        }

        assertTrue(pixels.isNotEmpty())
        assertTrue(pixels.all { (x, y) -> x in 0 until 10 && y in 0 until 10 })
    }

    @Test
    fun edgeFunctionAndInsideTestSupportEitherWinding() {
        val ccw = listOf(
            triangleEdge(0f, 0f, 10f, 0f, 2f, 2f),
            triangleEdge(10f, 0f, 0f, 10f, 2f, 2f),
            triangleEdge(0f, 10f, 0f, 0f, 2f, 2f),
        )
        val cw = ccw.map { -it }

        assertTrue(pointIsInsideTriangle(ccw[0], ccw[1], ccw[2]))
        assertTrue(pointIsInsideTriangle(cw[0], cw[1], cw[2]))
        assertFalse(pointIsInsideTriangle(1f, -1f, 1f))
    }

    @Test
    fun byteArrayRasterApiMatchesPixelMapApi() {
        val primitives = listOf(UiDrawPrimitive.Quad(1f, 1f, 2f, 2f, Color.White))

        val pixelMap = primitives.rasterizeToPixelMap(4, 4, Color.Transparent)

        assertTrue(primitives.rasterize(4, 4, Color.Transparent).contentEquals(pixelMap.pixels))
    }

    @Test
    fun rasterizedRoundCheckKeepsBothStrokeLegs() {
        val path = drawPath {
            moveTo(20f, 6f)
            lineTo(9f, 17f)
            lineTo(4f, 12f)
        }
        val mesh = path.tessellateStrokeAa(
            DrawStroke(width = 2f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round),
            Color.White,
        )
        assertTrue(
            mesh.vertices.all { it.position.x.isFinite() && it.position.y.isFinite() },
            "stroke AA mesh contains a non-finite vertex",
        )
        val pixels = listOf(
            UiDrawPrimitive.StrokedPath(
                path = path,
                stroke = DrawStroke(width = 2f.dp, cap = StrokeCap.Round, join = StrokeJoin.Round),
                color = Color.White,
            ),
        ).rasterize(24, 24, Color.Transparent)

        fun alphaAt(x: Int, y: Int): Int = pixels[(y * 24 + x) * 4 + 3].toInt() and 0xff

        val painted = (0 until 24).flatMap { y -> (0 until 24).map { x -> x to y } }
            .filter { (x, y) -> alphaAt(x, y) > 0 }
        assertTrue(
            painted.any { (x, y) -> x in 10..18 && y in 7..15 },
            "missing descending leg; painted bounds=${painted.minOfOrNull { it.first }}..${painted.maxOfOrNull { it.first }} x " +
                "${painted.minOfOrNull { it.second }}..${painted.maxOfOrNull { it.second }}, " +
                "mesh=${mesh.vertices.size}/${mesh.indices.size}",
        )
        assertTrue(painted.any { (x, y) -> x in 4..10 && y in 11..17 }, "missing ascending leg")
    }
}
