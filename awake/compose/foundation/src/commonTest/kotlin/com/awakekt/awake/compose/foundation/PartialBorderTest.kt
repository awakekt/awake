/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.RectangleShape
import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math2d.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartialBorderTest {

    private val red = Color(1f, 0f, 0f, 1f)
    private val blue = Color(0f, 0f, 1f, 1f)
    private val green = Color(0f, 1f, 0f, 1f)

    @Test
    fun fullBorderEmitsOneMesh() {
        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(40.dp, 40.dp).border(2.dp, red, RectangleShape, BorderSides.All))
        }

        val output = host.frame(FrameInput(viewportWidth = 40, viewportHeight = 40), content)
        val mesh = output.primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh()
        assertEquals(listOf(red), mesh.vertices.map { it.color }.filter { it.a > 0f }.distinct())
        // A 2px stroke on a 40x40 box: centreline inset by half its width to (1, 1, 38, 38), then
        // grown by that same half width to the ring's outer edge and again by the anti-aliased
        // fringe, which is min(AA_FRINGE_PX, stroke / 2) = 1. Asserting the box is asserting the
        // stroke width, which a tessellated mesh no longer carries as a field.
        assertEquals(Rectangle(-1f, -1f, 42f, 42f), mesh.bounds())
    }

    @Test
    fun partialBorderTopAndBottomEmitsPath() {
        val host = ComposeHost(density = 1f)
        val sides = BorderSides(top = true, bottom = true, start = false, end = false)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(40.dp, 40.dp).border(1.dp, blue, RectangleShape, sides))
        }

        val output = host.frame(FrameInput(viewportWidth = 40, viewportHeight = 40), content)
        val mesh = output.primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh()
        assertEquals(listOf(blue), mesh.vertices.map { it.color }.filter { it.a > 0f }.distinct())
        assertTrue(mesh.vertices.isNotEmpty(), "the top and bottom edges drew nothing")
    }

    @Test
    fun partialBorderRoundedCornerEmitsPath() {
        val host = ComposeHost(density = 1f)
        val sides = BorderSides(top = true, end = true, bottom = false, start = false)
        val shape = RoundedCornerShape(4.dp)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(50.dp, 50.dp).border(2.dp, green, shape, sides))
        }

        val output = host.frame(FrameInput(viewportWidth = 50, viewportHeight = 50), content)
        val mesh = output.primitives.filterIsInstance<UiDrawPrimitive.Mesh>().single().placedMesh()
        assertEquals(listOf(green), mesh.vertices.map { it.color }.filter { it.a > 0f }.distinct())
        assertTrue(mesh.vertices.isNotEmpty(), "the joined top and end edges drew nothing")
    }

    @Test
    fun noneBorderSidesEmitsNoPath() {
        val host = ComposeHost(density = 1f)
        val content: context(Composer)
        () -> Unit = {
            Box(Modifier.size(40.dp, 40.dp).border(2.dp, red, RectangleShape, BorderSides.None))
        }

        val output = host.frame(FrameInput(viewportWidth = 40, viewportHeight = 40), content)
        assertTrue(output.primitives.filterIsInstance<UiDrawPrimitive.Mesh>().isEmpty())
    }
}
