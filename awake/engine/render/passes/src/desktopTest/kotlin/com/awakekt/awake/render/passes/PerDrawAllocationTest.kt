/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What every draw of a frame asks of its vertex format, and the frame's sort, allocate nothing per
 * draw with escape analysis off (#1555). Kotlin/Wasm has no escape analysis: a list iterator in
 * `VertexFormat.equals` or a boxed key in `sortedBy` is garbage on every draw there, about 40% of a
 * 3,000-draw frame's on the JVM with escape analysis off.
 */
class PerDrawAllocationTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun askingAFormatAboutItselfAllocatesNothing() {
        assertEscapeAnalysisOff()
        val format = VertexFormat.PositionNormalColorUv
        // An equal format built separately: the comparison a pipeline lookup makes.
        val twin = VertexFormat(format.attributes.toList())
        var seen = 0
        val bytes = allocatedBy {
            repeat(CALLS) {
                if (format == twin) seen++
                seen += format.hashCode() and 1
                if (format.isSkinned) seen++
                seen += format.floatOffsetOf(VertexSemantic.Uv)
            }
        }
        assertTrue(seen != 0)
        // The measurement's own few hundred bytes aside: nothing per question.
        assertTrue(bytes < CALLS, "$CALLS format questions allocated $bytes B")
    }

    @Test
    fun sortingAFramesDrawsAllocatesOnlyTheSortedLists() {
        assertEscapeAnalysisOff()
        val pipelines = List(PIPELINES) { object : PipelineHandle {} }
        val handle = object : MaterialBinding, BufferHandle {}
        // Keys past the JVM's small-integer cache, as material keys are: boxing one allocates.
        val draws = List(DRAWS) { index ->
            GpuResolvedDraw(
                pipeline = pipelines[index % PIPELINES],
                materialBinding = handle,
                vertexBuffer = handle,
                indexBuffer = null,
                elementCount = 3,
                batchKey = 1_000 + (index * 7919) % 100_003,
                transparent = index % 10 == 0,
                depthSortKey = (index % 13).toFloat(),
            )
        }
        repeat(WARM_UP) { sortForRecording(draws) }
        val bytes = allocatedBy { sortForRecording(draws) }
        assertTrue(bytes <= SORT_BUDGET, "sorting $DRAWS draws allocated $bytes B, over $SORT_BUDGET B")
    }

    @Test
    fun compilingAShadowedFrameWithScratchAllocatesNoPerDrawLists() {
        assertEscapeAnalysisOff()
        val pipelines = List(PIPELINES) { object : PipelineHandle {} }
        val handle = object : MaterialBinding, BufferHandle {}
        val meshes = List(MESHES) {
            object : Mesh {
                override val format = VertexFormat.PositionNormalColor
                override val sizeBytes = 0L
                override fun destroy() = Unit
            }
        }
        val material = object : Material {
            override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
            override fun destroy() = Unit
        }
        val resolved = List(DRAWS) { index ->
            GpuResolvedDraw(
                pipeline = pipelines[index % PIPELINES],
                materialBinding = handle,
                vertexBuffer = handle,
                indexBuffer = null,
                elementCount = 3,
                batchKey = 1_000 + (index * 7919) % 100_003,
                transparent = index % 10 == 0,
                depthSortKey = (index % 13).toFloat(),
            )
        }
        val preparer = object : GpuDrawPreparer {
            override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw =
                resolved[sourceIndex % resolved.size]
        }
        val requests = List(DRAWS) { index ->
            val x = (index % ROW - ROW / 2) * SPACING
            val z = (index / ROW - ROW / 2) * SPACING
            RenderDrawCommand(
                meshes[index % MESHES],
                material,
                model = Mat4().translate(x, 0f, z),
                worldBounds = Aabb(Vec3f(x - 1f, -1f, z - 1f), Vec3f(x + 1f, 1f, z + 1f)),
                transparent = index % 10 == 0,
            )
        }
        val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
        val lens = Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
        val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, ClipSpace.WebGpu))
        val scratch = ScenePassScratch()
        fun compileAt(step: Int) {
            lens.eye = Vec3f(step * 0.01f, 30f, 60f)
            ScenePassCompiler.compile(
                lens,
                requests,
                light,
                EnvironmentUniforms.Default,
                ClipSpace.WebGpu,
                1f,
                null,
                preparer,
                emptyList(),
                ImagePassChain.Empty,
                null,
                scratch,
            )
        }
        repeat(WARM_UP) { compileAt(it) }
        val bytes = allocatedBy { compileAt(WARM_UP) }
        assertTrue(bytes <= COMPILE_SCRATCH_BUDGET, "compiling $DRAWS draws with scratch allocated $bytes B, over $COMPILE_SCRATCH_BUDGET B")
    }

    private inline fun allocatedBy(block: () -> Unit): Long {
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        block()
        return threads.getThreadAllocatedBytes(thread) - before
    }

    private fun assertEscapeAnalysisOff() {
        val diagnostics = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean::class.java)
        assertEquals("false", diagnostics.getVMOption("DoEscapeAnalysis").value, "these gates need -XX:-DoEscapeAnalysis")
    }

    private companion object {
        const val CALLS = 10_000
        const val DRAWS = 3_000
        const val MESHES = 190
        const val PIPELINES = 8
        const val ROW = 60
        const val SPACING = 4f
        const val WARM_UP = 20

        /**
         * The per-pipeline map and its sorted lists and their arrays: 56 KB at 3,000 draws.
         * Partitioning first and copying each sorted list allocated 104 KB; boxing the keys on
         * every comparison made it 789 KB.
         */
        const val SORT_BUDGET = 80_000L

        /**
         * With [ScenePassScratch] pooling the caster wrappers, pipeline buckets, sorted opaque and
         * transparent lists, and per-cascade `seenBy` lists, compiling 3,000 shadowed draws
         * allocates only the frame's uniform state and packet headers (~1.4 KB, down from ~280 KB).
         */
        const val COMPILE_SCRATCH_BUDGET = 4_000L
    }
}
