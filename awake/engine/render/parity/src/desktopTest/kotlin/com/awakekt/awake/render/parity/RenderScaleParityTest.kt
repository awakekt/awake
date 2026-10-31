/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.webgpu.HeadlessWebGpuEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `Renderer.renderScale` on the presented frame: the shadow scene under a strip of one-pixel UI
 * stripes, drawn through `Renderer.draw` and read back from the surface.
 *
 * At scale 1 the frame is the unscaled one byte for byte on both backends, also after a frame at 0.5.
 * At 0.5 on WebGPU the scene renders into a 64-square target that a bilinear upscale copies to the
 * 128-square surface, and the UI draws over it at full resolution: every stripe pixel is the scale-1
 * pixel, while the scene matches within [SCENE_TOLERANCE] away from its edges and visibly softens on
 * them. Vulkan does not implement the scale yet (#1181): setting it leaves 1 and the frame as it was.
 *
 * Measured on lavapipe (wgpu-native over Mesa): at 0.5, 0 of the 384 stripe pixels differ from
 * scale 1, the worst scene pixel away from edges is 1/255 off, and 530 edge pixels change.
 */
class RenderScaleParityTest {
    @Test
    fun scaleOneIsTheUnscaledFrameOnWebGpu() = withWebGpu { frames -> verifyScaleOne(HeadlessUiBackend.WebGpu, frames) }

    @Test
    fun scaleOneIsTheUnscaledFrameOnVulkan() = withVulkan { frames -> verifyScaleOne(HeadlessUiBackend.Vulkan, frames) }

    /**
     * Negative controls. With the scale ignored, the half frame is the full one: 0 edge pixels
     * change, under [MIN_EDGE_CHANGED] (measured with `SceneScaleTarget.resolve` returning null).
     * A UI drawn into the scaled target would lose the one-pixel stripes: sampling 32 alternating
     * columns at half resolution leaves 16 texels, so the upscale reads grey across the strip and
     * all 384 stripe pixels differ (computed from the sampling, not run).
     */
    @Test
    fun halfScaleUpscalesTheSceneUnderAFullResolutionUiOnWebGpu() = withWebGpu { frames ->
        val full = frames.frame(1f)
        val half = frames.frame(HALF)
        assertEquals(SIZE / 2, frames.sceneWidth, "the scene target is half the surface")
        assertEquals(SIZE, frames.outputWidth, "the surface keeps its size")
        assertEquals(SIZE * SIZE * 4, half.size, "the UI draws into, and the frame presents, the full-resolution surface")
        write("render-scale-full", full)
        write("render-scale-half", half)

        val uiDiffering = stripePixels().count { (x, y) -> !full.sameAt(half, x, y) }
        val edges = edgeMask(full)
        val awayWorst = (0 until SIZE * SIZE).filter { !edges[it] && !inStripes(it % SIZE, it / SIZE) }
            .maxOf { full.channelDelta(half, it) }
        val edgeChanged = (0 until SIZE * SIZE).count { edges[it] && full.channelDelta(half, it) > 0 }
        println("render scale 0.5: ui differing=$uiDiffering, worst away from edges=$awayWorst/255, edge pixels changed=$edgeChanged")

        assertEquals(0, uiDiffering, "the UI is drawn at full resolution over the upscaled scene")
        assertTrue(awayWorst <= SCENE_TOLERANCE, "the scene away from edges is within $SCENE_TOLERANCE/255: $awayWorst")
        assertTrue(edgeChanged >= MIN_EDGE_CHANGED, "the scene was rendered at half resolution: $edgeChanged edge pixels changed")
    }

    @Test
    fun vulkanIgnoresTheScaleUntilItImplementsIt() = withVulkan { frames ->
        val full = frames.frame(1f)
        val half = frames.frame(HALF)
        assertEquals(1f, frames.renderer.renderScale, "Vulkan keeps its scene at the surface's size")
        assertEquals(SIZE, frames.sceneWidth)
        assertContentEquals(full, half)
    }

    private fun verifyScaleOne(backend: HeadlessUiBackend, frames: Frames) {
        val untouched = frames.frame(scale = null)
        val untouchedDraws = frames.renderer.frameStats?.drawCalls
        frames.frame(HALF)
        val restored = frames.frame(1f)
        write("render-scale-one-$backend", restored)
        assertEquals(SIZE, frames.sceneWidth, "$backend: at scale 1 the scene target is the surface")
        assertEquals(untouchedDraws, frames.renderer.frameStats?.drawCalls, "$backend: no upscale draw at scale 1")
        assertContentEquals(untouched, restored, "$backend: scale 1 draws the frame drawn with no scale")
    }

    /** A renderer presenting [SIZE]-square frames of the shadow scene under the UI stripes. */
    private class Frames(val renderer: Renderer, private val present: () -> Unit) {
        private val ground = renderer.createMesh(plane(GROUND_HALF, y = 0f))
        private val caster = renderer.createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f))
        private val material = renderer.createMaterial(LitShadowUniformLayout)
        val sceneWidth: Int get() = renderer.surfaceWidth
        val outputWidth: Int get() = renderer.outputWidth

        /** Draws a frame at [scale] (or with none set) and reads it from the surface. */
        fun frame(scale: Float?): ByteArray {
            scale?.let { renderer.renderScale = it }
            renderer.drawUi(stripes())
            renderer.draw(renderer.compileShadowScene(listOf(RenderDrawCommand(ground, material), RenderDrawCommand(caster, material))))
            return runBlocking { renderer.readPresentedPixels() }.data.also { present() }
        }

        fun destroy() {
            renderer.waitIdle()
            ground.destroy()
            caster.destroy()
            material.destroy()
        }
    }

    private fun withWebGpu(block: (Frames) -> Unit) = runBlocking {
        val engine = HeadlessWebGpuEngine(plan = EngineShowcaseRenderPlan)
        val frames = Frames(engine.boot(HeadlessSurface(SIZE, SIZE)), engine::present)
        try {
            block(frames)
        } finally {
            frames.destroy()
            engine.destroy()
        }
    }

    private fun withVulkan(block: (Frames) -> Unit) {
        val game = app { window { size(SIZE, SIZE) } }
        openRuntimeSession(HeadlessUiBackend.Vulkan, game, SIZE, SIZE).use { session ->
            val frames = Frames(session.renderer) {}
            try {
                block(frames)
            } finally {
                frames.destroy()
            }
        }
    }

    private companion object {
        const val SIZE = SCENE_SIZE
        const val HALF = 0.5f
        const val GROUND_HALF = 2f
        const val CASTER_HALF = 0.5f
        const val CASTER_Y = 0.6f
        const val STRIPES_X = 8
        const val STRIPES_Y = 8
        const val STRIPES_WIDTH = 32
        const val STRIPES_HEIGHT = 12
        const val SCENE_TOLERANCE = 4
        const val MIN_EDGE_CHANGED = 100
        const val EDGE_LUMA = 24
        const val BYTE = 0xFF
        val REPORT_DIR = File("build/reports/render-scale")

        /** Opaque one-pixel black and white columns: what an upscale could never keep. */
        fun stripes(): List<UiDrawPrimitive> = buildList {
            add(UiDrawPrimitive.Quad(STRIPES_X.toFloat(), STRIPES_Y.toFloat(), STRIPES_WIDTH.toFloat(), STRIPES_HEIGHT.toFloat(), Color.Black))
            for (column in 0 until STRIPES_WIDTH step 2) {
                add(UiDrawPrimitive.Quad((STRIPES_X + column).toFloat(), STRIPES_Y.toFloat(), 1f, STRIPES_HEIGHT.toFloat(), Color.White))
            }
        }

        fun inStripes(x: Int, y: Int) = x in STRIPES_X until STRIPES_X + STRIPES_WIDTH && y in STRIPES_Y until STRIPES_Y + STRIPES_HEIGHT

        fun stripePixels() = (STRIPES_Y until STRIPES_Y + STRIPES_HEIGHT).flatMap { y -> (STRIPES_X until STRIPES_X + STRIPES_WIDTH).map { x -> x to y } }

        fun ByteArray.channel(pixel: Int, c: Int) = this[pixel * 4 + c].toInt() and BYTE

        fun ByteArray.channelDelta(other: ByteArray, pixel: Int) = (0..2).maxOf { kotlin.math.abs(channel(pixel, it) - other.channel(pixel, it)) }

        fun ByteArray.sameAt(other: ByteArray, x: Int, y: Int) = channelDelta(other, y * SIZE + x) == 0

        /** Pixels within two of a luma step over [EDGE_LUMA] in [frame]: where an upscale may differ. */
        fun edgeMask(frame: ByteArray): BooleanArray {
            fun luma(x: Int, y: Int): Int {
                val p = y.coerceIn(0, SIZE - 1) * SIZE + x.coerceIn(0, SIZE - 1)
                return (frame.channel(p, 0) * 2126 + frame.channel(p, 1) * 7152 + frame.channel(p, 2) * 722) / 10000
            }
            val step = BooleanArray(SIZE * SIZE) { p ->
                val x = p % SIZE
                val y = p / SIZE
                maxOf(kotlin.math.abs(luma(x + 1, y) - luma(x, y)), kotlin.math.abs(luma(x, y + 1) - luma(x, y))) > EDGE_LUMA
            }
            return BooleanArray(SIZE * SIZE) { p ->
                val x = p % SIZE
                val y = p / SIZE
                (-2..2).any { dy -> (-2..2).any { dx -> step.getOrElse((y + dy).coerceIn(0, SIZE - 1) * SIZE + (x + dx).coerceIn(0, SIZE - 1)) { false } } }
            }
        }

        fun write(name: String, frame: ByteArray) {
            PixelMap(SIZE, SIZE, frame.copyOf()).writePng(File(REPORT_DIR.apply { mkdirs() }, "$name.png"))
        }
    }
}
