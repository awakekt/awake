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
import com.awakekt.awake.render.renderer.createMaterial
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the WebGPU preparer allocates per draw of a planned frame, measured with escape analysis off
 * as Kotlin/Wasm runs (#1555). A shadowed scene of [DRAWS] lit props over [MESHES] meshes, with the
 * camera moving every frame so every frame is prepared.
 *
 * About 760 B a draw before: three resolved draws, a pipeline binding, a depth key, a capturing
 * lambda and a pair per draw, besides the prepared draw itself. About 550 B now; what is left is the
 * prepared and resolved draws the frame hands on.
 */
class PreparedDrawAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    private class MeasuringPreparer(private val delegate: GpuDrawPreparer, private val bytes: () -> Long) : GpuDrawPreparer by delegate {
        var prepared = 0L
        var draws = 0

        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw? {
            val before = bytes()
            return delegate.prepare(request, sourceIndex, context).also {
                prepared += bytes() - before
                draws++
            }
        }
    }

    @Test
    fun aPreparedDrawAllocatesLittleBesidesWhatItHandsOn() = webGpuHeadlessScene().use { session ->
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "this gate needs -XX:-DoEscapeAnalysis")
        val renderer = session.renderer
        val meshes = List(MESHES) { renderer.createMesh(triangle()) }
        val materials = List(MESHES) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
        val thread = Thread.currentThread().id
        val preparer = MeasuringPreparer((renderer as GpuDrawPreparationSource).gpuDrawPreparer!!) { threads.getThreadAllocatedBytes(thread) }
        val draws = List(DRAWS) { index ->
            val x = (index % ROW - ROW / 2) * SPACING
            val z = (index / ROW - ROW / 2) * SPACING
            RenderDrawCommand(
                meshes[index % MESHES],
                materials[index % MESHES],
                model = Mat4().translate(x, 0f, z),
                worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
            )
        }
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val scratch = ScenePassScratch()
        fun frame(index: Int) {
            val lens = Lens(eye = Vec3f(0.01f * index, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
            val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, renderer.clipSpace))
            ScenePassCompiler.compile(
                lens, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
                ImagePassChain.Empty, null, scratch,
            )
        }
        repeat(WARM_UP) { frame(it) }
        preparer.prepared = 0
        preparer.draws = 0
        repeat(FRAMES) { frame(WARM_UP + it) }

        val perDraw = preparer.prepared / preparer.draws
        println("Prepared draw: $perDraw B, ${preparer.draws / FRAMES} draws a frame")
        assertTrue(preparer.draws >= DRAWS * FRAMES, "every frame is prepared")
        assertTrue(perDraw <= PER_DRAW_BUDGET, "a prepared draw allocated $perDraw B, over $PER_DRAW_BUDGET B")
        meshes.forEach { it.destroy() }
        materials.forEach { it.destroy() }
    }

    private fun triangle() = MeshGeometry(
        floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f),
        intArrayOf(0, 1, 2),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val DRAWS = 1_000
        const val MESHES = 190
        const val ROW = 40
        const val SPACING = 4f
        const val WARM_UP = 20
        const val FRAMES = 20

        /** About 550 B a draw now: a little room, and well under the 760 B before. */
        const val PER_DRAW_BUDGET = 620L
    }
}
