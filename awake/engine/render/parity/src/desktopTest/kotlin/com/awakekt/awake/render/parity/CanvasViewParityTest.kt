/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.bootstrap.dsl.app
import com.awakekt.awake.engine.platform.dsl.requireService
import com.awakekt.awake.render.capture.PixelMap
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.writePng
import com.awakekt.awake.scene.authoring.scene
import com.awakekt.awake.scene.canvas.CanvasAnchor
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.Camera
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.runtime.SceneAppLifecycleRuntime
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A View element shows its camera's picture in its box, through the scene runtime and the showcase's
 * plan. The main camera sees a white cube on black; a second camera, far to the side, sees a red cube
 * filling the top half of its picture and nothing below. The View sits in the top-right corner, where
 * the main camera sees only black.
 *
 * Vulkan is read from the presented frame its own frame loop draws, View and all. WebGPU's headless
 * fixture presents nothing, so its frame is read from the target it renders into, and the runtime's UI
 * is drawn into a second one.
 */
class CanvasViewParityTest {
    @Test
    fun vulkanShowsTheViewsCameraInItsBox() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuShowsTheViewsCameraInItsBox() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        val game = app {
            window { size(WIDTH, HEIGHT) }
            scene("view") { onReady { populate() } }
        }
        openRuntimeSession(backend, game, WIDTH, HEIGHT).use { session ->
            val renderer = session.renderer
            try {
                game.ready(renderer)
                val runtime = game.requireService<SceneAppLifecycleRuntime>()
                repeat(FRAMES) { game.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }
                val before = mainFrame(renderer)

                runtime.world.add(runtime.world.create(), portrait())
                // The UI a frame composes is drawn by the next frame's pass.
                repeat(FRAMES) { game.update(FRAME, WIDTH.toFloat(), HEIGHT.toFloat()) }
                val after = mainFrame(renderer)
                val shown = if (backend == HeadlessUiBackend.Vulkan) after else ui(renderer, runtime)
                PixelMap(WIDTH, HEIGHT, shown.copyOf()).writePng(File(REPORT_DIR, "canvas-view-$backend.png"))

                val box = BOX_X until BOX_X + BOX_WIDTH
                val top = BOX_Y until BOX_Y + BOX_HEIGHT / 2
                // A row clear of the eye line, which the cube's bottom edge lies on.
                val bottom = BOX_Y + BOX_HEIGHT / 2 + 1 until BOX_Y + BOX_HEIGHT
                val redTop = top.sumOf { y -> box.count { x -> shown.isRed(x, y) } }
                val redBottom = bottom.sumOf { y -> box.count { x -> shown.isRed(x, y) } }
                val redOutside = (0 until HEIGHT).sumOf { y -> (0 until WIDTH).count { x -> !inBox(x, y) && shown.isRed(x, y) } }
                val changed = (0 until HEIGHT).sumOf { y ->
                    (0 until WIDTH).count { x -> (backend != HeadlessUiBackend.Vulkan || !inBox(x, y)) && !before.sameAt(after, x, y) }
                }
                // The camera's own picture at the box's size, as a capture draws it.
                val portraitCamera = runtime.findCamera("Portrait Camera")!!
                val expected = runtime.readback(runtime.world, portraitCamera, BOX_WIDTH, BOX_HEIGHT).data
                val differing = (0 until BOX_HEIGHT).sumOf { y ->
                    (0 until BOX_WIDTH).count { x ->
                        (0..2).any { c -> abs(shown.channel(BOX_X + x, BOX_Y + y, c) - (expected[(y * BOX_WIDTH + x) * 4 + c].toInt() and BYTE)) > TOLERANCE }
                    }
                }
                println("$backend canvas view: redTop=$redTop/${BOX_WIDTH * top.count()} redBottom=$redBottom redOutside=$redOutside changed=$changed differing=$differing")

                assertTrue(redTop > BOX_WIDTH * top.count() * 9 / 10, "$backend: the box's top half shows the red cube: $redTop")
                assertEquals(0, redBottom, "$backend: upright, the cube above the camera's eye line")
                assertEquals(0, differing, "$backend: the box shows its camera's picture texel for pixel")
                assertEquals(0, redOutside, "$backend: nothing of the view outside its box")
                assertEquals(0, changed, "$backend: the main camera's picture is the one it drew with no View")
                assertTrue(before.isBright(WIDTH / 2, HEIGHT / 2), "$backend: control, the main camera sees its white cube")
            } finally {
                renderer.waitIdle()
                game.dispose()
            }
        }
    }

    private fun SceneAppLifecycleRuntime.populate() {
        val white = generate { cube(size = 1f) }
        val red = generate { cube(size = RED_CUBE) }.let { geometry ->
            val stride = geometry.format.strideFloats
            val color = geometry.format.floatOffsetOf(VertexSemantic.Color)
            val vertices = geometry.vertices.copyOf()
            for (vertex in 0 until vertices.size / stride) {
                vertices[vertex * stride + color + 1] = 0f
                vertices[vertex * stride + color + 2] = 0f
            }
            geometry.copy(vertices = vertices)
        }
        val lit = renderer.createMaterial(LitShadowUniformLayout)
        world.add(world.create(), Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)))
        world.add(world.create(), Light(direction = Vec3f(-0.6f, 0.5f, 1f)))
        world.create().also {
            world.add(it, Transform())
            world.add(it, MeshRenderer(renderer.createMesh(white), lit))
        }
        // Its bottom face on the portrait camera's eye line, so it fills the top half of the picture.
        world.create().also {
            world.add(it, Transform(position = Vec3f(SIDE, RED_CUBE / 2, 0f)))
            world.add(it, MeshRenderer(renderer.createMesh(red), lit))
        }
        world.create().also {
            world.add(it, Name("Portrait Camera"))
            val lens = Lens(eye = Vec3f(SIDE, 0f, PORTRAIT_DISTANCE), center = Vec3f(SIDE, 0f, 0f), fovYRadians = 0.5f, near = 0.1f, far = 100f)
            world.add(it, Camera(lens, isPrimary = false))
        }
    }

    private fun portrait() = CanvasElement().apply {
        kind = CanvasElementKind.View
        camera = "Portrait Camera"
        anchor = CanvasAnchor.TopRight
        offsetX = INSET.toFloat()
        offsetY = INSET.toFloat()
        width = BOX_WIDTH.toFloat()
        height = BOX_HEIGHT.toFloat()
    }

    /** What the main camera drew: the presented frame on Vulkan, the frame target on WebGPU. */
    private suspend fun mainFrame(renderer: Renderer): ByteArray = when (renderer) {
        is OffscreenFrames -> renderer.readPixels(renderer.frame)
        else -> renderer.readPresentedPixels()
    }.data.copyOf()

    /** The runtime's last UI frame, drawn alone into a target. */
    private suspend fun ui(renderer: Renderer, runtime: SceneAppLifecycleRuntime): ByteArray {
        val target = renderer.createRenderTarget(WIDTH, HEIGHT)
        return try {
            renderer.drawUiToTexture(target, runtime.uiPrimitives)
            renderer.readPixels(target).data.copyOf()
        } finally {
            target.destroy()
        }
    }

    private fun inBox(x: Int, y: Int) = x in BOX_X until BOX_X + BOX_WIDTH && y in BOX_Y until BOX_Y + BOX_HEIGHT

    private fun ByteArray.channel(x: Int, y: Int, channel: Int) = this[(y * WIDTH + x) * 4 + channel].toInt() and BYTE

    private fun ByteArray.isRed(x: Int, y: Int): Boolean =
        channel(x, y, 0) > channel(x, y, 1) + DOMINANCE && channel(x, y, 0) > channel(x, y, 2) + DOMINANCE

    private fun ByteArray.isBright(x: Int, y: Int): Boolean = (0..2).all { channel(x, y, it) > DOMINANCE }

    private fun ByteArray.sameAt(other: ByteArray, x: Int, y: Int): Boolean = (0..2).all { channel(x, y, it) == other.channel(x, y, it) }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 180
        const val FRAMES = 2
        const val FRAME = 1f / 60f
        const val INSET = 8
        const val BOX_WIDTH = 96
        const val BOX_HEIGHT = 64
        const val BOX_X = WIDTH - INSET - BOX_WIDTH
        const val BOX_Y = INSET
        const val SIDE = 50f
        const val RED_CUBE = 4f
        const val PORTRAIT_DISTANCE = 6f
        const val DOMINANCE = 60
        const val TOLERANCE = 2
        const val BYTE = 0xFF
        const val REPORT_DIR = "build/reports/render-parity"
    }
}
