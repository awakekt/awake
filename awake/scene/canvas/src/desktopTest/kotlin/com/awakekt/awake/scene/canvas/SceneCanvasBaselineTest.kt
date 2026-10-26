/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.localization.LocaleId
import com.awakekt.awake.core.localization.StringTableSource
import com.awakekt.awake.core.localization.Strings
import com.awakekt.awake.core.math2d.Rectangle
import com.awakekt.awake.core.math2d.Vec2
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasAlign
import com.awakekt.awake.ui.canvas.CanvasAnchor
import com.awakekt.awake.ui.canvas.CanvasBar
import com.awakekt.awake.ui.canvas.CanvasBarDirection
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasFillImageMode
import com.awakekt.awake.ui.canvas.CanvasGradient
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasLayout
import com.awakekt.awake.ui.canvas.CanvasMask
import com.awakekt.awake.ui.canvas.CanvasPadding
import com.awakekt.awake.ui.canvas.CanvasShadow
import com.awakekt.awake.ui.canvas.CanvasStepper
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.ui.canvas.CanvasSweep
import com.awakekt.awake.ui.canvas.CanvasTextOutline
import com.awakekt.awake.ui.canvas.CanvasTextShadow
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test

class SceneCanvasBaselineTest {
    @Test
    fun aStatusWindowWithItsGaugesAtTwiceItsSize() {
        val world = World()
        val window = world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel; offsetX = 8f; offsetY = 8f; width = 120f; height = 52f
                    style = CanvasStyle(image = CanvasImage("frame.png", sliceLeft = 4, sliceTop = 4, sliceRight = 4, sliceBottom = 4, repeatEdges = true, pixelated = true))
                },
            )
        }
        for ((row, value) in listOf(0.8f, 0.45f, 0.2f).withIndex()) {
            world.create().also {
                world.add(it, Transform(parent = window))
                world.add(
                    it,
                    CanvasElement().apply {
                        kind = CanvasElementKind.Bar; offsetX = 10f; offsetY = 10f + row * 12f; width = 100f; height = 8f; this.value = value
                        background = "#00000080"
                        style = CanvasStyle(fillImage = CanvasImage("gauge.png", sliceLeft = 2, sliceRight = 2, tint = TINTS[row], pixelated = true))
                    },
                )
            }
        }

        ComposeHost().frame(FrameInput(272, 136)) {
            SceneCanvas(world, images = mapOf("frame.png" to FRAME, "gauge.png" to GAUGE), scale = 2f)
        }.primitives.assertMatchesBaseline("scene-canvas-status-window", 272, 136)
    }

    @Test
    fun corneredBorderedGradientAndShadowedElements() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = world.create().also { world.add(it, CanvasElement().apply(configure)) }
        element {
            kind = CanvasElementKind.Button; offsetX = 16f; offsetY = 16f; width = 120f; height = 40f
            style = CanvasStyle(
                gradient = CanvasGradient("#4A6FD8", "#2A3F8A"),
                cornerRadius = 10f,
                borderWidth = 1f,
                borderColor = "#FFFFFF60",
                shadow = CanvasShadow(color = "#000000A0", offsetY = 4f, blur = 8f),
            )
        }
        element {
            kind = CanvasElementKind.Panel; offsetX = 152f; offsetY = 16f; width = 80f; height = 40f; background = "#2E2A24"
            style = CanvasStyle(cornerRadius = 6f, borderWidth = 2f, borderColor = "#C8A060")
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 16f; offsetY = 80f; width = 216f; height = 12f; value = 0.65f
            color = "#30A46C"; background = "#00000080"
            style = CanvasStyle(cornerRadius = 6f)
        }
        element {
            kind = CanvasElementKind.Panel; offsetX = 16f; offsetY = 108f; width = 216f; height = 20f
            style = CanvasStyle(gradient = CanvasGradient("#E5484D", "#FFB224", horizontal = true), alpha = 0.6f)
        }

        ComposeHost().frame(FrameInput(248, 144)) { SceneCanvas(world) }
            .primitives.assertMatchesBaseline("scene-canvas-styles", 248, 144)
    }

    @Test
    fun centredOutlinedAndShadowedText() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = world.create().also { world.add(it, CanvasElement().apply(configure)) }
        element {
            kind = CanvasElementKind.Bar; offsetX = 16f; offsetY = 16f; width = 200f; height = 20f; value = 0.8f
            color = "#C0392B"; background = "#2A2A2A"
            style = CanvasStyle(cornerRadius = 4f)
        }
        element {
            // The label over the gauge: an element of its own, so the bar's fill does not cover it.
            offsetX = 16f; offsetY = 16f; width = 200f; height = 20f; text = "HP 120/150"; fontSize = 14f; color = "#FFFFFF"
            textAlign = CanvasAnchor.Center; style = CanvasStyle(textOutline = CanvasTextOutline("#000000", 1f))
        }
        element {
            offsetX = 16f; offsetY = 48f; width = 200f; height = 24f; text = "Harbor Guard"; fontSize = 16f; color = "#F5D76E"
            textAlign = CanvasAnchor.Center; background = "#5A7FA0"; style = CanvasStyle(textShadow = CanvasTextShadow("#000000C0", 1f, 1f))
        }

        ComposeHost().frame(FrameInput(232, 88)) { SceneCanvas(world) }
            .primitives.assertMatchesBaseline("scene-canvas-text", 232, 88, font = UiFonts.default())
    }

    /** A chat line written by a system, and below it a focused, typed-into line with its caret. */
    @Test
    fun textFieldsWrittenAndTypedInto() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = CanvasElement().apply(configure).also { world.add(world.create(), it) }
        element {
            kind = CanvasElementKind.TextField; offsetX = 16f; offsetY = 16f; width = 200f; height = 20f
            text = "Hello, Harbor Town"; fontSize = 14f; background = "#00000080"
        }
        val typing = element {
            kind = CanvasElementKind.TextField; offsetX = 16f; offsetY = 48f; width = 200f; height = 20f
            fontSize = 14f; color = "#F5D76E"; background = "#00000080"
            style = CanvasStyle(borderWidth = 1f, borderColor = "#FFFFFF60")
        }
        val host = ComposeHost()
        fun frame(typed: String = "") = host.frame(FrameInput(232, 84, typedText = typed)) { SceneCanvas(world) }
        frame()
        typing.focused = true
        frame()

        frame(typed = "On my way")
            .primitives.assertMatchesBaseline("scene-canvas-text-field", 232, 84, font = UiFonts.default())
    }

    /** A focused password field typed into: one mask a character, the caret after the last. */
    @Test
    fun aPasswordFieldTypedInto() {
        val world = World()
        val field = CanvasElement().apply {
            kind = CanvasElementKind.TextField; offsetX = 16f; offsetY = 16f; width = 200f; height = 20f
            fontSize = 14f; background = "#00000080"; mask = "*"
            style = CanvasStyle(borderWidth = 1f, borderColor = "#FFFFFF60")
        }
        world.add(world.create(), field)
        val host = ComposeHost()
        fun frame(typed: String = "") = host.frame(FrameInput(232, 52, typedText = typed)) { SceneCanvas(world) }
        frame()
        field.focused = true
        frame()

        frame(typed = "open sesame")
            .primitives.assertMatchesBaseline("scene-canvas-password-field", 232, 52, font = UiFonts.default())
    }

    /**
     * The same words twice: above in a font file the project loaded, Roboto Black, below in the
     * default Roboto. Before `font` both lines drew as the lower one.
     */
    @Test
    fun aTextInAProjectFontBesideTheDefault() {
        val world = World()
        for ((row, font) in listOf("fonts/title.ttf", "").withIndex()) {
            world.add(
                world.create(),
                CanvasElement().apply {
                    offsetX = 12f; offsetY = 8f + row * 36f; width = 216f; height = 32f
                    text = "Harbor Town"; fontSize = 28f; color = "#1A1A1A"; this.font = font
                },
            )
        }
        val title = UiFonts.load(File(ROBOTO_BLACK).readBytes(), "Roboto-Black.ttf")

        ComposeHost().frame(FrameInput(240, 84)) { SceneCanvas(world, fonts = mapOf("fonts/title.ttf" to title)) }
            .primitives.assertMatchesBaseline("scene-canvas-font-file", 240, 84)
    }

    @Test
    fun aMarkerFramingATargetAndANamePlateAboveIt() {
        val world = World()
        world.create().also {
            world.add(it, Name("Target"))
            world.add(it, Transform())
        }
        fun element(parent: com.awakekt.awake.ecs.Entity? = null, configure: CanvasElement.() -> Unit) = world.create().also {
            if (parent != null) world.add(it, Transform(parent = parent))
            world.add(it, CanvasElement().apply(configure))
        }
        // Stands in for the target as drawn: the box the projector reports.
        element { kind = CanvasElementKind.Panel; offsetX = 70f; offsetY = 40f; width = 60f; height = 90f; background = "#3A4A5A" }
        val marker = element { kind = CanvasElementKind.Panel; follow = "Target"; followBounds = true }
        for (corner in listOf(CanvasAnchor.TopLeft, CanvasAnchor.TopRight, CanvasAnchor.BottomLeft, CanvasAnchor.BottomRight)) {
            element(marker) { kind = CanvasElementKind.Panel; anchor = corner; offsetX = 0f; offsetY = 0f; width = 10f; height = 10f; background = "#FF4040" }
        }
        element {
            follow = "Target"; anchor = CanvasAnchor.BottomCenter; offsetX = 0f; offsetY = 6f; width = 120f; height = 18f
            text = "Harbor Guard"; fontSize = 14f; color = "#FFFFFF"; textAlign = CanvasAnchor.Center
            style = CanvasStyle(textOutline = CanvasTextOutline("#000000", 1f))
        }
        val projector = object : CanvasProjector {
            override fun project(x: Float, y: Float, z: Float) = Vec2(100f + x, 40f + y)

            override fun bounds(entity: com.awakekt.awake.ecs.Entity) = Rectangle(70f, 40f, 60f, 90f)
        }

        ComposeHost().frame(FrameInput(200, 150)) { SceneCanvas(world, projector = projector) }
            .primitives.assertMatchesBaseline("scene-canvas-follow", 200, 150, font = UiFonts.default())
    }

    @Test
    fun anActionBarAndAWrappingStripOfIcons() {
        val world = World()
        fun element(parent: com.awakekt.awake.ecs.Entity? = null, configure: CanvasElement.() -> Unit) = world.create().also {
            if (parent != null) world.add(it, Transform(parent = parent))
            world.add(it, CanvasElement().apply(configure))
        }
        val bar = element {
            kind = CanvasElementKind.Panel; offsetX = 8f; offsetY = 8f; width = 216f; height = 32f; background = "#202830"
            layout = CanvasLayout(gap = 4f, padding = 4f, align = CanvasAlign.Center)
        }
        repeat(8) { i -> element(bar) { kind = CanvasElementKind.Panel; order = i; width = 22f; height = 22f; background = "#5A7FA0"; style = CanvasStyle(cornerRadius = 3f) } }
        val strip = element {
            kind = CanvasElementKind.Panel; offsetX = 8f; offsetY = 48f; width = 100f; height = 56f; background = "#202830"
            layout = CanvasLayout(gap = 2f, padding = 2f, wrap = true)
        }
        repeat(7) { i -> element(strip) { kind = CanvasElementKind.Panel; order = i; width = 22f; height = 22f; background = if (i % 2 == 0) "#30A46C" else "#E5484D" } }
        val row = element {
            kind = CanvasElementKind.Panel; offsetX = 116f; offsetY = 48f; width = 108f; height = 24f; background = "#202830"
            layout = CanvasLayout(gap = 2f, padding = 2f)
        }
        element(row) { kind = CanvasElementKind.Panel; order = 0; width = 20f; height = 20f; background = "#F5D76E" }
        element(row) { kind = CanvasElementKind.Panel; order = 1; width = 0f; height = 20f; grow = 1f; background = "#3E63DD" }

        ComposeHost().frame(FrameInput(232, 112)) { SceneCanvas(world) }
            .primitives.assertMatchesBaseline("scene-canvas-layout", 232, 112)
    }

    /** A cooldown sweep over an icon-like square, a rotated picture, and a line of text with a link. */
    @Test
    fun aCooldownSweepARotatedPictureAndALinkInText() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = world.create().also { world.add(it, CanvasElement().apply(configure)) }
        element {
            kind = CanvasElementKind.Bar; offsetX = 16f; offsetY = 16f; width = 48f; height = 48f
            value = 0.3f; color = "#000000A0"; background = "#C8A060"
            style = CanvasStyle(fillImageMode = CanvasFillImageMode.Sweep, cornerRadius = 6f)
        }
        element {
            kind = CanvasElementKind.Image; offsetX = 92f; offsetY = 16f; width = 72f; height = 36f
            style = CanvasStyle(image = CanvasImage("frame.png", pixelated = true), rotation = 30f)
        }
        element {
            offsetX = 16f; offsetY = 84f; width = 200f; height = 20f; fontSize = 14f; color = "#FFFFFF"
            text = "Visit the [[quest|quest board]] for work."
        }

        val output = ComposeHost().frame(FrameInput(224, 112)) { SceneCanvas(world, images = mapOf("frame.png" to FRAME)) }
        output.primitives.assertMatchesBaseline("scene-canvas-sweep-rotation-links", 224, 112, font = UiFonts.default(), layers = output.graphicsLayers)
    }

    /**
     * Each new gauge: sweeps as a ring, cut to a circle and turning the other way; bars up, right to
     * left, segmented, ticked and labelled, and around as a ring; and a stepper.
     */
    @Test
    fun sweepsProgressBarsAndAStepper() {
        val world = World()
        fun element(configure: CanvasElement.() -> Unit) = world.create().also { world.add(it, CanvasElement().apply(configure)) }
        element { kind = CanvasElementKind.Panel; offsetX = 0f; offsetY = 0f; width = 304f; height = 124f; background = "#18181B"; order = -1 }
        element {
            kind = CanvasElementKind.Sweep; offsetX = 16f; offsetY = 16f; width = 40f; height = 40f; value = 0.65f; color = "#3E63DD"
            sweep = CanvasSweep(thickness = 6f, track = "#FFFFFF30")
        }
        element {
            kind = CanvasElementKind.Sweep; offsetX = 64f; offsetY = 16f; width = 40f; height = 40f; value = 0.3f; color = "#E5484D"
            background = "#30A46C"; sweep = CanvasSweep(mask = CanvasMask.Circle, startAngle = 90f, clockwise = false)
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 120f; offsetY = 16f; width = 14f; height = 80f; value = 0.4f; color = "#30A46C"
            bar = CanvasBar(direction = CanvasBarDirection.BottomToTop, track = "#FFFFFF20", trailColor = "#E5484D")
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 150f; offsetY = 16f; width = 140f; height = 18f; value = 0.6f; color = "#3E63DD"
            fontSize = 12f; bar = CanvasBar(direction = CanvasBarDirection.RightToLeft, track = "#FFFFFF20", label = true)
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 150f; offsetY = 42f; width = 140f; height = 10f; value = 0.5f; color = "#F5D90A"
            bar = CanvasBar(segments = 5, track = "#FFFFFF20")
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 150f; offsetY = 60f; width = 140f; height = 10f; value = 0.7f; color = "#30A46C"
            bar = CanvasBar(ticks = 4, track = "#FFFFFF20", tickColor = "#000000")
        }
        element {
            kind = CanvasElementKind.Bar; offsetX = 16f; offsetY = 64f; width = 48f; height = 48f; value = 0.75f; color = "#3E63DD"
            fontSize = 11f; bar = CanvasBar(direction = CanvasBarDirection.Radial, thickness = 5f, track = "#FFFFFF20", label = true)
        }
        element {
            kind = CanvasElementKind.Stepper; offsetX = 150f; offsetY = 80f; width = 120f; height = 28f; fontSize = 14f
            background = "#2B2B2B"; stepper = CanvasStepper(value = 2.5f, max = 10f, step = 0.5f, decimals = 1)
        }

        ComposeHost().frame(FrameInput(304, 124)) { SceneCanvas(world) }
            .primitives.assertMatchesBaseline("scene-canvas-gauges", 304, 124, font = UiFonts.default())
    }

    /**
     * Three padded fields: typed text 12 dp in from the border, an empty one showing its muted placeholder
     * from the same place, and an empty one with a wider padding and a placeholder.
     */
    @Test
    fun paddedTextFieldsWithAndWithoutAPlaceholder() = runTest {
        val world = World()
        fun field(y: Float, configure: CanvasElement.() -> Unit) = CanvasElement().apply {
            kind = CanvasElementKind.TextField; offsetX = 16f; offsetY = y; width = 200f; height = 28f
            fontSize = 14f; background = "#00000080"; style = CanvasStyle(borderWidth = 1f, borderColor = "#FFFFFF60")
            configure()
        }.also { world.add(world.create(), it) }
        field(8f) { text = "Harbor Town" }
        field(44f) { placeholder = "chat.hint" }
        field(80f) { placeholder = "chat.hint"; padding = CanvasPadding(horizontal = 32f, vertical = 0f) }
        val strings = Strings.load(StringTableSource.of(mapOf(LocaleId.English to mapOf("chat.hint" to "Say something"))), locale = LocaleId.English)

        ComposeHost().frame(FrameInput(232, 116)) { SceneCanvas(world, strings = strings) }
            .primitives.assertMatchesBaseline("scene-canvas-field-placeholder", 232, 116, font = UiFonts.default())
    }

    /**
     * A tooltip on a button near the canvas's right edge: it opens below and right of the pointer after
     * the hover delay, and, with no room to its right, to the pointer's left instead.
     */
    @Test
    fun aTooltipKeptInsideTheCanvasNearItsRightEdge() = runTest {
        val world = World()
        CanvasElement().apply {
            kind = CanvasElementKind.Button; anchor = CanvasAnchor.TopRight; offsetX = 12f; offsetY = 12f; width = 96f; height = 28f
            text = "Backpack"; fontSize = 14f; background = "#3A5F8A"; tooltip = "tip.bag"
        }.also { world.add(world.create(), it) }
        val strings = Strings.load(StringTableSource.of(mapOf(LocaleId.English to mapOf("tip.bag" to "Open your backpack"))), locale = LocaleId.English)
        val host = ComposeHost()
        fun frame(pointer: Boolean) = host.frame(
            FrameInput(240, 96, pointerX = if (pointer) 200 else FrameInput.UNKNOWN_POINTER, pointerY = if (pointer) 26 else FrameInput.UNKNOWN_POINTER, deltaSeconds = 0.1f),
        ) { SceneCanvas(world, strings = strings) }
        frame(pointer = false)
        repeat(TOOLTIP_FRAMES) { frame(pointer = true) }

        frame(pointer = true).primitives.assertMatchesBaseline("scene-canvas-tooltip", 240, 96, font = UiFonts.default())
    }

    private companion object {
        /** Frames of 0.1 s the pointer rests for, a little over the tooltip's delay. */
        const val TOOLTIP_FRAMES = 7

        /** An OFL font the atlas generator bakes, read where it lies, its licence beside it: heavy enough to tell apart. */
        const val ROBOTO_BLACK = "../../ui/font-atlas-generator/fonts/Roboto-Black.ttf"

        val TINTS = listOf("#E5484D", "#3E63DD", "#30A46C")

        /** 12 x 12: a dark frame with a light bevel, 4-pixel corners, its edges dashed. */
        val FRAME = image(12, 12) { x, y ->
            val edge = minOf(x, y, 11 - x, 11 - y)
            when {
                edge == 0 -> 0x1A1410
                edge == 1 -> if ((x + y) % 4 < 2) 0xC8A060 else 0x8A6A3A
                edge < 4 -> 0x3A2E24
                else -> 0x221C18
            }
        }

        /** 8 x 4, white so a tint colours it: rounded caps, a highlight along its top. */
        val GAUGE = image(8, 4) { x, y ->
            val cap = x == 0 || x == 7
            when {
                cap && (y == 0 || y == 3) -> -1
                y == 0 -> 0xFFFFFF
                else -> 0xB0B0B0
            }
        }

        /** [width] x [height] from 0xRRGGBB per pixel; -1 is transparent. */
        fun image(width: Int, height: Int, rgb: (Int, Int) -> Int) = ImageBitmap(
            width,
            height,
            ByteArray(width * height * 4) { i ->
                val pixel = rgb(i / 4 % width, i / 4 / width)
                when {
                    pixel == -1 -> 0
                    i % 4 == 3 -> -1
                    else -> (pixel shr (8 * (2 - i % 4))).toByte()
                }
            },
        )
    }
}
