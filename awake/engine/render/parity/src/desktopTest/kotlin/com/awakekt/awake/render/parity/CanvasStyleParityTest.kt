/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.World
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.scene.canvas.CanvasAnchor
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.canvas.CanvasGradient
import com.awakekt.awake.scene.canvas.CanvasShadow
import com.awakekt.awake.scene.canvas.CanvasStyle
import com.awakekt.awake.scene.canvas.CanvasTextOutline
import com.awakekt.awake.scene.canvas.SceneCanvas
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A scene UI element's gradient, rounded corners, border and shadow draw on Vulkan and on WebGPU:
 * the canvas's own primitives, through each backend's UI pass.
 */
class CanvasStyleParityTest {

    @Test
    fun everyShapeStyleDrawsOnBothBackends() {
        val world = World()
        fun element(x: Float, y: Float, width: Float, height: Float, background: String, style: CanvasStyle) = world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    kind = CanvasElementKind.Panel; offsetX = x; offsetY = y; this.width = width; this.height = height
                    this.background = background; this.style = style
                },
            )
        }
        element(0f, 0f, 60f, 60f, "#000000", CanvasStyle(gradient = CanvasGradient("#FF0000", "#0000FF")))
        element(64f, 0f, 60f, 60f, "#FFFFFF", CanvasStyle(cornerRadius = 20f))
        element(0f, 64f, 60f, 60f, "#FF0000", CanvasStyle(borderWidth = 6f, borderColor = "#00FF00"))
        element(64f, 64f, 40f, 30f, "#FF0000", CanvasStyle(shadow = CanvasShadow(color = "#FFFFFF", offsetY = 20f, blur = 0f)))
        val primitives = ComposeHost().frame(FrameInput(SIZE, SIZE)) { SceneCanvas(world) }.primitives

        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val frame = withHeadlessUi(backend, SIZE) { renderer ->
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    renderer.drawUiToTexture(target, primitives, font = null)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
            fun rgb(x: Int, y: Int): List<Int> = (0 until 3).map { frame[(y * SIZE + x) * 4 + it].toInt() and 0xFF }
            fun check(what: String, x: Int, y: Int, expected: (List<Int>) -> Boolean) =
                assertTrue(expected(rgb(x, y)), "$backend $what at ($x, $y): got ${rgb(x, y)}")

            check("gradient top", 30, 1) { (r, _, b) -> r >= ON && b < r }
            check("gradient bottom", 30, 58) { (r, _, b) -> b >= ON && r < b }
            check("rounded corner, cut away", 65, 1) { it.all { channel -> channel <= OFF } }
            check("rounded centre", 94, 30) { it.all { channel -> channel >= ON } }
            check("border", 2, 94) { (r, g, _) -> g >= ON && r <= OFF }
            check("inside the border", 30, 94) { (r, g, _) -> r >= ON && g <= OFF }
            check("shadow, below the element", 84, 105) { it.all { channel -> channel >= ON } }
            check("element over its shadow", 84, 80) { (r, g, _) -> r >= ON && g <= OFF }
        }
    }

    /**
     * A button, a framed panel, a gauge and a faded strip, drawn on both backends and written to
     * `build/reports/render-parity/`: the software rasterizer draws a shadow unblurred, so this is
     * what the style really looks like.
     */
    @Test
    fun aStyledHudDrawsOnBothBackends() {
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
        val primitives = ComposeHost().frame(FrameInput(HUD_WIDTH, HUD_HEIGHT)) { SceneCanvas(world) }.primitives

        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val frame = withHeadlessUi(backend, HUD_WIDTH) { renderer ->
                val target = renderer.createRenderTarget(HUD_WIDTH, HUD_HEIGHT)
                try {
                    renderer.drawUiToTexture(target, primitives, font = null)
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
            val image = BufferedImage(HUD_WIDTH, HUD_HEIGHT, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until HUD_HEIGHT) {
                for (x in 0 until HUD_WIDTH) {
                    val i = (y * HUD_WIDTH + x) * 4
                    fun channel(c: Int) = frame[i + c].toInt() and 0xFF
                    image.setRGB(x, y, (channel(3) shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2))
                }
            }
            File(REPORT_DIR).mkdirs()
            ImageIO.write(image, "png", File(REPORT_DIR, "canvas-styles-$backend.png"))
            // Under the button, where only its blurred shadow falls.
            val shadow = frame[((16 + 40 + 3) * HUD_WIDTH + 76) * 4 + 3].toInt() and 0xFF
            assertTrue(shadow in 1 until ON, "$backend: the shadow below the button is soft, alpha $shadow")
        }
    }

    /** An outlined, shadowed glyph shows its outline around its fill on both backends. */
    @Test
    fun outlinedTextDrawsOnBothBackends() {
        val world = World()
        world.create().also {
            world.add(
                it,
                CanvasElement().apply {
                    offsetX = 0f; offsetY = 0f; width = SIZE.toFloat(); height = SIZE.toFloat()
                    text = "O"; fontSize = 64f; color = "#FFFFFF"; textAlign = CanvasAnchor.Center
                    style = CanvasStyle(textOutline = CanvasTextOutline("#00FF00", 3f))
                },
            )
        }
        val primitives = ComposeHost().frame(FrameInput(SIZE, SIZE)) { SceneCanvas(world) }.primitives

        listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).forEach { backend ->
            val frame = withHeadlessUi(backend, SIZE) { renderer ->
                val target = renderer.createRenderTarget(SIZE, SIZE)
                try {
                    renderer.drawUiToTexture(target, primitives, font = UiFonts.default())
                    runBlocking { renderer.readPixels(target) }.data
                } finally {
                    target.destroy()
                }
            }
            val pixels = (0 until SIZE * SIZE).map { i -> (0 until 3).map { frame[i * 4 + it].toInt() and 0xFF } }
            val outline = pixels.count { (r, g, b) -> g >= ON && r <= OFF && b <= OFF }
            val fill = pixels.count { rgb -> rgb.all { it >= ON } }

            assertTrue(outline >= MIN_INK, "$backend: the outline shows, $outline green pixels")
            assertTrue(fill >= MIN_INK, "$backend: the glyph shows over it, $fill white pixels")
        }
    }

    private companion object {
        const val MIN_INK = 40
        const val HUD_WIDTH = 248
        const val HUD_HEIGHT = 144
        const val REPORT_DIR = "build/reports/render-parity"
        const val SIZE = 128
        const val ON = 200
        const val OFF = 64
    }
}
