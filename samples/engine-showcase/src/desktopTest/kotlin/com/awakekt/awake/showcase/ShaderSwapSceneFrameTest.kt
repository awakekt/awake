/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.logging.Log
import com.awakekt.awake.core.logging.LogLevel
import com.awakekt.awake.core.logging.LogRingBuffer
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.lifecycle.AwakeAppLifecycle
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shader hot reload sample, headless: pressing L in the running showcase swaps the lit shader
 * for its brighter variant and the frame changes; pressing K tries a broken variant, which is
 * logged, and the frame stays exactly as it was.
 *
 * Every frame uses a zero delta, so nothing moves and any pixel change is the shader's.
 */
class ShaderSwapSceneFrameTest {
    private val log = LogRingBuffer()

    @AfterTest
    fun resetLog() = Log.reset()

    @Test
    fun keysSwapTheLitShaderAndABrokenVariantIsRefused() = runBlocking {
        Log.reset()
        Log.install(log)
        val app = engineShowcaseApp(initialShowcaseId = "point-lights")
        val renderer = HeadlessPlanEngine(app, EngineShowcaseRenderPlan).boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            app.ready(renderer)
            suspend fun frame(): ByteArray {
                repeat(FRAMES) { app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat()) }
                return renderer.readPresentedPixels().data.copyOf()
            }
            val shipped = frame()

            app.press(ShowcaseShaderSwap.TOGGLE_KEY)
            val brighter = frame()
            PixelMap(WIDTH, HEIGHT, brighter.copyOf()).writePng(File(CAPTURE_PATH))
            assertTrue(changedPixels(shipped, brighter) > MINIMUM_CHANGED_PIXELS, "L did not change the frame")

            app.press(ShowcaseShaderSwap.BROKEN_KEY)
            assertEquals(0, changedPixels(brighter, frame()), "a refused variant changed the frame")
            assertTrue(
                log.snapshot().any { it.level == LogLevel.Warn && it.tag == "showcase-shaders" },
                "the refused variant was not logged",
            )

            app.press(ShowcaseShaderSwap.TOGGLE_KEY)
            assertEquals(0, changedPixels(shipped, frame()), "L again did not restore the shipped shader")
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    private fun AwakeAppLifecycle.press(key: Key) {
        input.setKeyDown(key, down = true)
        input.updateSnapshot()
        update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
        input.setKeyDown(key, down = false)
        input.updateSnapshot()
    }

    private fun changedPixels(before: ByteArray, after: ByteArray): Int =
        (0 until WIDTH * HEIGHT).count { i ->
            val offset = i * 4
            before[offset] != after[offset] || before[offset + 1] != after[offset + 1] || before[offset + 2] != after[offset + 2]
        }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val FRAMES = 4
        const val MINIMUM_CHANGED_PIXELS = 1_000
        const val CAPTURE_PATH = "build/reports/render-captures/point-lights-brighter-ambient.png"
    }
}
