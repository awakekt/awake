/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [CanvasOverlay] draws a flat [CanvasItem] list with no `World`: nesting by [CanvasItem.parent], a
 * [CanvasElement.follow]ed point or box, a View's picture and a [CanvasElement.bind]'s field, each
 * through a host's own [CanvasResolver].
 */
class CanvasOverlayTest {

    private fun frame(
        items: List<CanvasItem>,
        resolver: CanvasResolver = CanvasResolver.None,
        width: Int = 400,
        height: Int = 300,
    ): FrameOutput = ComposeHost().frame(FrameInput(width, height)) { CanvasOverlay(items, resolver = resolver) }

    private fun FrameOutput.box(id: Int): List<Int> =
        assertNotNull(semantics.find("canvas-element-$id")).let { listOf(it.x, it.y, it.width, it.height) }

    @Test
    fun aChildWhoseParentIsInTheListDrawsInsideItsParentsBox() {
        val parent = CanvasElement().apply { kind = CanvasElementKind.Panel; offsetX = 10f; offsetY = 10f; width = 100f; height = 80f }
        val child = CanvasElement().apply { offsetX = 5f; offsetY = 5f; width = 20f; height = 20f }
        val items = listOf(CanvasItem(1, parent), CanvasItem(2, child, parent))

        val out = frame(items)

        assertEquals(listOf(10, 10, 100, 80), out.box(1))
        assertEquals(listOf(15, 15, 20, 20), out.box(2), "anchored inside its parent's box, not the canvas's")
    }

    @Test
    fun aFollowerIsPlacedByTheResolverRegardlessOfItsParent() {
        val parent = CanvasElement().apply { kind = CanvasElementKind.Panel; offsetX = 10f; offsetY = 10f; width = 100f; height = 80f }
        val follower = CanvasElement().apply { follow = "Hero"; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }
        val items = listOf(CanvasItem(1, parent), CanvasItem(2, follower, parent))
        val resolver = object : CanvasResolver {
            override fun point(target: String, offset: Vec3f): Vec2? = if (target == "Hero") Vec2(200f, 150f) else null
        }

        val out = frame(items, resolver)

        assertEquals(listOf(200, 150, 10, 10), out.box(2), "on the resolver's point, not inside its parent's box")
    }

    @Test
    fun aFollowerWithBoundsFillsTheResolversBox() {
        val follower = CanvasElement().apply { follow = "Hero"; followBounds = true }
        val items = listOf(CanvasItem(1, follower))
        val resolver = object : CanvasResolver {
            override fun bounds(target: String): Rectangle? = if (target == "Hero") Rectangle(50f, 60f, 40f, 30f) else null
        }

        val out = frame(items, resolver)

        assertEquals(listOf(50, 60, 40, 30), out.box(1))
    }

    @Test
    fun aViewDrawsTheResolversPictureAtItsBoxSize() {
        val view = CanvasElement().apply {
            kind = CanvasElementKind.View; camera = "Cam"; offsetX = 0f; offsetY = 0f; width = 40f; height = 30f
        }
        val items = listOf(CanvasItem(1, view))
        var asked: List<Any>? = null
        val resolver = object : CanvasResolver {
            override fun texture(element: CanvasElement, camera: String, width: Int, height: Int): Any {
                asked = listOf(camera, width, height)
                return PICTURE
            }
        }

        val drawn = frame(items, resolver).primitives

        assertTrue(drawn.any { it is UiDrawPrimitive.Texture && it.material == PICTURE })
        assertEquals(listOf("Cam", 40, 30), asked)
    }

    @Test
    fun aBoundBarShowsTheResolversFieldAsItsShare() {
        val bar = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            offsetX = 0f; offsetY = 0f; width = 100f; height = 10f
            color = "#FF0000"
            value = 1f
            bind = CanvasBinding("Hero", value = "health.current", max = "health.max")
        }
        val items = listOf(CanvasItem(1, bar))
        val resolver = object : CanvasResolver {
            override fun field(node: String, path: String): String? = when {
                node != "Hero" -> null
                path == "health.current" -> "45"
                path == "health.max" -> "150"
                else -> null
            }
        }

        val pixels = frame(items, resolver, width = BAR_WIDTH, height = BAR_HEIGHT).primitives.rasterize(BAR_WIDTH, BAR_HEIGHT, Color.Black)
        val red = (0 until BAR_WIDTH).count { x -> (pixels[(5 * BAR_WIDTH + x) * 4].toInt() and 0xFF) > 200 }

        assertEquals(30, red, "45 of 150 is 30% of the bar")
    }

    private companion object {
        const val BAR_WIDTH = 100
        const val BAR_HEIGHT = 10
        val PICTURE = Any()
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
