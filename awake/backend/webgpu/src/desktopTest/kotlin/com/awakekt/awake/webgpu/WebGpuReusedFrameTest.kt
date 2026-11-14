/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
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
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import com.awakekt.awake.render.renderer.Renderer as RenderRenderer

/**
 * A still scene's frame, handed back by the compiler unprepared (#1555), draws on WebGPU exactly
 * what it drew when it was prepared, frame after frame, and a moved prop is prepared and drawn anew.
 *
 * The scene is a shadowed ground and a lit prop casting onto it, compiled with a kept scratch as
 * `RenderSystem3D`'s planner compiles, at a later clock time each frame.
 */
class WebGpuReusedFrameTest {

    @Test
    fun aReusedFrameDrawsTheSamePixelsWithoutPreparingADraw() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        assertTrue(first.prepared > 0, "the first frame is prepared")

        repeat(3) { index ->
            val still = scene.frame(timeSeconds = 2f + index)
            assertSame(first.pass, still.pass, "frame ${index + 2} hands back the first frame's input")
            assertEquals(0, still.prepared, "and prepares no draw")
            assertContentEquals(first.pixels, still.pixels, "and draws the same pixels")
        }
    }

    @Test
    fun aMovedPropIsPreparedAndDrawnWhereItMoved() = withScene { scene ->
        val first = scene.frame(timeSeconds = 1f)
        scene.frame(timeSeconds = 2f)

        val moved = scene.frame(timeSeconds = 3f, propX = 1.5f)

        assertTrue(moved.prepared > 0, "a moved prop is prepared")
        assertFalse(first.pixels.contentEquals(moved.pixels), "and drawn where it moved to")
        assertEquals(moved.pixels.toList(), scene.frame(timeSeconds = 4f, propX = 1.5f).pixels.toList(), "and kept there while still")
    }

    private fun <T> withScene(block: (Scene) -> T): T = webGpuHeadlessScene().use { session ->
        Scene(session.renderer).use(block)
    }

    private class Frame(val pass: Any, val prepared: Int, val pixels: ByteArray)

    /** The renderer's preparer, counting what it prepares; it keeps WebGPU's answer to redrawing. */
    private class CountingPreparer(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        var prepared = 0

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? {
            prepared += 1
            return delegate.prepare(request, sourceIndex, context)
        }
    }

    private class Scene(val renderer: RenderRenderer) : AutoCloseable {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val ground = renderer.createMesh(quad(GROUND_HALF))
        private val prop = renderer.createMesh(quad(PROP_HALF))
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val sun = SceneLight(direction = Vec3f(0.3f, 1f, 0.2f), color = Vec3f(1f, 1f, 1f))
        private val preparer = CountingPreparer((renderer as GpuDrawPreparationSource).gpuDrawPreparer!!)
        private val scratch = ScenePassScratch()

        fun frame(timeSeconds: Float, propX: Float = 0f): Frame {
            val lens = Lens(eye = Vec3f(0f, 9f, 7f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val draws = listOf(
                RenderDrawCommand(ground, lit, model = Mat4(), timeSeconds = timeSeconds),
                RenderDrawCommand(prop, lit, model = translation(propX, PROP_HEIGHT, 0f), timeSeconds = timeSeconds),
            )
            val before = preparer.prepared
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.renderToTexture(target, pass)
            return Frame(pass, preparer.prepared - before, runBlocking { renderer.readPixels(target) }.data)
        }

        override fun close() {
            ground.destroy()
            prop.destroy()
            lit.destroy()
            target.destroy()
        }
    }

    private companion object {
        const val SIZE = 96
        const val GROUND_HALF = 6f
        const val PROP_HALF = 1.2f
        const val PROP_HEIGHT = 1.5f

        fun translation(x: Float, y: Float, z: Float) = Mat4().apply {
            data[12] = x
            data[13] = y
            data[14] = z
        }

        /** A flat white square facing up. */
        fun quad(half: Float): MeshGeometry {
            val corners = listOf(-half to -half, half to -half, half to half, -half to half)
            val vertices = corners.flatMap { (x, z) -> listOf(x, 0f, z, 0f, 1f, 0f, 1f, 1f, 1f) }
            return MeshGeometry(vertices.toFloatArray(), intArrayOf(0, 1, 2, 2, 3, 0), VertexFormat.PositionNormalColor)
        }
    }
}
