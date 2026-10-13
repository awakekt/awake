/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElementBinding.toComponent
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoadCanvasImagesTest {
    @Test
    fun everyNamedImageIsDecodedOnceAndAMissingOneIsLeftOut() = runTest {
        val reads = mutableListOf<String>()
        val assets = AssetSource { path ->
            reads += path.value
            if (path.value == "frame.png") Result.success(PNG) else Result.failure(IllegalStateException("missing"))
        }
        val document = SceneDocument(
            nodes = listOf(
                SceneNode("panel", components = listOf(SceneCanvasElement(style = CanvasStyle(image = CanvasImage("frame.png"))))),
                SceneNode(
                    "hud",
                    children = listOf(
                        SceneNode("bar", components = listOf(SceneCanvasElement(style = CanvasStyle(image = CanvasImage("frame.png"), fillImage = CanvasImage("gone.png"))))),
                    ),
                ),
            ),
        )

        val images = loadCanvasImages(document, assets)

        assertTrue(hasCanvasImages(document))
        assertEquals(setOf("frame.png"), images.keys)
        assertEquals(3 to 2, images.getValue("frame.png").let { it.width to it.height })
        assertEquals(listOf("frame.png", "gone.png"), reads, "each path read once")
    }

    @Test
    fun aCheckReadsEveryNamedImageOnceAndDecodesNone() = runTest {
        val reads = mutableListOf<String>()
        val assets = AssetSource { path ->
            reads += path.value
            when (path.value) {
                "frame.png" -> Result.success(PNG)
                // Readable, but no decoder takes it: only a check that decodes would call it missing.
                "sheet.png" -> Result.success(NOT_AN_IMAGE)
                else -> Result.failure(IllegalStateException("missing"))
            }
        }
        val document = SceneDocument(
            nodes = listOf(
                SceneNode("panel", components = listOf(SceneCanvasElement(style = CanvasStyle(image = CanvasImage("frame.png"))))),
                SceneNode(
                    "hud",
                    children = listOf(
                        SceneNode("bar", components = listOf(SceneCanvasElement(style = CanvasStyle(image = CanvasImage("frame.png"), fillImage = CanvasImage("sheet.png"))))),
                        SceneNode("icon", components = listOf(SceneCanvasElement(style = CanvasStyle(image = CanvasImage("gone.png"))))),
                    ),
                ),
            ),
        )

        val missing = checkCanvasImages(document, assets)

        assertEquals(listOf("gone.png"), missing, "the one that could not be read; the one that could not be decoded was never decoded")
        assertEquals(listOf("frame.png", "sheet.png", "gone.png"), reads, "each path read once")
        assertEquals(setOf("frame.png"), loadCanvasImages(document, assets).keys, "the control: loading drops the one that does not decode as well")
    }

    @Test
    fun aButtonsHoveredAndPressedPicturesLoadWithItsOwn() = runTest {
        val reads = mutableListOf<String>()
        val assets = AssetSource { path ->
            reads += path.value
            if (path.value == "gone.png") Result.failure(IllegalStateException("missing")) else Result.success(PNG)
        }
        val button = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            style = CanvasStyle(
                image = CanvasImage("button.png"),
                hovered = CanvasStateStyle(image = CanvasImage("button-hover.png")),
                pressed = CanvasStateStyle(image = CanvasImage("button-down.png")),
            ),
        )
        // Only a state names a picture, and one state names one already named: still one read.
        val hoverOnly = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            style = CanvasStyle(hovered = CanvasStateStyle(image = CanvasImage("glow.png")), pressed = CanvasStateStyle(image = CanvasImage("button.png"))),
        )
        val document = SceneDocument(
            nodes = listOf(
                SceneNode("play", components = listOf(button)),
                SceneNode("menu", children = listOf(SceneNode("quit", components = listOf(hoverOnly)))),
            ),
        )

        val images = loadCanvasImages(document, assets)

        assertEquals(setOf("button.png", "button-hover.png", "button-down.png", "glow.png"), images.keys)
        assertEquals(listOf("button.png", "button-hover.png", "button-down.png", "glow.png"), reads, "each path read once")
        assertTrue(hasCanvasImages(SceneDocument(nodes = listOf(SceneNode("quit", components = listOf(hoverOnly))))), "a hover picture is an image too")
        reads.clear()
        val pressedOnly = SceneCanvasElement(kind = CanvasElementKind.Button, style = CanvasStyle(pressed = CanvasStateStyle(image = CanvasImage("gone.png"))))
        assertEquals(listOf("gone.png"), checkCanvasImages(SceneDocument(nodes = listOf(SceneNode("back", components = listOf(pressedOnly)))), assets))
        assertEquals(listOf("gone.png"), reads, "a check reads a state's picture as well")
    }

    @Test
    fun aButtonDrawsTheHoverPictureItsSceneNames() = runTest {
        val files = mapOf("up.png" to solidPng(0xFF0000), "over.png" to solidPng(0x00FF00))
        val saved = SceneCanvasElement(
            kind = CanvasElementKind.Button,
            offsetX = 0f,
            offsetY = 0f,
            width = 40f,
            height = 20f,
            style = CanvasStyle(image = CanvasImage("up.png"), hovered = CanvasStateStyle(image = CanvasImage("over.png"))),
        )
        val images = loadCanvasImages(SceneDocument(nodes = listOf(SceneNode("play", components = listOf(saved)))), AssetSource { Result.success(files.getValue(it.value)) })
        val world = World().also { it.add(it.create(), saved.toComponent()) }
        val host = ComposeHost()
        fun centre(input: FrameInput): Int {
            val pixels = host.frame(input) { SceneCanvas(world, images = images) }.primitives.rasterize(40, 20, Color.Black)
            val at = (10 * 40 + 20) * 4
            return ((pixels[at].toInt() and 0xFF) shl 16) or ((pixels[at + 1].toInt() and 0xFF) shl 8) or (pixels[at + 2].toInt() and 0xFF)
        }

        val idle = centre(FrameInput(40, 20))
        centre(FrameInput(40, 20, pointerX = 20, pointerY = 10))
        val hovered = centre(FrameInput(40, 20, pointerX = 20, pointerY = 10))

        assertEquals(listOf(0xFF0000, 0x00FF00), listOf(idle, hovered), "its own picture, then the hover picture loaded from the scene")
    }

    @Test
    fun aCheckOfACanvasWithNoImagesReadsNothing() = runTest {
        val document = SceneDocument(nodes = listOf(SceneNode("score", components = listOf(SceneCanvasElement(text = "0")))))

        assertEquals(emptyList(), checkCanvasImages(document, AssetSource { error("nothing to read") }))
    }

    @Test
    fun aCanvasWithNoImagesNeedsNone() {
        assertFalse(hasCanvasImages(SceneDocument(nodes = listOf(SceneNode("score", components = listOf(SceneCanvasElement(text = "0")))))))
    }

    private companion object {
        val NOT_AN_IMAGE: ByteArray = "this is not a picture".encodeToByteArray()

        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        /** A 4 x 4 opaque PNG of [rgb]. */
        fun solidPng(rgb: Int): ByteArray = ByteArrayOutputStream().also { out ->
            val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 4) for (x in 0 until 4) image.setRGB(x, y, rgb or OPAQUE)
            ImageIO.write(image, "png", out)
        }.toByteArray()

        const val OPAQUE = 0xFF shl 24
    }
}
