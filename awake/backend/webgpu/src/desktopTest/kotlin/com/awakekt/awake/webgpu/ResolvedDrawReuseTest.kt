/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Aabb
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
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.webgpu.renderer.WebGpuDrawPreparer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A draw's packet is handed back from frame to frame while it would resolve the same, and built
 * again the frame any of its fields changes (#1555).
 *
 * Negative controls, measured: with the cache answering yes for any slot it holds, a dropped copy's
 * draw kept its old instance count; with every opaque packet carrying its distance to the camera,
 * a moving camera built every packet again.
 */
class ResolvedDrawReuseTest {

    @Test
    fun aMovingCameraHandsBackEveryOpaquePacket() = withScene { scene ->
        val props = List(PROPS) { index -> scene.prop(x = index * SPACING) }
        repeat(2) { scene.frame(props, eyeX = it.toFloat()) }
        val built = scene.cache.built

        repeat(FRAMES) { scene.frame(props, eyeX = 2f + it) }

        assertEquals(built, scene.cache.built, "a moving camera changes no opaque packet, so none is built again")
    }

    @Test
    fun aDroppedCopyIsDrawnWithItsNewCount() = withScene { scene ->
        val copies = List(COPIES) { index -> scene.prop(x = index * SPACING) }
        scene.frame(copies, eyeX = 0f)

        val fewer = scene.frame(copies.take(COPIES - 2), eyeX = 0f)

        assertEquals(COPIES - 2, fewer.sumOf { it.instances }, "the draw is resolved with the copies left")
    }

    @Test
    fun aTransparentDrawsSortKeyFollowsTheCamera() = withScene { scene ->
        val glass = listOf(scene.prop(x = 0f, transparent = true))
        val near = scene.frame(glass, eyeX = 0f).single().depthSortKey
        val far = scene.frame(glass, eyeX = 20f).single().depthSortKey

        assertNotEquals(near, far, "a transparent draw is ordered by its distance, so its packet carries the new one")
    }

    private fun <T> withScene(block: (Scene) -> T): T = webGpuHeadlessScene().use { session -> Scene(session.renderer).use(block) }

    /** Collects the packets a frame resolves. */
    private class Collecting(private val delegate: GpuDrawPreparer) : GpuDrawPreparer by delegate {
        val packets = ArrayList<GpuResolvedDraw>()

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? =
            delegate.prepare(request, sourceIndex, context)?.also { packets += it }
    }

    private class Scene(private val renderer: Renderer) : AutoCloseable {
        private val target = renderer.createRenderTarget(SIZE, SIZE)
        private val mesh = renderer.createMesh(triangle())
        private val lit = renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total)
        private val webGpu = (renderer as GpuDrawPreparationSource).gpuDrawPreparer as WebGpuDrawPreparer
        private val preparer = Collecting(webGpu)
        private val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        private val scratch = ScenePassScratch()
        val cache get() = webGpu.resolvedDraws

        fun prop(x: Float, transparent: Boolean = false) = RenderDrawCommand(
            mesh,
            lit,
            model = Mat4().translate(x, 0f, 0f),
            worldBounds = Aabb(Vec3f(x - 1f, -1f, -1f), Vec3f(x + 1f, 1f, 1f)),
            transparent = transparent,
        )

        /** The packets of the scene pass drawing [draws] from [eyeX]. */
        fun frame(draws: List<RenderDrawCommand>, eyeX: Float): List<GpuResolvedDraw> {
            preparer.packets.clear()
            val lens = Lens(eye = Vec3f(eyeX, 8f, 12f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            val pass = ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
            renderer.submitToTexture(target, pass)
            return preparer.packets.toList()
        }

        override fun close() {
            mesh.destroy()
            lit.destroy()
            target.destroy()
        }
    }

    private companion object {
        const val SIZE = 32
        const val PROPS = 8
        const val COPIES = 6
        const val FRAMES = 10
        const val SPACING = 1.5f

        fun triangle() = MeshGeometry(
            floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f),
            intArrayOf(0, 1, 2),
            VertexFormat.PositionNormalColor,
        )
    }
}
