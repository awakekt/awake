/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

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
            sorted.opaqueByPipeline.getValue(PipelineA).map { (it as Draw).label },
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
            sorted.transparent.map { (it as Draw).label },
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
        assertEquals(listOf("blended"), sorted.transparent.map { (it as Draw).label })
    }
}
