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
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneCanvasTest {

    internal val world = World()

    private fun element(configure: CanvasElement.() -> Unit): Entity =
        world.create().also { world.add(it, CanvasElement().apply(configure)) }

    private fun frame(
        host: ComposeHost = ComposeHost(),
        input: FrameInput = FrameInput(800, 600),
        touch: Boolean = false,
    ): FrameOutput = host.frame(input) { SceneCanvas(world, showTouchControls = touch) }

    private fun FrameOutput.node(entity: Entity): SemanticsNode? = semantics.find("canvas-element-${entity.id}")

    @Test
    fun anchorsPlaceElementsInwardFromEachScreenEdge() {
        val topLeft = element { anchor = CanvasAnchor.TopLeft; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val bottomRight = element { anchor = CanvasAnchor.BottomRight; offsetX = 16f; offsetY = 8f; width = 200f; height = 40f }
        val center = element { anchor = CanvasAnchor.Center; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }

        val out = frame()

        assertEquals(16 to 8, assertNotNull(out.node(topLeft)).let { it.x to it.y })
        assertEquals(800 - 16 - 200 to 600 - 8 - 40, assertNotNull(out.node(bottomRight)).let { it.x to it.y })
        assertEquals(350 to 275, assertNotNull(out.node(center)).let { it.x to it.y })
    }

    @Test
    fun hiddenElementsAreNotDrawn() {
        val hidden = element { kind = CanvasElementKind.Panel; visible = false }

        assertNull(frame().node(hidden))
    }

    @Test
    fun tappingAButtonIsReportedOnce() {
        lateinit var button: CanvasElement
        element { kind = CanvasElementKind.Button; text = "Jump"; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f; button = this }
        val host = ComposeHost()
        frame(host)
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true, pointerPressed = true))
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerReleased = true))

        assertTrue(button.consumePress())
        assertFalse(button.consumePress())
    }

    @Test
    fun aButtonIsHeldWhileThePointerIsDown() {
        lateinit var button: CanvasElement
        element { kind = CanvasElementKind.Button; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f; button = this }
        val host = ComposeHost()
        frame(host)

        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true, pointerPressed = true))
        val heldOnPress = button.isHeld
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerDown = true))
        val heldAfter = button.isHeld
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 20, pointerReleased = true))

        assertTrue(heldOnPress && heldAfter, "a button must stay held for as long as it is pressed")
        assertFalse(button.isHeld, "releasing must let go")
    }

    @Test
    fun draggingAJoystickDeflectsItUpToItsEdgeAndReleasingCentresIt() {
        lateinit var stick: CanvasElement
        element {
            kind = CanvasElementKind.Joystick; anchor = CanvasAnchor.TopLeft
            offsetX = 0f; offsetY = 0f; width = 100f; height = 100f
            stick = this
        }
        val host = ComposeHost()
        frame(host)

        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 50, pointerDown = true, pointerPressed = true))
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = 25, pointerDown = true))
        val halfUp = stick.stickY
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = -200, pointerDown = true))
        val pinned = stick.stickY
        frame(host, FrameInput(800, 600, pointerX = 50, pointerY = -200, pointerReleased = true))

        assertEquals(-0.5f, halfUp, 0.01f, "25 of a 50 radius upward is half deflection")
        assertEquals(-1f, pinned, 0.01f, "the knob stops at the pad's edge")
        assertEquals(0f, stick.stickY, "releasing centres the stick")
    }

    @Test
    fun touchOnlyElementsAppearOnlyWhereTouchControlsAreShown() {
        val pad = element { kind = CanvasElementKind.Joystick; touchOnly = true }

        assertNull(frame().node(pad))
        assertNotNull(frame(touch = true).node(pad))
    }

    @Test
    fun savedFormRoundTripsEveryField() {
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Bar,
            anchor = CanvasAnchor.BottomCenter,
            offsetX = 4f,
            offsetY = 24f,
            width = 300f,
            height = 12f,
            text = "HP",
            fontSize = 14f,
            color = "#E5484D",
            background = "#00000080",
            action = "jump",
            touchOnly = true,
            value = 0.4f,
            order = 3,
            visible = false,
            style = CanvasStyle(
                image = CanvasImage("ui/frame.png", regionX = 4, regionWidth = 48, sliceLeft = 16, sliceRight = 16, repeatEdges = true, tint = "#FFFFFF80"),
                fillImage = CanvasImage("ui/gauge.png", sliceLeft = 3, sliceRight = 3, repeatCenter = true, pixelated = true),
            ),
        )

        assertEquals(saved, saved.toComponent().toSceneComponent())
    }

    @Test
    fun validationRejectsAnImageWithNoPathBadSlicesOrABadTint() {
        val issues = SceneCanvasElement(
            style = CanvasStyle(
                image = CanvasImage("", sliceLeft = -1),
                fillImage = CanvasImage("gauge.png", regionWidth = 4, sliceLeft = 3, sliceRight = 3, tint = "blue"),
            ),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.style.image.path must name an image",
                "canvas_element.style.image.slices must not be negative",
                "canvas_element.style.fillImage.left and right slices must fit in regionWidth",
                "canvas_element.style.fillImage.tint \"blue\" must be #RRGGBB or #RRGGBBAA",
            ),
            issues,
        )
    }

    @Test
    fun anImageElementDrawsItsPicture() {
        element { kind = CanvasElementKind.Image; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; style = CanvasStyle(image = CanvasImage("quadrants.png")) }

        val pixels = pixels(40, 40, mapOf("quadrants.png" to QUADRANTS))

        assertEquals(listOf(RED, GREEN, BLUE, WHITE), listOf(pixels[10][10], pixels[10][30], pixels[30][10], pixels[30][30]))
    }

    @Test
    fun aBarsFillImageIsCutAtTheValueNotSqueezed() {
        // Red left cap, green middle, blue right cap: half full, the cap at the bar's end stays hidden.
        val gauge = ImageBitmap(4, 1, rgba(RED, GREEN, GREEN, BLUE))
        element {
            kind = CanvasElementKind.Bar; offsetX = 0f; offsetY = 0f; width = 100f; height = 10f; value = 0.5f
            style = CanvasStyle(fillImage = CanvasImage("gauge.png", sliceLeft = 1, sliceRight = 1))
        }

        val row = pixels(100, 10, mapOf("gauge.png" to gauge))[5]

        assertEquals(RED, row[0])
        assertEquals(List(49) { GREEN }, row.subList(1, 50))
        assertEquals(BACKGROUND, row[50], "the bar's empty half shows its track")
    }

    @Test
    fun anImageThatDidNotLoadIsLeftOut() {
        element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; background = "#FF0000"; style = CanvasStyle(image = CanvasImage("gone.png")) }
        element { kind = CanvasElementKind.Bar; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; order = 1; value = 0.5f; color = "#00FF00"; style = CanvasStyle(fillImage = CanvasImage("gone.png")) }

        val pixels = pixels(40, 40, emptyMap())

        assertEquals(GREEN, pixels[20][10], "the bar falls back to its colour")
        assertEquals(RED, pixels[20][30], "the panel to its background")
    }

    @Test
    fun aRegionTheImageIsTooSmallForIsLeftOut() {
        element { kind = CanvasElementKind.Image; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; style = CanvasStyle(image = CanvasImage("quadrants.png", regionX = 1, regionWidth = 2)) }

        assertEquals(BACKGROUND, pixels(40, 40, mapOf("quadrants.png" to QUADRANTS))[20][20])
    }

    @Test
    fun validationRejectsBadValuesAndColours() {
        val issues = SceneCanvasElement(value = 2f, color = "red", fontSize = 0f).validate("nodes[0]")

        assertEquals(3, issues.size)
        assertTrue(SceneCanvasElement().validate("nodes[0]").isEmpty())
    }
}

/** The canvas drawn at [width] x [height] with [images], as rows of 0xRRGGBB over [BACKGROUND]. */
private fun SceneCanvasTest.pixels(width: Int, height: Int, images: Map<String, ImageBitmap>): List<List<Int>> {
    val out = ComposeHost().frame(FrameInput(width, height)) { SceneCanvas(world, images = images) }
    val pixels = out.primitives.rasterize(width, height, Color.Black)
    return List(height) { y ->
        List(width) { x ->
            val offset = (y * width + x) * 4
            ((pixels[offset].toInt() and 0xFF) shl 16) or ((pixels[offset + 1].toInt() and 0xFF) shl 8) or (pixels[offset + 2].toInt() and 0xFF)
        }
    }
}

private const val RED = 0xFF0000
private const val GREEN = 0x00FF00
private const val BLUE = 0x0000FF
private const val WHITE = 0xFFFFFF
private const val BACKGROUND = 0x000000

/** 2 x 2: red, green over blue, white. */
private val QUADRANTS = ImageBitmap(2, 2, rgba(RED, GREEN, BLUE, WHITE))

private fun rgba(vararg pixels: Int): ByteArray =
    pixels.flatMap { listOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte(), -1) }.toByteArray()

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
