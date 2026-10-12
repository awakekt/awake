/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A View draws the picture its [CanvasViews] hands it, sized and placed as its box; without one, only its style. */
class CanvasViewTest {
    private val world = World()
    private val camera = world.create().also { world.add(it, Name("Portrait Camera")) }

    private fun element(parent: Entity? = null, configure: CanvasElement.() -> Unit): Entity = world.create().also {
        if (parent != null) world.add(it, Transform(parent = parent))
        world.add(it, CanvasElement().apply(configure))
    }

    private fun view(configure: CanvasElement.() -> Unit = {}) = element {
        kind = CanvasElementKind.View; camera = "Portrait Camera"; offsetX = 10f; offsetY = 20f; width = 100f; height = 50f
        background = "#FF0000"
        configure()
    }

    private val asked = ArrayList<List<Any>>()
    private val views = CanvasViews { element, node, width, height ->
        asked += listOf(element, node, width, height)
        PICTURE
    }

    private fun primitives(views: CanvasViews?, scale: Float = 1f): List<UiDrawPrimitive> =
        ComposeHost().frame(FrameInput(400, 300)) { SceneCanvas(world, scale = scale, views = views) }.primitives

    @Test
    fun withNoViewsAViewDrawsOnlyItsStyle() {
        view()

        val drawn = primitives(views = null)

        assertTrue(drawn.none { it is UiDrawPrimitive.Texture }, "no picture: $drawn")
        assertTrue(drawn.any { it is UiDrawPrimitive.Quad && it.color == Color.fromHex("#FF0000") }, "its background still draws")
    }

    @Test
    fun aViewFillsItsBoxWithItsCamerasPictureAtThatSizeInPixels() {
        val portrait = view()

        val picture = primitives(views, scale = 2f).filterIsInstance<UiDrawPrimitive.Texture>().single()

        assertEquals(PICTURE, picture.material)
        assertTrue(picture.premultiplied, "a render target's colours are premultiplied")
        assertEquals(listOf(20f, 40f, 200f, 100f), listOf(picture.x, picture.y, picture.w, picture.h))
        assertEquals(listOf(listOf(world.get<CanvasElement>(portrait)!!, camera, 200, 100)), asked)
    }

    @Test
    fun theViewsChildrenDrawOverItsPicture() {
        val portrait = view()
        element(parent = portrait) { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 20f; height = 20f; background = "#00FF00" }

        val drawn = primitives(views)

        val picture = drawn.indexOfFirst { it is UiDrawPrimitive.Texture }
        val child = drawn.indexOfFirst { it is UiDrawPrimitive.Quad && it.color == Color.fromHex("#00FF00") }
        assertTrue(picture in 0 until child, "the picture at $picture, the child over it at $child")
    }

    @Test
    fun roundedCornersClipThePicture() {
        view { style = CanvasStyle(cornerRadius = 8f) }

        val drawn = primitives(views)

        val clip = drawn.indexOfFirst { it is UiDrawPrimitive.ClipPathPush }
        assertTrue(clip in 0 until drawn.indexOfFirst { it is UiDrawPrimitive.Texture }, "clipped to its corners: $drawn")
    }

    @Test
    fun aViewNamingNoNodeAsksForNoPicture() {
        view { camera = "Nobody" }
        view { camera = "" }

        val drawn = primitives(views)

        assertTrue(asked.isEmpty())
        assertTrue(drawn.none { it is UiDrawPrimitive.Texture })
    }

    private companion object {
        val PICTURE = Any()
    }
}
