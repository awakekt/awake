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
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.webgpu.renderer.Renderer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drawing a still frame again, as `RenderSystem3D` does with the input it planned last when nothing
 * moved, writes nothing it already wrote (#1621).
 *
 * Every frame wrote each cascade's matrix to every depth pipeline's cascade buffer, through a new
 * staging buffer each time, though a replayed frame's matrices are the ones the buffers already
 * hold: a wasm build pays a typed array and a queue write for each. With escape analysis off, as
 * this module's test task runs, so what wasm allocates is counted. What remains is mostly the
 * desktop WebGPU binding's own descriptor marshalling for each pass begun, which wasm does not do.
 */
class ReplayedFrameAllocationTest {

    @Test
    fun aReplayedStillFrameWritesNothingAgain() = webGpuHeadlessScene().use { session ->
        val renderer = session.renderer as Renderer
        val main = renderer.createRenderTarget(SIZE, SIZE)
        val meshes = List(MESHES) { renderer.createMesh(triangle()) }
        val materials = List(MESHES) { renderer.createMaterial(uniformFloatCount = MaterialUniformLayouts.LitShadow.total) }
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
        val preparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer!!
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val wide = Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
        val scratch = ScenePassScratch()
        fun allocated() = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes
        val light = sun.copy(cascades = shadowCascadeUniforms(sun, wide, 1f, renderer.clipSpace))
        val pass = ScenePassCompiler.compile(
            wide, draws, light, EnvironmentUniforms.Default, renderer.clipSpace, 1f, null, preparer, emptyList(),
            ImagePassChain.Empty, null, scratch,
        )
        fun replayBytes(): Long {
            var least = Long.MAX_VALUE
            repeat(5) {
                val before = allocated()
                repeat(50) { renderer.submitToTexture(main, pass) }
                least = minOf(least, (allocated() - before) / 50)
            }
            return least
        }
        repeat(WARM_UP) { replayBytes() }
        val replay = replayBytes()
        println("ReplayedFrameAllocationTest: a replayed still frame of $DRAWS shadowed draws allocates $replay B")
        assertTrue(replay <= CEILING, "a replayed still frame allocates $replay B")
        meshes.forEach { it.destroy() }
        materials.forEach { it.destroy() }
        main.destroy()
    }

    private fun triangle() = MeshGeometry(
        floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, -1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f),
        intArrayOf(0, 1, 2),
        VertexFormat.PositionNormalColor,
    )

    private companion object {
        const val SIZE = 64
        const val DRAWS = 600
        const val MESHES = 60
        const val ROW = 30
        const val SPACING = 3f
        const val WARM_UP = 20

        // A still frame wrote every cascade matrix to every depth pipeline again before #1621. The
        // ceiling sits above what is left (the binding's per-pass descriptor marshalling, ~13 KB).
        const val CEILING = 16_000L
    }
}
