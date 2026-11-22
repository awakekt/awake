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
import com.awakekt.awake.render.renderer.GpuFramePart
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SceneUpscaling
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.webgpu.HeadlessWebGpuEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scale-up sharpening parity on Vulkan and WebGPU at 50% and 75% render scale (#1830).
 *
 * Verifies that both [com.awakekt.awake.render.renderer.SceneUpscaleMode.Sharpen] (5-tap CAS) and
 * [com.awakekt.awake.render.renderer.SceneUpscaleMode.UpscaleAndSharpen] (12-tap edge-thinning CAS):
 * - recover edge contrast relative to the plain bilinear stretch ([SceneUpscaling.Off]);
 * - leave flat interior regions invariant (no ringing or DC shift);
 * - keep the full-resolution UI stripes byte-for-byte identical (sharpening runs before UI);
 * - add zero extra render passes on Vulkan.
 */
class UpscaleSharpenParityTest {
    @Test
    fun sharpenRecoversEdgeContrastAt50And75PercentOnWebGpu() = withWebGpu { frames ->
        verifyScale(HeadlessUiBackend.WebGpu, frames, HALF, "50")
        verifyScale(HeadlessUiBackend.WebGpu, frames, THREE_QUARTERS, "75")
    }

    @Test
    fun sharpenRecoversEdgeContrastAt50And75PercentOnVulkan() = withVulkan { frames ->
        verifyScale(HeadlessUiBackend.Vulkan, frames, HALF, "50")
        verifyScale(HeadlessUiBackend.Vulkan, frames, THREE_QUARTERS, "75")
    }

    private fun verifyScale(backend: HeadlessUiBackend, frames: Frames, scale: Float, label: String) {
        val full = frames.frame(1f, SceneUpscaling.Off)
        val edges = edgeMask(full)
        val plain = frames.frame(scale, SceneUpscaling.Off)
        val plainPasses = frames.renderer.frameRenderPasses
        val sharpen = frames.frame(scale, SceneUpscaling.sharpen(STRENGTH))
        val sharpenPasses = frames.renderer.frameRenderPasses
        val upscale = frames.frame(scale, SceneUpscaling.upscaleAndSharpen(STRENGTH))
        val upscalePasses = frames.renderer.frameRenderPasses
        val timing = frames.renderer.frameGpuTiming

        write("upscale-$label-plain-$backend", plain)
        write("upscale-$label-sharpen-$backend", sharpen)
        write("upscale-$label-cas-$backend", upscale)

        val plainContrast = meanEdgeGradient(plain, edges)
        val sharpenContrast = meanEdgeGradient(sharpen, edges)
        val upscaleContrast = meanEdgeGradient(upscale, edges)
        val flat = flatMask(plain)
        val sharpenFlatWorst = maxFlatDelta(plain, sharpen, flat)
        val upscaleFlatWorst = maxFlatDelta(plain, upscale, flat)
        val sharpenUiDiff = stripePixels().count { (x, y) -> !full.sameAt(sharpen, x, y) }
        val upscaleUiDiff = stripePixels().count { (x, y) -> !full.sameAt(upscale, x, y) }

        println(
            "$backend upscale@$label%: edgeGradient plain=${plainContrast.fmt()} sharpen=${sharpenContrast.fmt()} " +
                "cas=${upscaleContrast.fmt()} flatWorst=($sharpenFlatWorst,$upscaleFlatWorst) " +
                "passes=($plainPasses,$sharpenPasses,$upscalePasses) gpuUpscaleMs=${timing?.partMs(GpuFramePart.Upscale)}",
        )

        assertTrue(
            sharpenContrast > plainContrast,
            "$backend@$label%: Sharpen must increase edge contrast over plain bilinear ($sharpenContrast vs $plainContrast)",
        )
        assertTrue(
            upscaleContrast > plainContrast,
            "$backend@$label%: UpscaleAndSharpen must increase edge contrast over plain bilinear ($upscaleContrast vs $plainContrast)",
        )
        assertTrue(
            sharpenFlatWorst <= FLAT_TOLERANCE,
            "$backend@$label%: Sharpen disturbed flat pixels by $sharpenFlatWorst/255 (limit $FLAT_TOLERANCE)",
        )
        assertTrue(
            upscaleFlatWorst <= FLAT_TOLERANCE,
            "$backend@$label%: UpscaleAndSharpen disturbed flat pixels by $upscaleFlatWorst/255 (limit $FLAT_TOLERANCE)",
        )
        assertEquals(0, sharpenUiDiff, "$backend@$label%: UI stripes must stay full-res and untouched under Sharpen")
        assertEquals(0, upscaleUiDiff, "$backend@$label%: UI stripes must stay full-res and untouched under UpscaleAndSharpen")
        assertTrue(sharpenPasses <= plainPasses, "$backend@$label%: Sharpen added render passes ($sharpenPasses vs $plainPasses)")
        assertTrue(upscalePasses <= plainPasses, "$backend@$label%: UpscaleAndSharpen added render passes ($upscalePasses vs $plainPasses)")
    }

    private class Frames(val renderer: Renderer, private val present: () -> Unit) {
        private val ground = renderer.createMesh(plane(GROUND_HALF, y = 0f))
        private val caster = renderer.createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 0.85f, g = 0.25f, b = 0.25f))
        private val material = renderer.createMaterial(LitShadowUniformLayout)

        fun frame(scale: Float, upscaling: SceneUpscaling): ByteArray {
            renderer.renderScale = scale
            renderer.sceneUpscaling = upscaling
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
        const val THREE_QUARTERS = 0.75f
        const val STRENGTH = 0.8f
        const val GROUND_HALF = 2f
        const val CASTER_HALF = 0.5f
        const val CASTER_Y = 0.6f
        const val STRIPES_X = 8
        const val STRIPES_Y = 8
        const val STRIPES_WIDTH = 32
        const val STRIPES_HEIGHT = 12
        const val FLAT_TOLERANCE = 2
        const val EDGE_LUMA = 20
        const val BYTE = 0xFF
        val REPORT_DIR = File("build/reports/upscale-sharpen")

        fun stripes(): List<UiDrawPrimitive> = buildList {
            add(UiDrawPrimitive.Quad(STRIPES_X.toFloat(), STRIPES_Y.toFloat(), STRIPES_WIDTH.toFloat(), STRIPES_HEIGHT.toFloat(), Color.Black))
            for (column in 0 until STRIPES_WIDTH step 2) {
                add(UiDrawPrimitive.Quad((STRIPES_X + column).toFloat(), STRIPES_Y.toFloat(), 1f, STRIPES_HEIGHT.toFloat(), Color.White))
            }
        }

        fun inStripes(x: Int, y: Int) = x in STRIPES_X until STRIPES_X + STRIPES_WIDTH && y in STRIPES_Y until STRIPES_Y + STRIPES_HEIGHT

        fun stripePixels() = (STRIPES_Y until STRIPES_Y + STRIPES_HEIGHT).flatMap { y -> (STRIPES_X until STRIPES_X + STRIPES_WIDTH).map { x -> x to y } }

        fun ByteArray.channel(pixel: Int, c: Int) = this[pixel * 4 + c].toInt() and BYTE

        fun ByteArray.lumaAt(x: Int, y: Int): Int {
            val p = y.coerceIn(0, SIZE - 1) * SIZE + x.coerceIn(0, SIZE - 1)
            return (channel(p, 0) * 2126 + channel(p, 1) * 7152 + channel(p, 2) * 722) / 10000
        }

        fun ByteArray.channelDelta(other: ByteArray, pixel: Int) = (0..2).maxOf { abs(channel(pixel, it) - other.channel(pixel, it)) }

        fun ByteArray.sameAt(other: ByteArray, x: Int, y: Int) = channelDelta(other, y * SIZE + x) == 0

        fun edgeMask(frame: ByteArray): BooleanArray {
            val step = BooleanArray(SIZE * SIZE) { p ->
                val x = p % SIZE
                val y = p / SIZE
                maxOf(abs(frame.lumaAt(x + 1, y) - frame.lumaAt(x, y)), abs(frame.lumaAt(x, y + 1) - frame.lumaAt(x, y))) > EDGE_LUMA
            }
            return BooleanArray(SIZE * SIZE) { p ->
                val x = p % SIZE
                val y = p / SIZE
                (-2..2).any { dy -> (-2..2).any { dx -> step.getOrElse((y + dy).coerceIn(0, SIZE - 1) * SIZE + (x + dx).coerceIn(0, SIZE - 1)) { false } } }
            }
        }

        /** Mean local luma gradient across scene edge pixels (excluding UI stripes). */
        fun meanEdgeGradient(frame: ByteArray, edges: BooleanArray): Double {
            var sum = 0L
            var count = 0
            for (y in 1 until SIZE - 1) {
                for (x in 1 until SIZE - 1) {
                    val p = y * SIZE + x
                    if (!edges[p] || inStripes(x, y)) continue
                    val gx = abs(frame.lumaAt(x + 1, y) - frame.lumaAt(x - 1, y))
                    val gy = abs(frame.lumaAt(x, y + 1) - frame.lumaAt(x, y - 1))
                    sum += gx + gy
                    count += 1
                }
            }
            return if (count == 0) 0.0 else sum.toDouble() / count
        }

        /** Pixels whose +/-4 output-pixel neighbourhood (full 12-tap CAS footprint at 50% scale) is uniform. */
        fun flatMask(plain: ByteArray): BooleanArray = BooleanArray(SIZE * SIZE) { p ->
            val x = p % SIZE
            val y = p / SIZE
            val inside = x in 4 until SIZE - 4 && y in 4 until SIZE - 4
            if (!inside || inStripes(x, y)) return@BooleanArray false
            var minL = 255
            var maxL = 0
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val l = plain.lumaAt(x + dx, y + dy)
                    if (l < minL) minL = l
                    if (l > maxL) maxL = l
                }
            }
            maxL - minL <= 1
        }

        fun maxFlatDelta(plain: ByteArray, filtered: ByteArray, flat: BooleanArray): Int =
            (0 until SIZE * SIZE)
                .filter { flat[it] }
                .maxOf { plain.channelDelta(filtered, it) }

        fun Double.fmt() = ((this * 100).toInt() / 100.0).toString()

        fun write(name: String, frame: ByteArray) {
            PixelMap(SIZE, SIZE, frame.copyOf()).writePng(File(REPORT_DIR.apply { mkdirs() }, "$name.png"))
        }
    }
}
