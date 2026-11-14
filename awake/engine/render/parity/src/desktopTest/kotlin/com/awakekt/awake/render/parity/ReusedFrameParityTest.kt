/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.directionalShadowBox
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A still scene's frame, handed back by the compiler unprepared, draws exactly what it drew when
 * it was prepared, on both backends (#1555); a moved caster is prepared and drawn anew. Vulkan
 * draws it again only when opted in (`redrawsStillFrames`), the batch after a reused one waiting
 * for every frame slot; off, it plans every frame, as by default.
 */
class ReusedFrameParityTest {

    @Test
    fun aReusedFrameDrawsTheSamePixelsWithoutPreparingADraw() {
        BACKEND_ORDER.forEach { backend ->
            openSession(backend).renderer.withScene { scene ->
                val first = scene.frame(timeSeconds = 1f)
                assertTrue(first.prepared > 0, "$backend: the first frame is prepared")
                repeat(3) { index ->
                    val still = scene.frame(timeSeconds = 2f + index)
                    assertSame(first.pass, still.pass, "$backend: frame ${index + 2} hands back the first frame's input")
                    assertEquals(0, still.prepared, "$backend: and prepares no draw")
                    assertContentEquals(first.pixels, still.pixels, "$backend: and draws the same pixels")
                }
            }
        }
    }

    @Test
    fun aMovedCasterIsPreparedAndDrawnWhereItMoved() {
        BACKEND_ORDER.forEach { backend ->
            openSession(backend).renderer.withScene { scene ->
                val first = scene.frame(timeSeconds = 1f)
                scene.frame(timeSeconds = 2f)
                val moved = scene.frame(timeSeconds = 3f, casterX = 1.5f)
                assertTrue(moved.prepared > 0, "$backend: a moved caster is prepared")
                assertFalse(first.pixels.contentEquals(moved.pixels), "$backend: and drawn where it moved to")
                assertContentEquals(moved.pixels, scene.frame(timeSeconds = 4f, casterX = 1.5f).pixels, "$backend: and kept there")
            }
        }
    }

    private class Frame(val pass: Any, val prepared: Int, val pixels: ByteArray)

    /** The renderer's preparer, counting what it prepares; it keeps the backend's answer to redrawing. */
    private class CountingPreparer(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        var prepared = 0

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? {
            prepared += 1
            return delegate.prepare(request, sourceIndex, context)
        }
    }

    /** The ground and a caster over it, compiled with a kept scratch as a scene's planner compiles. */
    private class Scene(private val renderer: Renderer) {
        val target = renderer.createRenderTarget(SCENE_SIZE, SCENE_SIZE)
        val ground = renderer.createMesh(plane(GROUND_HALF, y = 0f))
        val caster = renderer.createMesh(plane(CASTER_HALF, y = CASTER_Y))
        val material = renderer.createMaterial(LitShadowUniformLayout)
        private val preparer = CountingPreparer((renderer as GpuDrawPreparationSource).gpuDrawPreparer!!)
        private val scratch = ScenePassScratch()

        fun frame(timeSeconds: Float, casterX: Float = 0f): Frame {
            val draws = listOf(
                RenderDrawCommand(ground, material, timeSeconds = timeSeconds),
                RenderDrawCommand(caster, material, model = Mat4().translate(casterX, 0f, 0f), timeSeconds = timeSeconds),
            )
            val light = SceneLight(direction = Vec3f(2f, 1f, 0f), color = Vec3f(1f, 1f, 1f)).let {
                it.copy(viewProjection = directionalShadowBox(it.direction, renderer.clipSpace).viewProjection)
            }
            val before = preparer.prepared
            val pass = ScenePassCompiler.compile(
                Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f),
                draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.renderToTexture(target, pass)
            return Frame(pass, preparer.prepared - before, runBlocking { renderer.readPixels(target) }.data)
        }
    }

    @Test
    fun vulkanPlansEveryFrameUnlessOptedIn() {
        val vulkan = openSession(HeadlessUiBackend.Vulkan).renderer
        vulkan.withScene(optIn = false) { scene ->
            scene.frame(timeSeconds = 1f)
            assertTrue(scene.frame(timeSeconds = 2f).prepared > 0, "by default a still Vulkan frame is planned again")
        }
    }

    private fun Renderer.withScene(optIn: Boolean = true, block: (Scene) -> Unit) {
        val vulkan = this as? com.awakekt.awake.vulkan.renderer.Renderer
        vulkan?.redrawsStillFrames = optIn
        val scene = Scene(this)
        try {
            block(scene)
        } finally {
            vulkan?.redrawsStillFrames = false
            scene.ground.destroy()
            scene.caster.destroy()
            scene.material.destroy()
            scene.target.destroy()
        }
    }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        private val sessions = mutableMapOf<HeadlessUiBackend, HeadlessRenderSession>()

        fun openSession(backend: HeadlessUiBackend): HeadlessRenderSession = sessions.getOrPut(backend) { openHeadlessScene(backend) }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            sessions.remove(HeadlessUiBackend.Vulkan)?.close()
        }

        const val CASTER_HALF = 1f
    }
}
