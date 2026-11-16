/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.test.Test

/**
 * A moving camera's compile, bytes a draw, as the frame takes on the scene town's shape one trait at a
 * time (#1621): its own material per mesh, no blended draws, and draws that only cast. The scene's
 * own draws compile at about 170 B a draw, the instanced synthetic frame at 17; this names which
 * trait carries the difference.
 */
class CompileShapeHeapBrowserTest {
    @Test
    fun compileHeapByFrameShape() {
        if (!heapMeasurable()) return
        val shapes = linkedMapOf(
            "instanced" to CompileFrame(instancing = true),
            "distinctMaterials" to CompileFrame(instancing = true, distinctMaterials = true),
            "noneBlended" to CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0),
            "halfShadowOnly" to CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0, shadowsOnlyEvery = 2),
            "halfShadowOnlyUnfolded" to CompileFrame(instancing = false, distinctMaterials = true, transparentEvery = 0, shadowsOnlyEvery = 2),
            "atOrigin" to CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0, atOrigin = true),
            "atOriginUnfolded" to CompileFrame(instancing = false, distinctMaterials = true, transparentEvery = 0, atOrigin = true),
        )
        val line = shapes.entries.joinToString { (name, frame) ->
            val scratch = ScenePassScratch()
            "$name=${medianGrowth(CompileFrame.DRAWS) { frame.compile(scratch, null) }.toInt()}"
        }
        println("CompileShapeHeap: ${CompileFrame.DRAWS} draws, a moving camera's compile, bytes a draw: $line")
        println("CompileShapeHeap at the origin, by step, bytes a draw: " + steps(CompileFrame(instancing = true, distinctMaterials = true, transparentEvery = 0, atOrigin = true)))
    }

    /** The compile's own steps over [frame], as [CompileHeapBreakdownBrowserTest] splits them. */
    private fun steps(frame: CompileFrame): String {
        val scratch = ScenePassScratch()
        val canInstance = scratch.batcher.canInstanceWith(frame.preparer)
        val canTint = scratch.batcher.canTintWith(frame.preparer)
        val lens = frame.lens
        val context = com.awakekt.awake.render.command.GpuDrawPreparationContext(
            viewProjection = lens.viewProjectionMatrix(1f, com.awakekt.awake.core.math.ClipSpace.WebGpu),
            cameraEye = lens.eye,
        )
        val cascades = shadowCascadeData(frame.light, frame.environment)
        val batched = scratch.batcher.batch(frame.requests, canInstance, canTint)
        val parts = linkedMapOf(
            "reach" to medianGrowth(CompileFrame.DRAWS) { cascades?.reachingCasters(frame.requests, com.awakekt.awake.core.math.ClipSpace.WebGpu) },
            "batch" to medianGrowth(CompileFrame.DRAWS) { scratch.batcher.batch(frame.requests, canInstance, canTint) },
            "casterFold" to medianGrowth(CompileFrame.DRAWS) { scratch.casters.fold(batched, true, canInstance) },
            "prepare" to medianGrowth(CompileFrame.DRAWS) {
                scratch.casters.fold(batched, true, canInstance)
                scratch.draws.reset(emptyList())
                scratch.draws.prepare(batched, frame.preparer, context, scratch.casters)
            },
            "prepareAndShadowPasses" to medianGrowth(CompileFrame.DRAWS) {
                scratch.casters.fold(batched, true, canInstance)
                scratch.draws.reset(emptyList())
                scratch.draws.prepare(batched, frame.preparer, context, scratch.casters)
                scratch.draws.shadowPrePasses(cascades?.viewProjections.orEmpty(), true, frame.light, frame.environment)
            },
        )
        return parts.entries.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" }
    }
}
