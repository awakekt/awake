/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.effects.BackdropBlurReference
import com.awakekt.awake.render.effects.BackdropRegion
import com.awakekt.awake.render.effects.BlurChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.ui.canvas.CanvasBackdrop
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasItem
import com.awakekt.awake.ui.canvas.CanvasOverlay
import com.awakekt.awake.ui.canvas.CanvasStyle
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A canvas panel with a `backdrop` blurs the 3D scene under it, in the frame each backend presents:
 * the scene pass and then the UI pass, through the showcase's plan, on one surface.
 *
 * A lit white cube seen face-on, under an orthographic camera, fills the left half of a black frame;
 * the glass panel straddles its right edge. The same scene rendered alone into a target is the
 * unblurred reference: outside the panel the presented frame must equal it, inside the panel it
 * must differ, and match [BackdropBlurReference] run over it.
 *
 * Measured on lavapipe and wgpu-native, both backends alike: 0 pixels differ outside the panel,
 * 1130 inside it, and the worst channel error against the reference is 1.
 *
 * Negative control, run once: with the effect quality Off, so the panel fills its fallback instead
 * of sampling the scene, the reference check fails on both backends with a worst error of 144. The
 * fallback still changes 768 pixels inside the panel, so the reference check, not the count of
 * changed pixels, is what proves the scene was blurred.
 */
