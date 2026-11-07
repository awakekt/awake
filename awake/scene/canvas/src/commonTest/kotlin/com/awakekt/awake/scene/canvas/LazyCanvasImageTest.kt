/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.assets.AssetImages
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.io.AssetSource
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasImage
import com.awakekt.awake.ui.canvas.CanvasStyle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** An image named lazily is read the first time an element drawing it shows, not when the scene opens. */
class LazyCanvasImageTest {
    private val reads = mutableListOf<String>()

    // Each read is counted; the bytes are only ever decoded by the test's own decoder below.
    private val files = AssetSource { path ->
        reads += path.value
        if (path.value == ICONS) Result.success(ByteArray(ImageBitmap.BYTES_PER_PIXEL) { -1 }) else Result.failure(NoSuchElementException(path.value))
    }

    @Test
    fun loadingTheSceneLeavesOutAnImageEveryElementNamesLazily() = runTest {
        val bag = SceneCanvasElement(style = CanvasStyle(image = CanvasImage(ICONS, lazy = true)))
        val hud = SceneCanvasElement(style = CanvasStyle(image = CanvasImage("hud.png")))
        val document = SceneDocument(nodes = listOf(SceneNode("bag", components = listOf(bag)), SceneNode("hud", components = listOf(hud))))

        loadCanvasImages(document, files)

        assertEquals(listOf("hud.png"), reads, "the lazy atlas is not read with the scene")
        reads.clear()
        assertEquals(listOf("hud.png"), checkCanvasImages(document, files))
        assertEquals(listOf(ICONS, "hud.png"), reads, "a host that draws nothing still checks every image")
    }

    @Test
    fun anImageNamedEagerlyAnywhereStillLoadsWithTheScene() = runTest {
        val lazy = SceneCanvasElement(style = CanvasStyle(image = CanvasImage(ICONS, lazy = true)))
        val eager = SceneCanvasElement(style = CanvasStyle(fillImage = CanvasImage(ICONS)))
        val document = SceneDocument(nodes = listOf(SceneNode("bag", components = listOf(lazy)), SceneNode("bar", components = listOf(eager))))

        loadCanvasImages(document, files)

        assertEquals(listOf(ICONS), reads)
    }

    @Test
    fun aHiddenWindowsImageIsReadOnlyOnceTheWindowShowsAndThenDraws() = runTest {
        val world = World()
        val window = CanvasElement().apply { kind = CanvasElementKind.Panel; width = 40f; height = 40f; visible = false }
        val windowEntity = world.create().also { world.add(it, window) }
        world.create().also {
            world.add(it, Transform(parent = windowEntity))
            world.add(it, CanvasElement().apply { kind = CanvasElementKind.Image; width = 8f; height = 8f; style = CanvasStyle(image = CanvasImage(ICONS, lazy = true)) })
        }
        val images = AssetImages(files, backgroundScope) { bytes -> ImageBitmap(1, 1, bytes) }
        val host = ComposeHost()
        fun textures() = host.frame(FrameInput(100, 100)) { SceneCanvas(world, images = images.asMap()) }
            .primitives.filterIsInstance<UiDrawPrimitive.Texture>()

        repeat(3) { assertEquals(emptyList(), textures()) }
        runCurrent()
        assertEquals(emptyList(), reads, "nothing is read while the window is closed")

        window.visible = true
        assertEquals(emptyList(), textures(), "the frame that shows it draws without the picture")
        runCurrent()
        assertEquals(listOf(ICONS), reads)
        assertTrue(textures().isNotEmpty(), "the picture draws once it has decoded")
        textures()
        assertEquals(listOf(ICONS), reads, "read once")
    }

    private companion object {
        const val ICONS = "ui/icons.png"
    }
}
