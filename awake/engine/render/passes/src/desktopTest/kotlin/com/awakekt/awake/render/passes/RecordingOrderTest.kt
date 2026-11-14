/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.drawsForRecording
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An executor records a compiled frame's draws as the compiler sorted them, instead of sorting them
 * again (#1555): the same order, for a small part of the garbage. A pass input built by hand is
 * sorted as before.
 */
class RecordingOrderTest {
    private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    @Test
    fun aCompiledFrameIsRecordedInTheOrderSortingWouldGive() {
        val input = compiled()
        assertTrue(input.drawsInRecordingOrder, "the compiler sorted these")

        val asIs = input.drawsForRecording()
        val sorted = sortForRecording(input.resolvedDraws)

        assertEquals(sorted.opaqueByPipeline.keys.toList(), asIs.opaqueByPipeline.keys.toList(), "pipelines in the same order")
        sorted.opaqueByPipeline.forEach { (pipeline, draws) -> assertSameDraws(draws, asIs.opaqueByPipeline.getValue(pipeline)) }
        assertSameDraws(sorted.transparent, asIs.transparent)
    }

    @Test
    fun recordingACompiledFrameAllocatesAFractionOfSortingIt() {
        val input = compiled()
        repeat(WARM_UP) { input.drawsForRecording() }
        repeat(WARM_UP) { sortForRecording(input.resolvedDraws) }

        val asIs = allocatedBy { input.drawsForRecording() }
        val sorting = allocatedBy { sortForRecording(input.resolvedDraws) }

        assertTrue(asIs <= AS_IS_BUDGET, "recording $DRAWS draws as they are allocated $asIs B, over $AS_IS_BUDGET B")
        assertTrue(asIs * SHARE < sorting, "as they are: $asIs B; sorting again: $sorting B")
    }

    @Test
    fun anInputBuiltByHandIsStillSorted() {
        val pipeline = object : PipelineHandle {}
        val draws = listOf(5, 1, 3).map { resolved(pipeline, batchKey = it) }
        val input = GpuPassInput.EMPTY.copy(resolvedOpaqueDraws = draws, resolvedPath = true)
        assertFalse(input.drawsInRecordingOrder)

        assertEquals(listOf(1, 3, 5), input.drawsForRecording().opaqueByPipeline.getValue(pipeline).map { it.batchKey })
    }

    private fun assertSameDraws(expected: List<GpuResolvedDraw>, actual: List<GpuResolvedDraw>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertTrue(expected[it] === actual[it], "draw $it") }
    }

    /** [DRAWS] draws over [PIPELINES] pipelines and many batch keys, every tenth blended. */
    private fun compiled(): GpuPassInput {
        val pipelines = List(PIPELINES) { object : PipelineHandle {} }
        val meshes = List(MESHES) { mesh() }
        val preparer = object : GpuDrawPreparer {
            override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw {
                val mesh = meshes.indexOf(request.mesh)
                return resolved(pipelines[mesh % PIPELINES], batchKey = 1_000 + mesh * 7_919 % 10_007, transparent = request.transparent)
            }
        }
        val draws = List(DRAWS) { index ->
            RenderDrawCommand(meshes[index % MESHES], material, model = Mat4().translate(index.toFloat(), 0f, 0f), transparent = index % 10 == 0)
        }
        val lens = Lens(eye = Vec3f(0f, 5f, 10f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 100f)
        return ScenePassCompiler.compile(lens, draws, null, clipSpace = ClipSpace.WebGpu, aspect = 1f, drawPreparer = preparer)
    }

    private fun resolved(pipeline: PipelineHandle, batchKey: Int, transparent: Boolean = false) = GpuResolvedDraw(
        pipeline = pipeline,
        materialBinding = handle,
        vertexBuffer = handle,
        indexBuffer = null,
        elementCount = 3,
        batchKey = batchKey,
        transparent = transparent,
        depthSortKey = batchKey.toFloat(),
    )

    private inline fun allocatedBy(block: () -> Unit): Long {
        val thread = Thread.currentThread().id
        val before = threads.getThreadAllocatedBytes(thread)
        block()
        return threads.getThreadAllocatedBytes(thread) - before
    }

    private fun mesh() = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private val handle = object : MaterialBinding, BufferHandle {}

    private companion object {
        const val DRAWS = 3_000
        const val MESHES = 190
        const val PIPELINES = 8
        const val WARM_UP = 20

        /** A map and a view per pipeline: a few hundred bytes, whatever the draw count. */
        const val AS_IS_BUDGET = 4_000L

        /** Recording as they are allocates under a tenth of sorting again. */
        const val SHARE = 10
    }
}
