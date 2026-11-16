/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.geometry.VertexFormat
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
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Where a 3,000-draw frame's compile allocates in the browser, step by step (#1621). On the JVM with
 * escape analysis off it allocates about 1.4 KB in all ([PerDrawAllocationTest]); in the browser the
 * scene planning breakdown measured about 170 B a draw. Each step runs on its own here, over the
 * same frame, with a preparer that hands back draws it made once.
 */
class CompileHeapBreakdownBrowserTest {
    private val frame = CompileFrame(instancing = false)
    private val preparer = frame.preparer
    private val resolved = frame.resolved
    private val requests = frame.requests
    private val light = frame.light
    private val environment = frame.environment

    @Test
    fun compileHeapBreakdown() {
        if (!heapMeasurable()) {
            println("CompileHeapBreakdown: skipped, the browser cannot collect on demand")
            return
        }
        val scratch = ScenePassScratch()
        val stats = SceneDrawStats()
        val canInstance = scratch.batcher.canInstanceWith(preparer)
        val canTint = scratch.batcher.canTintWith(preparer)
        val context = GpuDrawPreparationContext(viewProjection = frame.lens.viewProjectionMatrix(1f, ClipSpace.WebGpu), cameraEye = frame.lens.eye)
        val cascades = shadowCascadeData(light, environment)?.viewProjections.orEmpty()
        val batched = scratch.batcher.batch(requests, canInstance, canTint)
        val sorter = StableListSort()
        val sortable = ArrayList<GpuResolvedDraw>(DRAWS)
        val figures = linkedMapOf(
            "preparerOnly" to medianGrowth(DRAWS) { for (i in requests.indices) preparer.prepare(requests[i], i, context) },
            "lambdaPreparerOnly" to medianGrowth(DRAWS) { for (i in requests.indices) frame.lambdaPreparer.prepare(requests[i], i, context) },
            "signature" to medianGrowth(DRAWS) { scratch.signature.record(frame.moved(), requests, light, environment, ClipSpace.WebGpu, 1f, null, emptyList(), ImagePassChain.Empty) },
            "batch" to medianGrowth(DRAWS) { scratch.batcher.batch(requests, canInstance, canTint) },
            "diagnose" to medianGrowth(DRAWS) { diagnoseInstanceFolds(requests, canInstance, stats.also { it.reset() }, canTint) },
            "casterFold" to medianGrowth(DRAWS) { scratch.casters.fold(batched, true, canInstance) },
            "prepare" to medianGrowth(DRAWS) { prepare(scratch, batched, context) },
            "prepareAndShadowPasses" to medianGrowth(DRAWS) {
                prepare(scratch, batched, context)
                scratch.draws.shadowPrePasses(cascades, true, light, environment)
            },
            "prepareAndMaskPasses" to medianGrowth(DRAWS) {
                prepare(scratch, batched, context)
                scratch.draws.maskSubPasses(context.viewProjection)
            },
            "sortTransparent" to medianGrowth(DRAWS) { sortCopy(sorter, sortable, com.awakekt.awake.render.command.TransparentOrder) },
            "sortBatchKey" to medianGrowth(DRAWS) { sortCopy(sorter, sortable, BatchKeyOrder) },
            "sortInline" to medianGrowth(DRAWS) {
                sortable.clear()
                sortable.addAll(resolved)
                sorter.sortWith(sortable) { a, b -> com.awakekt.awake.render.command.compareTransparent(a, b) }
            },
            "refillOnly" to medianGrowth(DRAWS) {
                sortable.clear()
                sortable.addAll(resolved)
            },
            "comparatorCalls" to medianGrowth(DRAWS) { for (i in 0 until DRAWS) sink += BatchKeyOrder.compare(resolved[i], resolved[DRAWS - 1 - i]) },
            "inIndices" to medianGrowth(DRAWS) { for (i in 0 until DRAWS) if ((i and 7) in maskLayers.indices) sink++ },
            "compareFloats" to medianGrowth(DRAWS) { for (i in 0 until DRAWS) sink += resolved[i].depthSortKey.compareTo(resolved[DRAWS - 1 - i].depthSortKey) },
            "compileMoving" to medianGrowth(DRAWS) { frame.compile(scratch, null) },
            "compileMovingWithStats" to medianGrowth(DRAWS) { frame.compile(scratch, stats) },
        )
        val line = figures.entries.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" }
        println("CompileHeapBreakdown: $DRAWS draws, bytes a draw: $line")
        val compile = figures.getValue("compileMoving")
        assertTrue(compile <= COMPILE_BUDGET, "a moving camera's compile took $compile B a draw, over $COMPILE_BUDGET")
    }

    private var sink = 0
    private val maskLayers = List(3) { FloatArray(4) }

    /** Refills [into] from the draws in their made order, then sorts it as preparation does. */
    private fun sortCopy(sorter: StableListSort, into: ArrayList<GpuResolvedDraw>, order: Comparator<in GpuResolvedDraw>) {
        into.clear()
        into.addAll(resolved)
        sorter.sort(into, order)
    }

    private fun prepare(scratch: ScenePassScratch, batched: List<RenderDrawCommand>, context: GpuDrawPreparationContext) {
        scratch.casters.fold(batched, true, scratch.batcher.canInstanceWith(preparer))
        scratch.draws.reset(emptyList())
        scratch.draws.prepare(batched, preparer, context, scratch.casters)
    }

    private companion object {
        val BatchKeyOrder: Comparator<GpuResolvedDraw> = Comparator { a, b -> a.batchKey.compareTo(b.batchKey) }
        const val DRAWS = CompileFrame.DRAWS

        /** A moving camera's compile, bytes a draw: 284 before the sorts inlined their comparison, 26 after. */
        const val COMPILE_BUDGET = 48.0
    }
}
