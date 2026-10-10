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
    fun aCanvasWithNoImagesNeedsNone() {
        assertFalse(hasCanvasImages(SceneDocument(nodes = listOf(SceneNode("score", components = listOf(SceneCanvasElement(text = "0")))))))
    }

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
