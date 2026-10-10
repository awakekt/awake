/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.core.io.AssetSource
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
    }
}
