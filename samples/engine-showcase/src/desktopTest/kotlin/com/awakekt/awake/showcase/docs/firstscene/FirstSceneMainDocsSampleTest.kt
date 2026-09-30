/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.firstscene

import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.docs.GameRenderPlan
import com.awakekt.awake.showcase.docs.firstScene
import com.awakekt.awake.showcase.docs.firstwindow.HEIGHT
import com.awakekt.awake.showcase.docs.firstwindow.WIDTH
import com.awakekt.awake.showcase.docs.firstwindow.renderHeadless
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
// --8<-- [start:main]
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() = runVulkanDesktopGame(firstScene(), GameRenderPlan)
// --8<-- [end:main]

/** Renders the "Your first scene" app on Vulkan without a window: the cube shows, and it turns. */
class FirstSceneMainDocsSampleTest {

    @Test
    fun theCubeIsDrawnAndTurns() = runBlocking {
        val early = renderHeadless(firstScene(), GameRenderPlan, frames = 1)
        val later = renderHeadless(firstScene(), GameRenderPlan, frames = 30)
        // The page's figure is this frame.
        PixelMap(WIDTH, HEIGHT, later.copyOf()).writePng(File("build/reports/render-captures/first-scene.png"))

        assertTrue(litPixels(early) > MIN_CUBE_PIXELS, "The cube was not drawn")
        assertFalse(early.contentEquals(later), "Half a second of frames left the cube where it was")
    }

    /** Pixels that differ from the top-left one, which is empty background in this shot. */
    private fun litPixels(rgba: ByteArray): Int =
        (0 until rgba.size / 4).count { pixel ->
            (0 until 3).any { channel -> rgba[pixel * 4 + channel] != rgba[channel] }
        }

    private companion object {
        const val MIN_CUBE_PIXELS = 2_000
    }
}
