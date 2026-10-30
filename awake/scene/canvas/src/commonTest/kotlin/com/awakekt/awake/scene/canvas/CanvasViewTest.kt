/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.graphics.RoundedCornerShape
import com.awakekt.awake.compose.ui.graphics.ShapeOutline
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawShape
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.toPath
import com.awakekt.awake.core.graphics2d.transform
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Size2D
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasInsets
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasView
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

    private fun primitives(views: CanvasViews?, scale: Float = 1f, safeArea: CanvasInsets = CanvasInsets.None): List<UiDrawPrimitive> =
        ComposeHost().frame(FrameInput(400, 300)) { SceneCanvas(world, scale = scale, safeArea = safeArea, views = views) }.primitives

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
    fun anOpaqueRoundedPictureClipsAsAShapeClipDidAtFractionalScales() {
        view { style = CanvasStyle(cornerRadius = 8f) }

        for (scale in listOf(1f, 1.37f, 2.5f)) {
            val drawn = primitives(views, scale = scale)
            val picture = drawn.filterIsInstance<UiDrawPrimitive.Texture>().single()
            val clip = drawn.take(drawn.indexOf(picture)).filterIsInstance<UiDrawPrimitive.ClipPathPush>().last()
            // What `Modifier.clip(RoundedCornerShape(radius.dp))` emitted for the same box: the
            // outline resolved at the canvas density, as a path placed at the box's origin.
            val outline = RoundedCornerShape(8.dp).createOutline(Size2D(picture.w, picture.h), scale) as ShapeOutline.Rounded
            val r = outline.radius
            val b = outline.bounds
            val path = DrawShape.RoundedRectangle(r.dp).toPath(b).transform(1f, 1f, picture.x, picture.y)
            assertEquals(path, clip.path, "corner path at scale $scale")
            assertEquals(Rectangle(picture.x + r, picture.y + r, b.width - 2 * r, b.height - 2 * r), clip.safeInteriorRect, "unclipped interior at scale $scale")
        }
    }

    @Test
    fun transparencyRemovesOnlyThePicturesBlackBacking() {
        val entity = view()
        val opaque = primitives(views)
        world.get<CanvasElement>(entity)!!.view = CanvasView(transparent = true)
        val transparent = primitives(views)

        assertEquals(1, opaque.filterIsInstance<UiDrawPrimitive.Quad>().count { it.color == Color.Black })
        assertTrue(transparent.filterIsInstance<UiDrawPrimitive.Quad>().none { it.color == Color.Black })
        assertTrue(transparent.any { it is UiDrawPrimitive.Quad && it.color == Color.fromHex("#FF0000") }, "the authored background remains")
        assertEquals(opaque.filterIsInstance<UiDrawPrimitive.Texture>(), transparent.filterIsInstance<UiDrawPrimitive.Texture>())
    }

    @Test
    fun aCircularPictureKeepsItsPixelSizeAndSafeAreaPlacementAtDoubleScale() {
        view { view = CanvasView(transparent = true, mask = CanvasMask.Circle) }
        val drawn = primitives(views, scale = 2f, safeArea = CanvasInsets(left = 3f, top = 5f))
        val picture = drawn.filterIsInstance<UiDrawPrimitive.Texture>().single()
        // Insets are host dp, independent of the canvas's two-pixels-per-unit scale.
        assertEquals(listOf(23f, 45f, 200f, 100f), listOf(picture.x, picture.y, picture.w, picture.h))
        assertTrue(drawn.takeWhile { it !is UiDrawPrimitive.Texture }.any { it is UiDrawPrimitive.ClipPathPush })
        assertEquals(200, asked.single()[2])
        assertEquals(100, asked.single()[3])
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
