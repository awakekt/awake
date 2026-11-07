/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.assets.AssetImages
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

/** A played project's UI asks for images by path through the runtime's per-scene [AssetImages]. */
class SceneAssetImagesTest {
    @Test
    fun theUiSharesTheImagesTheCanvasNames() = runTest {
        val reads = mutableListOf<String>()
        val project = loadProject(files(reads))
        val game = play(project)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()

        val images = assertNotNull(runtime.assetImages, "made on the first frame of a project with files")

        assertSame(runtime.canvasImages.getValue("ui/frame.png"), images["ui/frame.png"], "the canvas's own decode, at once")
        assertEquals(1, reads.count { it == "ui/frame.png" }, "read once, for the canvas")
        project.close()
    }

    @Test
    fun anImageTheUiAskedForIsFreedOnASceneSwitch() = runTest {
        val project = loadProject(files(mutableListOf()))
        val game = play(project)
        val runtime = game.requireService<SceneAppLifecycleRuntime>()
        val images = assertNotNull(runtime.assetImages)
        game.until("the icon loads") { images["ui/icon.png"] != null }
        assertEquals(3 to 2, images["ui/icon.png"]?.let { it.width to it.height })

        game.requireService<SceneSwitch>().request(LEVEL)
        game.until("the level plays") { project.scene.name == "level" }

        assertSame(images, runtime.assetImages, "one cache for the runtime, started again")
        assertEquals(1, images.size, "only the level's canvas image is left")
        assertSame(runtime.canvasImages.getValue("ui/level.png"), images["ui/level.png"])
        assertNull(images["ui/icon.png"], "the menu's icon was freed, and is loaded again when asked for")
        project.close()
    }

    @Test
    fun aMissingPathIsNullAndTheFramesGoOn() = runTest {
        val project = loadProject(files(mutableListOf()))
        val game = play(project)
        val images = assertNotNull(game.requireService<SceneAppLifecycleRuntime>().assetImages)

        game.until("the read fails") { images.failure("ui/missing.png") != null || images["ui/missing.png"] != null }

        assertNull(images["ui/missing.png"])
        game.update(FRAME, WIDTH, HEIGHT)
        project.close()
    }

    private fun play(project: LoadedProject): AwakeAppLifecycle {
        val game = app { scene("play") { runProject(project) } }
        game.ready(DocsRenderer())
        game.update(FRAME, WIDTH, HEIGHT)
        return game
    }

    private suspend fun AwakeAppLifecycle.until(what: String, done: () -> Boolean) {
        repeat(ATTEMPTS) {
            update(FRAME, WIDTH, HEIGHT)
            if (done()) return
            withContext(Dispatchers.Default) { delay(WAIT_MILLIS) }
        }
        fail("Timed out waiting until $what")
    }

    private fun files(reads: MutableList<String>) = AssetSource { path ->
        reads += path.value
        runCatching {
            when (path.value) {
                "awake.project.json" -> MANIFEST.encodeToByteArray()
                "scenes/main.scene.json" -> scene("menu", "ui/frame.png").encodeToByteArray()
                LEVEL -> scene("level", "ui/level.png").encodeToByteArray()
                "ui/frame.png", "ui/icon.png", "ui/level.png" -> PNG
                else -> error("no ${path.value}")
            }
        }
    }

    private companion object {
        const val LEVEL = "scenes/level.scene.json"
        const val ATTEMPTS = 200
        const val WAIT_MILLIS = 10L
        const val MANIFEST =
            """{"formatVersion":1,"id":"com.example.harbor-town","name":"Harbor Town","version":"1.0.0","entryScene":"scenes/main.scene.json"}"""

        val PNG: ByteArray = ByteArrayOutputStream().also {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", it)
        }.toByteArray()

        fun scene(name: String, image: String) = """
            { "version": 1, "name": "$name", "nodes": [
              { "name": "Frame", "components": [ { "component": "canvas_element", "kind": "Panel",
                "style": { "image": { "path": "$image" } } } ] }
            ] }
        """.trimIndent()
    }
}
