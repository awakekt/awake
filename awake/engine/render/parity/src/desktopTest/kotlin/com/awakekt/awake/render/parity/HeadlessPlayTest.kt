/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.engine.window.DesktopRunLimits
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.vulkan.application.playHeadless
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A game played as it ships, with no window: what a CI job runs to check that a build, such as an
 * obfuscated release, starts, draws and reads back. The sample's 2D scene draws a green tile floor,
 * so the frame has one; an engine that can't start fails with why, rather than passing as a short run.
 */
class HeadlessPlayTest {
    @Test
    fun aGamePlayedWithNoWindowDrawsItsScene() {
        val frame = VulkanEngine(app(), EngineShowcaseRenderPlan).playHeadless(WIDTH, HEIGHT, FRAMES)

        val green = greenPixels(frame)
        println("Headless play on Vulkan: ${frame.width}x${frame.height}, green=$green")
        assertEquals(WIDTH to HEIGHT, frame.width to frame.height)
        assertTrue(green > FLOOR_PIXELS, "the tile floor must be drawn; green=$green")
    }

    @Test
    fun aCaptureRunWritesTheLastFrameAsAPng() {
        val app = app()
        val png = File(createTempDirectory("capture").toFile(), "frames/last.png")

        runVulkanDesktopGame(app, VulkanEngine(app, EngineShowcaseRenderPlan), limits = DesktopRunLimits(frames = FRAMES, capture = png.path))

        val image = ImageIO.read(png)
        val colours = (0 until image.height step SAMPLE).flatMap { y -> (0 until image.width step SAMPLE).map { x -> image.getRGB(x, y) } }.toSet()
        println("Capture on Vulkan: ${image.width}x${image.height}, ${colours.size} sampled colours")
        assertEquals(app.windowConfig.width to app.windowConfig.height, image.width to image.height, "a capture is the window's size")
        assertTrue(colours.size > 1, "a drawn frame isn't one flat colour")
    }

    @Test
    fun anEngineThatCannotStartFailsWithWhy() {
        val engine = object : VulkanEngine(app(), EngineShowcaseRenderPlan) {
            override suspend fun createBackendResources(window: Any): BackendResources = error("no Vulkan driver here")
        }

        val error = assertFailsWith<IllegalStateException> { engine.playHeadless(WIDTH, HEIGHT, FRAMES) }

        assertTrue("no Vulkan driver here" in error.message.orEmpty(), "the failure says why: ${error.message}")
    }

    private fun app() = engineShowcaseApp(initialShowcaseId = "runtime-2d")

    private fun greenPixels(frame: TextureAsset): Int =
        (0 until frame.width * frame.height).count { pixel ->
            val at = pixel * RGBA
            val (r, g, b) = (0..2).map { frame.data[at + it].toInt() and BYTE }
            g > r + DOMINANCE && g > b + DOMINANCE
        }

    private companion object {
        const val WIDTH = 384
        const val HEIGHT = 224
        const val FRAMES = 3
        const val FLOOR_PIXELS = 10_000
        const val SAMPLE = 16
        const val RGBA = 4
        const val BYTE = 0xFF
        const val DOMINANCE = 50
    }
}
