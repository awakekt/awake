/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import kotlin.test.Test

/**
 * The compile of a frame whose copies fold into instanced draws, as the scene's own backends fold
 * them, step by step in the browser (#1621): 3,000 draws over 190 meshes fold into about 190
 * instanced draws. The scene planning breakdown, whose preparer also instances, still measures about
 * 170 B a renderer after the sorts stopped allocating, where [CompileHeapBreakdownBrowserTest]'s
 * unfolded frame takes 26: this splits the folded frame the same way, to find where.
 */
class InstancedCompileHeapBrowserTest {
    private val frame = CompileFrame(instancing = true)

    @Test
    fun instancedCompileHeapBreakdown() {
        if (!heapMeasurable()) return
        val scratch = ScenePassScratch()
        val canInstance = scratch.batcher.canInstanceWith(frame.preparer)
        val canTint = scratch.batcher.canTintWith(frame.preparer)
        val context = GpuDrawPreparationContext(viewProjection = frame.lens.viewProjectionMatrix(1f, ClipSpace.WebGpu), cameraEye = frame.lens.eye)
        val batched = scratch.batcher.batch(frame.requests, canInstance, canTint)
        val figures = linkedMapOf(
            "batch" to medianGrowth(DRAWS) { scratch.batcher.batch(frame.requests, canInstance, canTint) },
            "casterFold" to medianGrowth(DRAWS) { scratch.casters.fold(batched, true, canInstance) },
            "prepare" to medianGrowth(DRAWS) {
                scratch.casters.fold(batched, true, canInstance)
                scratch.draws.reset(emptyList())
                scratch.draws.prepare(batched, frame.preparer, context, scratch.casters)
            },
            "compileMoving" to medianGrowth(DRAWS) { frame.compile(scratch, null) },
        )
        val line = figures.entries.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" }
        println("InstancedCompileHeap: $DRAWS draws into ${batched.size} after folding, bytes a draw: $line")
    }

    private companion object {
        const val DRAWS = CompileFrame.DRAWS
    }
}
