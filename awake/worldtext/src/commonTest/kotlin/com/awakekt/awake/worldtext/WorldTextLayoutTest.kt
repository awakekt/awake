/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.worldtext

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.text.font.UiFonts
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [layoutWorldText] and [WorldText]: glyph quads laid out once, in world units, lined up as asked. */
class WorldTextLayoutTest {
    private val style = WorldTextStyle(size = 1f, color = Color(1f, 0.5f, 0.25f, 1f))

    @Test
    fun aLineIsCentredOnTheOriginInWorldUnits() {
        val layout = layoutWorldText("HI", style)
        val (minX, maxX) = xRange(layout)

        assertEquals(1, layout.pages.size)
        assertEquals(VertexFormat.PositionUvColor, layout.pages.single().geometry.format)
        assertEquals(2 * 6, layout.pages.single().geometry.indices.size, "two glyphs, two triangles each")
        assertTrue(abs(minX + maxX) < 0.1f, "centred: $minX..$maxX")
        assertTrue(layout.width in 0.8f..2f, "two capitals at 1 world unit an em: ${layout.width}")
        assertEquals(UiFonts.default().lineHeightEm, layout.height, 1e-4f)
    }

    @Test
    fun spacesAdvanceWithoutQuadsAndLinesStack() {
        val layout = layoutWorldText("A B\nC", style)

        assertEquals(3 * 6, layout.pages.sumOf { it.geometry.indices.size })
        assertEquals(2 * UiFonts.default().lineHeightEm, layout.height, 1e-4f)
    }

    @Test
    fun startAndEndAlignLinesToTheBlocksEdges() {
        val start = xRange(layoutWorldText("WWWW\nI", style.copy(align = WorldTextAlign.Start)))
        val end = xRange(layoutWorldText("WWWW\nI", style.copy(align = WorldTextAlign.End)))

        assertEquals(start.first, end.first, 0.05f, "the long line spans the block either way")
        assertEquals(start.second, end.second, 0.05f)
    }

    @Test
    fun everyVertexCarriesTheStylesColour() {
        val geometry = layoutWorldText("A", style).pages.single().geometry
        val stride = geometry.format.strideFloats
        val color = geometry.format.floatOffsetOf(VertexSemantic.Color)
        for (vertex in 0 until geometry.vertices.size / stride) {
            assertEquals(listOf(1f, 0.5f, 0.25f, 1f), (0 until 4).map { geometry.vertices[vertex * stride + color + it] })
        }
    }

    @Test
    fun textIsLaidOutOnceUntilItChanges() {
        val text = WorldText("Harbor Town", style)
        val first = text.layout

        assertSame(first, text.layout)
        text.text = "Harbor Town"
        assertSame(first, text.layout, "the same text again is no change")
        assertEquals(1, text.revision)
        text.text = "Market"
        text.layout
        assertEquals(2, text.revision)
    }

    @Test
    fun aCoverageFontIsRefused() {
        assertFailsWith<IllegalArgumentException> { WorldTextStyle(font = UiFonts.bitmap()) }
    }

    @Test
    fun aBillboardFacesTheCameraAndKeepsItsPlaceAndScale() {
        val placement = Mat4().setTrs(Vec3f(3f, 2f, 1f), Quat(), Vec3f(2f, 2f, 2f))
        val camera = Lens(eye = Vec3f(3f, 2f, 11f), center = Vec3f(3f, 2f, 1f), fovYRadians = 1f, near = 0.1f, far = 100f)
        val facing = Mat4().setFacing(placement, camera)

        assertEquals(listOf(3f, 2f, 1f), listOf(facing.m03, facing.m13, facing.m23))
        assertEquals(listOf(2f, 0f, 0f), listOf(facing.m00, facing.m10, facing.m20).map { it + 0f })
        assertEquals(listOf(0f, 0f, 2f), listOf(facing.m02, facing.m12, facing.m22).map { it + 0f }, "+Z back toward the camera")
    }

    private fun xRange(layout: WorldTextLayout): Pair<Float, Float> {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (page in layout.pages) {
            val stride = page.geometry.format.strideFloats
            for (i in page.geometry.vertices.indices step stride) {
                min = minOf(min, page.geometry.vertices[i])
                max = maxOf(max, page.geometry.vertices[i])
            }
        }
        return min to max
    }
}
