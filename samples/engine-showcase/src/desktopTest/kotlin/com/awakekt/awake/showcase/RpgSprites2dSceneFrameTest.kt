/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.scene.scene2d.Sprite
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Both named rows must reach the real renderer and change together on scene time. */
class RpgSprites2dSceneFrameTest {
    @Test
    fun theRangerAndEnemyRenderAndAnimateFromTheirOwnAtlasRows() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "rpg-sprites-2d")
        val renderer = HeadlessPlanEngine(app, EngineShowcaseRenderPlan).boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            app.ready(renderer)
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val idle = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, idle.copyOf()).writePng(File("build/reports/render-captures/rpg-sprites-2d-idle.png"))
            assertCharacterColors(idle)
            app.update(0.25f, WIDTH.toFloat(), HEIGHT.toFloat())
            val next = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, next.copyOf()).writePng(File("build/reports/render-captures/rpg-sprites-2d-next.png"))
            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            val frames = buildList { runtime.world.queryEach<Sprite> { _, sprite -> add(sprite.frame) } }.sorted()
            assertEquals(listOf(1, 5), frames)
            for (region in listOf(heroRegion, enemyRegion)) {
                val changed = region.count { pixel -> (0 until 3).any { channel -> idle[pixel * 4 + channel] != next[pixel * 4 + channel] } }
                assertTrue(changed > MIN_PIXELS, "An idle row changed only $changed pixels")
            }
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    private fun assertCharacterColors(pixels: ByteArray) {
        val teal = heroRegion.count { pixel ->
            val r = pixels[pixel * 4].toInt() and 0xff
            val g = pixels[pixel * 4 + 1].toInt() and 0xff
            val b = pixels[pixel * 4 + 2].toInt() and 0xff
            g > r + COLOR_MARGIN && b > r + COLOR_MARGIN
        }
        val purple = enemyRegion.count { pixel ->
            val r = pixels[pixel * 4].toInt() and 0xff
            val g = pixels[pixel * 4 + 1].toInt() and 0xff
            val b = pixels[pixel * 4 + 2].toInt() and 0xff
            r > g + COLOR_MARGIN && b > g + COLOR_MARGIN
        }
        assertTrue(teal > MIN_PIXELS, "The ranger's teal cloak did not reach the frame: $teal pixels")
        assertTrue(purple > MIN_PIXELS, "The thorn beast's purple body did not reach the frame: $purple pixels")
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val COLOR_MARGIN = 20
        const val MIN_PIXELS = 100
        val heroRegion = (HEIGHT / 3 until HEIGHT * 4 / 5).flatMap { y -> (WIDTH / 5 until WIDTH / 2).map { x -> y * WIDTH + x } }
        val enemyRegion = (HEIGHT / 3 until HEIGHT * 4 / 5).flatMap { y -> (WIDTH / 2 until WIDTH * 4 / 5).map { x -> y * WIDTH + x } }
    }
}
