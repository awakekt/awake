/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.particles

import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadParticleSpritesTest {
    @Test
    fun everyNamedSpriteIsDecodedOnceAndAMissingOneIsLeftOut() = runTest {
        val reads = mutableListOf<String>()
        val assets = AssetSource { path ->
            reads += path.value
            if (path.value == "dust.png") Result.success(PNG) else Result.failure(IllegalStateException("missing"))
        }
        val document = SceneDocument(
            nodes = listOf(
                SceneNode("a", components = listOf(SceneParticleEmitter(texture = "dust.png"))),
                SceneNode(
                    "b",
                    children = listOf(
                        SceneNode("c", components = listOf(SceneParticleEmitter(texture = "dust.png"))),
                        SceneNode("d", components = listOf(SceneParticleEmitter(texture = "gone.png"))),
                    ),
                ),
            ),
        )

        val sprites = loadParticleSprites(document, assets)

        assertEquals(setOf("dust.png"), sprites.keys)
        assertEquals(3 to 2, sprites.getValue("dust.png").let { it.width to it.height })
        assertEquals(listOf("dust.png", "gone.png"), reads, "each path read once")
    }

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()
    }
}
