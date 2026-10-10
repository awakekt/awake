/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.LocalTextStyle
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.text.theme.TextOutline
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toSceneComponent
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneVec3
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

    /** An element on a new entity below [parent] in the scene hierarchy. */
    private fun child(parent: Entity, configure: CanvasElement.() -> Unit): Entity =
        world.create().also {
            world.add(it, Transform(parent = parent))
            world.add(it, CanvasElement().apply(configure))
        }

    private fun frame(
        host: ComposeHost = ComposeHost(),
        input: FrameInput = FrameInput(800, 600),
        touch: Boolean = false,
        scale: Float = 1f,
    ): FrameOutput = host.frame(input) { SceneCanvas(world, showTouchControls = touch, scale = scale) }

    private fun FrameOutput.box(entity: Entity): List<Int> = assertNotNull(node(entity)).let { listOf(it.x, it.y, it.width, it.height) }

    @Test
    fun childrenAreAnchoredInsideTheirParentAndMoveWithIt() {
        lateinit var window: CanvasElement
        val parent = element { kind = CanvasElementKind.Panel; offsetX = 16f; offsetY = 8f; width = 200f; height = 100f; window = this }
        val topLeft = child(parent) { kind = CanvasElementKind.Bar; offsetX = 4f; offsetY = 6f; width = 50f; height = 20f }
        val bottomRight = child(parent) { kind = CanvasElementKind.Button; anchor = CanvasAnchor.BottomRight; offsetX = 4f; offsetY = 6f; width = 50f; height = 20f }

        val before = frame()
        window.offsetX = 100f
        window.offsetY = 50f
        val after = frame()

        assertEquals(listOf(20, 14, 50, 20), before.box(topLeft))
        assertEquals(listOf(16 + 200 - 4 - 50, 8 + 100 - 6 - 20, 50, 20), before.box(bottomRight), "against the parent's corner, not the screen's")
        assertEquals(listOf(104, 56, 50, 20), after.box(topLeft), "moving the window moves its controls")
    }

    @Test
    fun anElementSitsInTheNearestElementAboveItThroughPlainNodes() {
        val window = element { kind = CanvasElementKind.Panel; offsetX = 30f; offsetY = 20f; width = 200f; height = 100f }
        val group = world.create().also { world.add(it, Transform(parent = window)) }
        val label = child(group) { text = "HP"; offsetX = 5f; offsetY = 5f; width = 40f; height = 20f }

        assertEquals(listOf(35, 25, 40, 20), frame().box(label))
    }

    @Test
    fun aHiddenParentHidesItsChildren() {
        val window = element { kind = CanvasElementKind.Panel; visible = false }
        val label = child(window) { text = "HP" }

        assertNull(frame().node(label))
    }

    @Test
    fun scaleMultipliesEverySizeAndOffset() {
        val window = element { kind = CanvasElementKind.Panel; offsetX = 16f; offsetY = 8f; width = 200f; height = 100f }
        val bar = child(window) { kind = CanvasElementKind.Bar; offsetX = 4f; offsetY = 6f; width = 50f; height = 20f }

        val out = frame(scale = 2f)

        assertEquals(listOf(32, 16, 400, 200), out.box(window))
        assertEquals(listOf(40, 28, 100, 40), out.box(bar))
    }

    @Test
    fun atAFractionalScaleEveryImageEdgeLandsOnAWholePixel() {
        val frameImage = ImageBitmap(6, 6, rgba(*IntArray(36) { RED }))
        val window = element {
            kind = CanvasElementKind.Panel; offsetX = 7f; offsetY = 7f; width = 41f; height = 21f
            style = CanvasStyle(image = CanvasImage("frame.png", sliceLeft = 1, sliceTop = 1, sliceRight = 1, sliceBottom = 1, repeatEdges = true))
        }
        child(window) { kind = CanvasElementKind.Image; offsetX = 3f; offsetY = 3f; width = 9f; height = 5f; style = CanvasStyle(image = CanvasImage("frame.png")) }

        val pieces = ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, images = mapOf("frame.png" to frameImage), scale = 1.5f) }
            .primitives.filterIsInstance<UiDrawPrimitive.Texture>()

        assertTrue(pieces.size > 9)
        val offGrid = pieces.filter { piece -> listOf(piece.x, piece.y, piece.w, piece.h).any { it != kotlin.math.round(it) } }
        assertEquals(emptyList(), offGrid)
    }

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
                background = "#202020",
                gradient = CanvasGradient("#303030", "#101010", horizontal = true),
                image = CanvasImage("ui/frame.png", regionX = 4, regionWidth = 48, sliceLeft = 16, sliceRight = 16, repeatEdges = true, tint = "#FFFFFF80"),
                fillImage = CanvasImage("ui/gauge.png", sliceLeft = 3, sliceRight = 3, repeatCenter = true, pixelated = true),
                cornerRadius = 6f,
                borderWidth = 1f,
                borderColor = "#FFFFFF40",
                shadow = CanvasShadow(offsetY = 3f, blur = 8f, spread = 1f),
                textColor = "#FFD700",
                alpha = 0.9f,
                hovered = CanvasStateStyle(image = CanvasImage("ui/frame.png", regionY = 48)),
                pressed = CanvasStateStyle(background = "#101010", borderColor = "#FFFFFF", alpha = 1f),
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
    fun aGradientRunsFromItsStartToItsEnd() {
        element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; style = CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF")) }
        element {
            kind = CanvasElementKind.Panel; offsetX = 50f; offsetY = 0f; width = 40f; height = 40f
            style = CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF", horizontal = true))
        }

        val pixels = pixels(100, 40, emptyMap())

        assertTrue(red(pixels[2][20]) > 200 && blue(pixels[37][20]) > 200, "top red, bottom blue")
        assertTrue(red(pixels[20][52]) > 200 && blue(pixels[20][87]) > 200, "left red, right blue")
    }

    @Test
    fun aCornerRadiusRoundsTheFill() {
        element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; background = "#FF0000"; style = CanvasStyle(cornerRadius = 12f) }

        val pixels = pixels(40, 40, emptyMap())

        assertEquals(BACKGROUND, pixels[0][0], "the corner is cut away")
        assertEquals(RED, pixels[20][20])
        assertEquals(RED, pixels[2][20], "the edge between the corners is still filled")
    }

    @Test
    fun aBorderDrawsInsideTheEdge() {
        element {
            kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; background = "#FF0000"
            style = CanvasStyle(borderWidth = 4f, borderColor = "#00FF00")
        }

        val pixels = pixels(40, 40, emptyMap())

        assertEquals(GREEN, pixels[20][1])
        assertEquals(RED, pixels[20][20])
    }

    @Test
    fun aShadowFallsBelowTheElement() {
        element {
            kind = CanvasElementKind.Panel; offsetX = 20f; offsetY = 10f; width = 40f; height = 20f; background = "#FF0000"
            style = CanvasStyle(shadow = CanvasShadow(color = "#FFFFFF", offsetY = 10f, blur = 0f))
        }

        val pixels = pixels(80, 60, emptyMap())

        assertEquals(WHITE, pixels[35][40], "under the element, where it was cast")
        assertEquals(RED, pixels[20][40], "the element stays over its shadow")
    }

    @Test
    fun alphaFadesTheElement() {
        element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 40f; height = 40f; background = "#FF0000"; style = CanvasStyle(alpha = 0.5f) }

        assertEquals(128f, red(pixels(40, 40, emptyMap())[20][20]).toFloat(), 2f)
    }

    @Test
    fun aButtonTakesItsHoveredAndPressedLooks() {
        element {
            kind = CanvasElementKind.Button; offsetX = 0f; offsetY = 0f; width = 100f; height = 40f; background = "#FF0000"
            style = CanvasStyle(hovered = CanvasStateStyle(background = "#0000FF"), pressed = CanvasStateStyle(background = "#00FF00"))
        }
        val host = ComposeHost()
        fun look(input: FrameInput): Int {
            val pixels = host.frame(input) { SceneCanvas(world) }.primitives.rasterize(100, 40, Color.Black)
            return ((pixels[(20 * 100 + 90) * 4].toInt() and 0xFF) shl 16) or ((pixels[(20 * 100 + 90) * 4 + 1].toInt() and 0xFF) shl 8) or
                (pixels[(20 * 100 + 90) * 4 + 2].toInt() and 0xFF)
        }

        val idle = look(FrameInput(100, 40))
        look(FrameInput(100, 40, pointerX = 50, pointerY = 20))
        val hovered = look(FrameInput(100, 40, pointerX = 50, pointerY = 20))
        look(FrameInput(100, 40, pointerX = 50, pointerY = 20, pointerDown = true, pointerPressed = true))
        val pressed = look(FrameInput(100, 40, pointerX = 50, pointerY = 20, pointerDown = true))

        assertEquals(listOf(RED, BLUE, GREEN), listOf(idle, hovered, pressed))
    }

    @Test
    fun anElementSavedBeforeStylesLoadsUnchanged() {
        val saved = """{"kind":"Bar","width":120.0,"height":12.0,"color":"#E5484D","background":"#00000080","value":0.5}"""

        val loaded = kotlinx.serialization.json.Json.decodeFromString(SceneCanvasElement.serializer(), saved)

        assertEquals(CanvasStyle(), loaded.style)
        assertEquals(SceneCanvasElement(kind = CanvasElementKind.Bar, width = 120f, height = 12f, color = "#E5484D", background = "#00000080", value = 0.5f), loaded)
    }

    @Test
    fun validationRejectsABadStyle() {
        val issues = SceneCanvasElement(
            style = CanvasStyle(
                background = "red",
                gradient = CanvasGradient("#FF0000", "blue"),
                cornerRadius = -1f,
                shadow = CanvasShadow(blur = -2f),
                alpha = 2f,
                pressed = CanvasStateStyle(textColor = "white"),
            ),
        ).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.style.background \"red\" must be #RRGGBB or #RRGGBBAA",
                "canvas_element.style.cornerRadius must not be negative",
                "canvas_element.style.alpha must be between 0 and 1",
                "canvas_element.style.gradient.end \"blue\" must be #RRGGBB or #RRGGBBAA",
                "canvas_element.style.shadow.blur must not be negative",
                "canvas_element.style.pressed.textColor \"white\" must be #RRGGBB or #RRGGBBAA",
            ),
            issues,
        )
    }

    private fun FrameOutput.glyphs() = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    @Test
    fun textAlignPlacesTheTextInItsElement() {
        element { offsetX = 0f; offsetY = 0f; width = 200f; height = 40f; text = "HP"; textAlign = CanvasAnchor.Center }
        element { kind = CanvasElementKind.Button; offsetX = 0f; offsetY = 50f; width = 200f; height = 40f; text = "Go"; textAlign = CanvasAnchor.CenterLeft }

        val glyphs = frame().glyphs()
        val centred = glyphs.filter { it.y < 45f }
        val left = glyphs.filter { it.y >= 45f }

        assertEquals(100f, (centred.minOf { it.x } + centred.maxOf { it.x + it.w }) / 2f, 3f, "centred across")
        assertEquals(20f, (centred.minOf { it.y } + centred.maxOf { it.y + it.h }) / 2f, 4f, "and down")
        assertTrue(left.minOf { it.x } < 10f, "a Button label set to the left starts at its left edge")
    }

    @Test
    fun textKeepsItsOwnPlaceWhenNoAlignIsSet() {
        element { offsetX = 0f; offsetY = 0f; width = 200f; height = 40f; text = "HP" }
        element { kind = CanvasElementKind.Button; offsetX = 0f; offsetY = 50f; width = 200f; height = 40f; text = "Go" }

        val glyphs = frame().glyphs()

        assertTrue(glyphs.filter { it.y < 45f }.minOf { it.x } < 10f, "a Text starts at its top-left")
        assertTrue(glyphs.filter { it.y >= 45f }.minOf { it.x } > 80f, "a Button's label is centred")
    }

    @Test
    fun aTextOutlineAndShadowDrawUnderItsText() {
        element {
            offsetX = 0f; offsetY = 0f; width = 200f; height = 40f; text = "HP"; color = "#FFFFFF"
            style = CanvasStyle(textOutline = CanvasTextOutline("#000000", 2f), textShadow = CanvasTextShadow("#FF0000"))
        }

        val colours = frame().glyphs().map { it.color }

        assertEquals(listOf(Color.fromHex("#FF0000"), Color.fromHex("#000000"), Color.White), colours.distinct(), "shadow, then outline, then the text")
    }

    /** Ten pixels per world unit across and down, from the screen's corner; null behind z 0. */
    private class FlatProjector(var box: Rectangle? = null) : CanvasProjector {
        override fun project(x: Float, y: Float, z: Float): Vec2? = if (z < 0f) null else Vec2(x * 10f, y * 10f)

        override fun bounds(entity: Entity): Rectangle? = box
    }

    private fun named(name: String, x: Float, y: Float, z: Float = 0f): Entity = world.create().also {
        world.add(it, Name(name))
        world.add(it, Transform(position = Vec3f(x, y, z)).apply { worldMatrix = Mat4().translate(x, y, z) })
    }

    private fun followFrame(projector: CanvasProjector?, scale: Float = 1f): FrameOutput =
        ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world, projector = projector, scale = scale) }

    @Test
    fun aFollowerLandsOnItsNodesPixelAtAnyScale() {
        named("Hero", 20f, 10f)
        val plate = element { follow = "Hero"; anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }
        val marker = element { follow = "Hero"; followBounds = true }
        val projector = FlatProjector(box = Rectangle(100f, 50f, 80f, 120f))

        val out = followFrame(projector, scale = 2f)

        assertEquals(listOf(200, 100, 20, 20), out.box(plate), "on the projected pixel, twice the size")
        assertEquals(listOf(100, 50, 80, 120), out.box(marker), "the box in pixels, whatever the scale")
    }

    @Test
    fun anElementStandsOnTheNodeItFollowsWithItsAnchorOnThePoint() {
        val hero = named("Hero", 20f, 10f)
        val plate = element {
            text = "Hero"; follow = "Hero"; anchor = CanvasAnchor.BottomCenter; offsetX = 0f; offsetY = 4f; width = 60f; height = 20f
        }

        val before = followFrame(FlatProjector()).box(plate)
        world.get<Transform>(hero)!!.worldMatrix = Mat4().translate(30f, 12f, 0f)
        val after = followFrame(FlatProjector()).box(plate)

        assertEquals(listOf(200 - 30, 100 - 20 - 4, 60, 20), before, "its bottom centre 4 dp above the node's point")
        assertEquals(listOf(300 - 30, 120 - 20 - 4, 60, 20), after, "and it moves with the node")
    }

    @Test
    fun aFollowOffsetMovesThePointInTheWorld() {
        named("Hero", 20f, 10f)
        val plate = element { follow = "Hero"; followOffset = SceneVec3(0f, -2f, 0f); anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }

        assertEquals(listOf(200, 80, 10, 10), followFrame(FlatProjector()).box(plate))
    }

    @Test
    fun anElementFollowingANodeBehindTheCameraOrMissingIsHidden() {
        named("Behind", 5f, 5f, -1f)
        val behind = element { follow = "Behind" }
        val missing = element { follow = "Nobody" }
        val unprojected = element { follow = "Behind" }

        val out = followFrame(FlatProjector())

        assertNull(out.node(behind))
        assertNull(out.node(missing))
        assertNull(ComposeHost().frame(FrameInput(800, 600)) { SceneCanvas(world) }.node(unprojected), "no projector, nothing to follow with")
    }

    @Test
    fun aBoundsFollowerCoversTheNodesScreenBoxAndItsChildrenFollow() {
        named("Target", 0f, 0f)
        val marker = element { kind = CanvasElementKind.Panel; follow = "Target"; followBounds = true; width = 5f; height = 5f }
        val corner = child(marker) { kind = CanvasElementKind.Image; anchor = CanvasAnchor.BottomRight; offsetX = 0f; offsetY = 0f; width = 8f; height = 8f }

        val out = followFrame(FlatProjector(box = Rectangle(100f, 50f, 80f, 120f)))

        assertEquals(listOf(100, 50, 80, 120), out.box(marker))
        assertEquals(listOf(100 + 80 - 8, 50 + 120 - 8, 8, 8), out.box(corner), "anchored to the box's corner")
    }

    @Test
    fun aFollowerNestedUnderAnotherElementIsStillPlacedOnItsNode() {
        named("Hero", 20f, 10f)
        val window = element { kind = CanvasElementKind.Panel; offsetX = 300f; offsetY = 300f; width = 100f; height = 100f }
        val plate = child(window) { follow = "Hero"; anchor = CanvasAnchor.TopLeft; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f }

        assertEquals(listOf(200, 100, 10, 10), followFrame(FlatProjector()).box(plate))
    }

    private fun slots(parent: Entity, count: Int, size: Float = 32f): List<Entity> =
        List(count) { i -> child(parent) { kind = CanvasElementKind.Panel; order = i; width = size; height = size } }

    @Test
    fun aRowPlacesItsChildrenOneGapApart() {
        val bar = element { kind = CanvasElementKind.Panel; offsetX = 10f; offsetY = 20f; width = 400f; height = 40f; layout = CanvasLayout(gap = 4f) }
        val slots = slots(bar, 5)

        val out = frame()

        assertEquals(listOf(10, 46, 82, 118, 154), slots.map { out.box(it)[0] }, "36 dp apart, from the bar's left")
        assertTrue(slots.all { out.box(it)[1] == 20 })
    }

    @Test
    fun wrapMovesWhatDoesNotFitToANewLine() {
        val strip = element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 100f; height = 200f; layout = CanvasLayout(gap = 4f, wrap = true) }
        val icons = slots(strip, 5)

        val out = frame()

        assertEquals(listOf(0 to 0, 36 to 0, 0 to 36, 36 to 36, 0 to 72), icons.map { out.box(it).let { box -> box[0] to box[1] } })
    }

    @Test
    fun aChildAddedAtRunTimeMovesTheOnesAfterIt() {
        val bar = element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 400f; height = 40f; layout = CanvasLayout() }
        val first = child(bar) { kind = CanvasElementKind.Panel; order = 0; width = 32f; height = 32f }
        val last = child(bar) { kind = CanvasElementKind.Panel; order = 2; width = 32f; height = 32f }
        val host = ComposeHost()
        val before = frame(host).box(last)[0]

        child(bar) { kind = CanvasElementKind.Panel; order = 1; width = 50f; height = 32f }
        val after = frame(host)

        assertEquals(32, before)
        assertEquals(0, after.box(first)[0])
        assertEquals(82, after.box(last)[0], "pushed along by the new one")
    }

    @Test
    fun growSharesTheLeftoverSpaceAndCentreingCentres() {
        val bar = element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 200f; height = 40f; layout = CanvasLayout(align = CanvasAlign.Center) }
        child(bar) { kind = CanvasElementKind.Panel; order = 0; width = 32f; height = 20f }
        val fill = child(bar) { kind = CanvasElementKind.Panel; order = 1; width = 0f; height = 20f; grow = 1f }
        val centred = element {
            kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 100f; width = 200f; height = 40f
            layout = CanvasLayout(justify = CanvasJustify.Center, padding = 5f)
        }
        val middle = child(centred) { kind = CanvasElementKind.Panel; width = 40f; height = 10f }

        val out = frame()

        assertEquals(listOf(32, 10, 168, 20), out.box(fill), "the rest of the row, centred down it")
        assertEquals(80, out.box(middle)[0], "centred along a row")
        assertEquals(105, out.box(middle)[1], "inside the padding")
    }

    @Test
    fun validationRejectsANegativeGapPaddingOrGrow() {
        val issues = SceneCanvasElement(layout = CanvasLayout(gap = -1f, padding = -2f), grow = -1f).validate("nodes[0]").map { it.message }

        assertEquals(
            listOf(
                "canvas_element.layout.gap must not be negative",
                "canvas_element.layout.padding must not be negative",
                "canvas_element.grow must not be negative",
            ),
            issues,
        )
    }

    @Test
    fun theHostsTextStyleDoesNotReachTheScenesText() {
        element { offsetX = 0f; offsetY = 0f; width = 200f; height = 40f; text = "HP"; color = "#FFFFFF" }

        val colours = ComposeHost().frame(FrameInput(800, 600)) {
            CompositionLocalProvider(LocalTextStyle provides TextStyle(outline = TextOutline(Color.Black, 2f))) { SceneCanvas(world) }
        }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>().map { it.color }.distinct()

        assertEquals(listOf(Color.White), colours, "no outline the scene did not ask for")
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

private fun red(rgb: Int) = rgb shr 16 and 0xFF

private fun blue(rgb: Int) = rgb and 0xFF

/** 2 x 2: red, green over blue, white. */
private val QUADRANTS = ImageBitmap(2, 2, rgba(RED, GREEN, BLUE, WHITE))

private fun rgba(vararg pixels: Int): ByteArray =
    pixels.flatMap { listOf((it shr 16).toByte(), (it shr 8).toByte(), it.toByte(), -1) }.toByteArray()

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
