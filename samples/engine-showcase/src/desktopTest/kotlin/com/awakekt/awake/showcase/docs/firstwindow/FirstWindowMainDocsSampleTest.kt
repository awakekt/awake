/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.docs.firstwindow

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.showcase.docs.GameRenderPlan
import com.awakekt.awake.showcase.docs.firstWindow
import com.awakekt.awake.vulkan.application.VulkanEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
// --8<-- [start:main]
import com.awakekt.awake.vulkan.application.runVulkanDesktopGame

fun main() = runVulkanDesktopGame(firstWindow(), GameRenderPlan)
// --8<-- [end:main]

/**
 * Runs the tutorial's app and plan on the Vulkan backend without a window, the way
 * `runVulkanDesktopGame` would, and checks a frame reaches the screen.
 */
class FirstWindowMainDocsSampleTest {

    @Test
    fun theAppRendersFramesWithThePlan() = runBlocking {
        val app = firstWindow()
        val frame = renderHeadless(app, GameRenderPlan, frames = 2)

        assertEquals(WIDTH * HEIGHT * 4, frame.size)
    }
}

/** A [VulkanEngine] that draws into an offscreen surface instead of a window. */
internal class HeadlessDocsEngine(app: AwakeAppLifecycle, plan: RenderPlan) : VulkanEngine(app, plan) {
    suspend fun boot(): Renderer = createBackendResources(HeadlessSurface(WIDTH, HEIGHT)).renderer
}

/** Renders [frames] frames of [app] at 60 fps and returns the last one as RGBA bytes. */
internal suspend fun renderHeadless(app: AwakeAppLifecycle, plan: RenderPlan, frames: Int): ByteArray {
    val renderer = HeadlessDocsEngine(app, plan).boot()
    try {
        app.ready(renderer)
        repeat(frames) { app.update(1f / 60f, WIDTH.toFloat(), HEIGHT.toFloat()) }
        return renderer.readPresentedPixels().data.copyOf()
    } finally {
        app.dispose()
        renderer.destroy()
    }
}

internal const val WIDTH = 480
internal const val HEIGHT = 270
