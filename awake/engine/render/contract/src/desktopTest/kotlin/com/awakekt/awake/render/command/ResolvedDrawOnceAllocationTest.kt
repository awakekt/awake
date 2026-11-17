/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A resolver that knows a draw's depth-pass fields once it has prepared it builds the resolved draw
 * once with them. Building it and then copying it to set them made two or three packets per draw per
 * frame, about 6% of a populated scene's frame garbage on Vulkan.
 */
class ResolvedDrawOnceAllocationTest {
    private val pipeline = object : PipelineHandle {}
    private val depthPipeline = object : PipelineHandle {}
    private val handle = object : MaterialBinding, BufferHandle {}
    private val depthBinding = object : MaterialBinding {}
    private val key = DepthRenderKey.of(DepthCasterKind.entries.first(), AlphaMode.Masked)
    private val prepared: PreparedDraw = GpuResolvedDraw(
        pipeline = pipeline,
        materialBinding = handle,
        vertexBuffer = handle,
        indexBuffer = null,
        elementCount = 36,
        depthSortKey = 3f,
        batchKey = 7,
        sortLayer = 2,
    )

    @Test
    fun buildingOnceGivesWhatBuildingThenCopyingGave() {
        assertEquals(prepared.toGpuResolvedDraw().copy(depthRenderKey = key), prepared.toGpuResolvedDraw(depthRenderKey = key))
        assertEquals(
            prepared.toGpuResolvedDraw().copy(depthRenderKey = key).copy(
                depthRenderKey = key,
                alphaCutoff = 0.25f,
                depthPipeline = depthPipeline,
                depthMaterialBinding = depthBinding,
                depthJointPaletteBinding = depthBinding,
            ),
            prepared.toGpuResolvedDraw(key, 0.25f, depthPipeline, depthBinding, depthBinding),
        )
    }

    @Test
    fun aDrawWithItsDepthFieldsIsOnePacket() {
        val once = bytesPerDraw { prepared.toGpuResolvedDraw(key, 0.25f, depthPipeline, depthBinding, depthBinding) }
        val onePacket = bytesPerDraw { prepared.toGpuResolvedDraw() }
        val copied = bytesPerDraw {
            prepared.toGpuResolvedDraw().copy(depthRenderKey = key)
                .copy(depthRenderKey = key, alphaCutoff = 0.25f, depthPipeline = depthPipeline, depthMaterialBinding = depthBinding)
        }
        println("PERF resolved draw with depth fields: $once B built once, $copied B built then copied twice, one packet $onePacket B")
        assertTrue(once <= onePacket, "a draw with its depth fields allocated $once B, more than the $onePacket B of one packet")
    }

    /** Kept in a field, so the packet a call returns escapes and is counted. */
    private var sink: GpuResolvedDraw? = null

    private inline fun bytesPerDraw(build: () -> GpuResolvedDraw): Long {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        repeat(WARMUP) { sink = build() }
        val start = threads.currentThreadAllocatedBytes
        repeat(COUNT) { sink = build() }
        return (threads.currentThreadAllocatedBytes - start) / COUNT
    }

    private companion object {
        const val WARMUP = 20_000
        const val COUNT = 100_000
    }
}
