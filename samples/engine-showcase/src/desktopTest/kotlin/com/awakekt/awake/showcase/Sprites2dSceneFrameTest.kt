/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase

import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.showcase.app.engineShowcaseApp
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real sprite art and different atlas cells must reach the presented frame, with no transform motion. */
class Sprites2dSceneFrameTest {
    @Test
    fun theSpriteAtlasDrawsAndItsBlinkChangesThePresentedFrame() = runBlocking {
        val app = engineShowcaseApp(initialShowcaseId = "sprites-2d")
        val renderer = HeadlessPlanEngine(app, EngineShowcaseRenderPlan).boot(HeadlessSurface(WIDTH, HEIGHT))
        try {
            app.ready(renderer)
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val runtime = app.requireService<SceneAppLifecycleRuntime>()
            runtime.holdFrame(0)
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val idle = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, idle.copyOf()).writePng(File("build/reports/render-captures/sprites-2d-idle.png"))

            val goldPixels = spriteRegion.count { pixel ->
                val offset = pixel * CHANNELS
                val r = idle[offset].toInt() and 0xff
                val g = idle[offset + 1].toInt() and 0xff
                val b = idle[offset + 2].toInt() and 0xff
                r > g + COLOR_MARGIN && g > b + COLOR_MARGIN
            }
            assertTrue(goldPixels > MIN_SPRITE_PIXELS, "The firefly's copper/gold texture did not reach the frame: $goldPixels pixels.")

            runtime.holdFrame(2)
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val blink = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, blink.copyOf()).writePng(File("build/reports/render-captures/sprites-2d-blink.png"))
            val changed = spriteRegion.count { pixel ->
                val offset = pixel * CHANNELS
                idle[offset] != blink[offset] || idle[offset + 1] != blink[offset + 1] || idle[offset + 2] != blink[offset + 2]
            }
            assertTrue(changed > MIN_SPRITE_PIXELS, "Switching to the blink cell changed only $changed pixels.")

            runtime.world.queryEach(TextureAnimation::class, MeshRenderer::class) { entity, _, mesh ->
                assertTrue(mesh.transparent, "sprite renderers must retain their authored transparency")
                runtime.world.add(entity, mesh.copy(visible = false))
            }
            app.update(0f, WIDTH.toFloat(), HEIGHT.toFloat())
            val background = renderer.readPresentedPixels().data.copyOf()
            PixelMap(WIDTH, HEIGHT, background.copyOf()).writePng(File("build/reports/render-captures/sprites-2d-background.png"))
            val emptyMarginOffset = (HEIGHT * 7 / 25 * WIDTH + WIDTH / 2) * CHANNELS
            val marginDrift = (0 until 3).maxOf { channel ->
                abs((idle[emptyMarginOffset + channel].toInt() and 0xff) - (background[emptyMarginOffset + channel].toInt() and 0xff))
            }
            assertTrue(marginDrift <= 2, "An empty atlas margin darkened the backdrop by $marginDrift channel levels.")
        } finally {
            app.dispose()
            renderer.destroy()
        }
    }

    private fun SceneAppLifecycleRuntime.holdFrame(frame: Int) {
        world.queryEach<TextureAnimation> { entity, animation ->
            world.add(entity, animation.copy(firstFrame = frame, frameCount = 1, framesPerSecond = 0f))
        }
    }

    private companion object {
        const val WIDTH = 960
        const val HEIGHT = 540
        const val CHANNELS = 4
        const val COLOR_MARGIN = 20
        const val MIN_SPRITE_PIXELS = 100

        // Isolate the middle sprite from the switcher and stats UI.
        val spriteRegion = (HEIGHT / 3 until HEIGHT * 2 / 3).flatMap { y ->
            (WIDTH * 2 / 5 until WIDTH * 3 / 5).map { x -> y * WIDTH + x }
        }
    }
}