class CanvasBackdropParityTest {
    @Test
    fun vulkanGlassPanelBlursTheSceneBehindIt() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuGlassPanelBlursTheSceneBehindIt() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            val renderer = session.renderer
            renderer.effectQuality = EffectQuality.High
            val mesh = renderer.createMesh(generate { cube(size = CUBE, colored = false) })
            val material = renderer.createMaterial(LitShadowUniformLayout)
            val target = renderer.createRenderTarget(WIDTH, HEIGHT)
            try {
                val pass = scenePass(renderer, RenderDrawCommand(mesh, material))
                renderer.renderToTexture(target, pass)
                val scene = renderer.readPixels(target).data.copyOf()

                renderer.drawUi(glassPanel(), null)
                renderer.draw(pass)
                val frame = renderer.readPresentedPixels().data.copyOf()
                PixelMap(WIDTH, HEIGHT, frame.copyOf()).writePng(File(REPORT_DIR, "canvas-backdrop-$backend.png"))

                val outsideDiffering = countPixels { x, y -> !inPanel(x, y) && differs(frame, scene, x, y, SAME) }
                val insideDiffering = countPixels { x, y -> inPanel(x, y, INSET) && differs(frame, scene, x, y, SAME) }
                val reference = referenceBlur(scene)
                val worst = worstReferenceError(frame, reference)
                println("$backend canvas backdrop: outsideDiffering=$outsideDiffering insideDiffering=$insideDiffering worstReferenceError=$worst")

                assertTrue(scene.luma(CUBE_EDGE - 4, HEIGHT / 2) > LIT, "$backend: control, the cube is lit")
                assertEquals(0, outsideDiffering, "$backend: outside the panel the presented frame is the scene")
                assertTrue(insideDiffering > MIN_BLURRED, "$backend: inside the panel the scene is blurred: $insideDiffering")
                assertTrue(worst <= REFERENCE_TOLERANCE, "$backend: the panel matches the reference blur within $REFERENCE_TOLERANCE: $worst")
            } finally {
                renderer.waitIdle()
                target.destroy()
                mesh.destroy()
                material.destroy()
            }
        }
    }

    private fun scenePass(renderer: Renderer, draw: RenderDrawCommand): GpuPassInput {
        val lens = Lens(eye = Vec3f(CUBE_X, 0f, 5f), center = Vec3f(CUBE_X, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 20f).apply {
            projection = Lens.Projection.Orthographic
            orthoHalfHeight = HALF_HEIGHT
        }
        return ScenePassCompiler.compile(
            lens = lens,
            drawCalls = listOf(draw),
            light = SceneLight(direction = Vec3f(0f, 0f, 1f), color = Vec3f(1f, 1f, 1f)),
            clipSpace = renderer.clipSpace,
            aspect = WIDTH.toFloat() / HEIGHT,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )
    }

    /** One canvas Panel, no fill, with glass: the canvas's own primitives at density 1. */
    private fun glassPanel(): List<UiDrawPrimitive> {
        val panel = CanvasElement().apply {
            kind = CanvasElementKind.Panel
            offsetX = PANEL_X.toFloat(); offsetY = PANEL_Y.toFloat(); width = PANEL_W.toFloat(); height = PANEL_H.toFloat()
            style = CanvasStyle(cornerRadius = CORNER, backdrop = CanvasBackdrop(blur = BLUR))
        }
        return ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) { CanvasOverlay(listOf(CanvasItem(0, panel))) }.primitives
    }

    private fun referenceBlur(scene: ByteArray): FloatArray {
        val chain = requireNotNull(BlurChain.forRadius(BLUR, EffectQuality.High))
        val floats = FloatArray(scene.size) { (scene[it].toInt() and BYTE) / 255f }
        val region = BackdropRegion.around(
            PANEL_X.toFloat(),
            PANEL_Y.toFloat(),
            PANEL_W.toFloat(),
            PANEL_H.toFloat(),
            chain.extent,
            WIDTH,
            HEIGHT,
        )
        val blurred = BackdropBlurReference.blur(floats, WIDTH, HEIGHT, region, chain)
        val out = FloatArray(WIDTH * HEIGHT * 4)
        val sample = FloatArray(4)
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                blurred.sample(x, y, sample)
                sample.copyInto(out, (y * WIDTH + x) * 4)
            }
        }
        return out
    }

    /** The largest RGB difference, in bytes, between the frame and the reference inside the panel. */
    private fun worstReferenceError(frame: ByteArray, reference: FloatArray): Int {
        var worst = 0
        for (y in PANEL_Y + INSET until PANEL_Y + PANEL_H - INSET) {
            for (x in PANEL_X + INSET until PANEL_X + PANEL_W - INSET) {
                for (c in 0..2) {
                    val expected = (reference[(y * WIDTH + x) * 4 + c] * 255f).roundToInt()
                    worst = maxOf(worst, abs(frame.channel(x, y, c) - expected))
                }
            }
        }
        return worst
    }

    private suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
        if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(EngineShowcaseRenderPlan, surface = HeadlessSurface(WIDTH, HEIGHT))
        val ready = CompletableDeferred<Renderer>()
        val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
        val engine = VulkanEngine(app, EngineShowcaseRenderPlan)
        engine.create(HeadlessSurface(WIDTH, HEIGHT))
        val renderer = withTimeout(READY_TIMEOUT_MS) { ready.await() }
        return object : HeadlessRenderSession {
            override val renderer = renderer
            override fun close() = engine.dispose()
        }
    }

    private fun inPanel(x: Int, y: Int, inset: Int = 0) =
        x in PANEL_X + inset until PANEL_X + PANEL_W - inset && y in PANEL_Y + inset until PANEL_Y + PANEL_H - inset

    private fun countPixels(test: (Int, Int) -> Boolean): Int = (0 until HEIGHT).sumOf { y -> (0 until WIDTH).count { x -> test(x, y) } }

    private fun differs(a: ByteArray, b: ByteArray, x: Int, y: Int, tolerance: Int) =
        (0..2).any { abs(a.channel(x, y, it) - b.channel(x, y, it)) > tolerance }

    private fun ByteArray.channel(x: Int, y: Int, c: Int) = this[(y * WIDTH + x) * 4 + c].toInt() and BYTE

    private fun ByteArray.luma(x: Int, y: Int) = (0..2).sumOf { channel(x, y, it) } / 3

    private companion object {
        const val WIDTH = 160
        const val HEIGHT = 120
        const val HALF_HEIGHT = 2f
        const val CUBE = 2f

        /** The cube's right face edge on screen, at the frame's centre: the camera looks at its right edge. */
        const val CUBE_X = 1f
        const val CUBE_EDGE = WIDTH / 2
        const val PANEL_W = 64
        const val PANEL_H = 48
        const val PANEL_X = CUBE_EDGE - PANEL_W / 2
        const val PANEL_Y = (HEIGHT - PANEL_H) / 2
        const val CORNER = 8f
        const val BLUR = 8f

        /** Clear of the rounded corners and the antialiased edge. */
        const val INSET = 8
        const val SAME = 2
        const val LIT = 150
        const val MIN_BLURRED = 200
        const val REFERENCE_TOLERANCE = 8
        const val BYTE = 0xFF
        const val READY_TIMEOUT_MS = 30_000L
        const val REPORT_DIR = "build/reports/render-parity"
    }
}
