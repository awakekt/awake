/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.BoundedPreparationQueue
import com.awakekt.awake.render.passes.RenderDrawCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the draw ordering both backends now share.
 *
 * Each rule here is invisible at runtime when broken: a mis-sorted transparent list still renders
 * every surface, just blended in the wrong order, and a lost grouping still draws everything,
 * just with more state changes. Vulkan and WebGPU had already drifted on one of them -- Vulkan
 * clustered each pipeline group by mesh, WebGPU did not -- which is exactly the kind of silent
 * divergence that survives when two backends each own the same decision.
 */
class SortedDrawsTest {

    private object PipelineA : PipelineHandle
    private object PipelineB : PipelineHandle
    private object Binding : MaterialBinding

    private val mesh = object : Mesh {
        override val format = VertexFormat.PositionColorUv
        override val sizeBytes = 12L
        override fun destroy() = Unit
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }

    private class Draw(
        override val pipeline: PipelineHandle,
        override val transparent: Boolean = false,
        override val depthSortKey: Float = 0f,
        override val batchKey: Int = 0,
        val label: String = "",
    ) : PreparedDraw {
        override val materialBinding: MaterialBinding = Binding
        override val vertexBuffer: BufferHandle? = null
        override val indexBuffer: BufferHandle? = null
        override val elementCount: Int = 3
    }

    @Test
    fun opaqueDrawsAreGroupedByPipeline() {
        val sorted = sortForRecording(
            listOf(Draw(PipelineA), Draw(PipelineB), Draw(PipelineA)),
        )

        assertEquals(setOf(PipelineA, PipelineB), sorted.opaqueByPipeline.keys)
        assertEquals(2, sorted.opaqueByPipeline.getValue(PipelineA).size)
        assertTrue(sorted.transparent.isEmpty())
    }

    /** The rule WebGPU was missing. Without it, two draws sharing a mesh can be separated by a
     * third, and each rebinds the same vertex and index buffers. */
    @Test
    fun eachPipelineGroupIsClusteredByBatchKey() {
        val sorted = sortForRecording(
            listOf(
                Draw(PipelineA, batchKey = 2, label = "meshB"),
                Draw(PipelineA, batchKey = 1, label = "meshA"),
                Draw(PipelineA, batchKey = 2, label = "meshB again"),
            ),
        )

        assertEquals(
            listOf("meshA", "meshB", "meshB again"),
            sorted.opaqueByPipeline.getValue(PipelineA).map { it.label },
        )
    }

    /** Farthest first, so nearer surfaces blend over what is already behind them. */
    @Test
    fun transparentDrawsAreSortedBackToFront() {
        val sorted = sortForRecording(
            listOf(
                Draw(PipelineA, transparent = true, depthSortKey = 1f, label = "near"),
                Draw(PipelineA, transparent = true, depthSortKey = 9f, label = "far"),
                Draw(PipelineA, transparent = true, depthSortKey = 5f, label = "mid"),
            ),
        )

        assertEquals(
            listOf("far", "mid", "near"),
            sorted.transparent.map { it.label },
        )
    }

    /** Grouping reorders, and blending cannot survive that -- so transparent draws must never
     * enter the grouped map, even when they share a pipeline with opaque ones. */
    @Test
    fun transparentDrawsAreHeldOutOfPipelineGrouping() {
        val sorted = sortForRecording(
            listOf(
                Draw(PipelineA, label = "opaque"),
                Draw(PipelineA, transparent = true, label = "blended"),
            ),
        )

        assertEquals(1, sorted.opaqueByPipeline.getValue(PipelineA).size)
        assertEquals(listOf("blended"), sorted.transparent.map { it.label })
    }

    @Test
    fun asyncCompilerKeepsSourceResultsAndSharedOrdering() = runTest {
        val source = listOf(
            RenderDrawCommand(mesh, material),
            RenderDrawCommand(mesh, material),
            RenderDrawCommand(mesh, material),
        )
        val queue = BoundedPreparationQueue<Int, Draw?>(this, capacity = 2, workers = 2) { index ->
            Draw(
                pipeline = PipelineA,
                transparent = true,
                depthSortKey = index.toFloat(),
                label = index.toString(),
            )
        }
        try {
            val sorted = compileDrawCallsAsync<RenderDrawCommand, Draw>(source, queue)
            assertEquals(listOf("2", "1", "0"), sorted.transparent.map { it.label })
        } finally {
            queue.close()
        }
    }

    @Test
    fun asyncCompilerPublishesAnExplicitCommandLease() = runTest {
        val source = listOf(RenderDrawCommand(mesh, material))
        val queue = BoundedPreparationQueue<Int, Draw?>(this, capacity = 1) { index ->
            Draw(PipelineA, label = index.toString())
        }
        try {
            val lease = compileDrawCallsAsyncLease<RenderDrawCommand, Draw>(source, queue)
            assertEquals(GpuCommandLeaseState.Sealed, lease.state)
            lease.submit()
            lease.retire()
            assertEquals(GpuCommandLeaseState.Retired, lease.state)
        } finally {
            queue.close()
        }
    }
}
