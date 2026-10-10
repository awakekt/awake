/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanvasImagesTest {
    @Test
    fun aPlayedProjectDrawsTheImagesItsCanvasNames() = runTest {
        val project = loadProject(PROJECT)
        val game = app { scene("play") { runProject(project) } }
        game.ready(DocsRenderer())
        game.update(FRAME, WIDTH, HEIGHT)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        assertEquals(setOf("ui/frame.png"), runtime.canvasImages.keys)
        assertEquals(3 to 2, runtime.canvasImages.getValue("ui/frame.png").let { it.width to it.height })
        assertTrue(runtime.uiPrimitives.any { it is UiDrawPrimitive.Texture }, "the panel draws its frame")
    }

    private companion object {
        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        val PROJECT = AssetSource { path ->
            runCatching {
                when (path.value) {
                    "awake.project.json" ->
                        """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""
                            .encodeToByteArray()
                    "scenes/main.scene.json" -> """
                        { "version": 1, "name": "hud", "nodes": [
                          { "name": "Frame", "components": [ { "component": "canvas_element", "kind": "Panel",
                            "style": { "image": { "path": "ui/frame.png", "sliceLeft": 1, "sliceRight": 1 } } } ] }
                        ] }
                    """.trimIndent().encodeToByteArray()
                    "ui/frame.png" -> PNG
                    else -> error("no ${path.value}")
                }
            }
        }
    }
}
