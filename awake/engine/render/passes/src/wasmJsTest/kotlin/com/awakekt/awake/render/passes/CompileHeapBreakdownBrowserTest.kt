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

/**
 * Where a 3,000-draw frame's compile allocates in the browser, step by step (#1621). On the JVM with
 * escape analysis off it allocates about 1.4 KB in all ([PerDrawAllocationTest]); in the browser the
 * scene planning breakdown measured about 170 B a draw. Each step runs on its own here, over the
 * same frame, with a preparer that hands back draws it made once.
 */
class CompileHeapBreakdownBrowserTest {
    private val pipelines = List(PIPELINES) { object : PipelineHandle {} }
    private val handle = object : MaterialBinding, BufferHandle {}
    private val meshes = List(MESHES) {
        object : Mesh {
            override val format = VertexFormat.PositionNormalColor
            override val sizeBytes = 0L
            override fun destroy() = Unit
        }
    }
    private val material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
    private val resolved = List(DRAWS) { index ->
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
    private val preparer = object : GpuDrawPreparer {
        override fun prepare(request: GpuDrawRequest, sourceIndex: Int, context: GpuDrawPreparationContext): GpuResolvedDraw =
            resolved[sourceIndex % resolved.size]
    }
    private val requests = List(DRAWS) { index ->
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
    private val lens = Lens(eye = Vec3f(0f, 30f, 60f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 400f)
    private val sun = SceneLight(direction = Vec3f(0.4f, 1f, 0.3f), color = Vec3f(1f, 1f, 1f))
    private val light = sun.copy(cascades = shadowCascadeUniforms(sun, lens, 1f, ClipSpace.WebGpu))
    private val environment = EnvironmentUniforms.Default
    private var step = 0

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
        val context = GpuDrawPreparationContext(viewProjection = lens.viewProjectionMatrix(1f, ClipSpace.WebGpu), cameraEye = lens.eye)
        val cascades = shadowCascadeData(light, environment)?.viewProjections.orEmpty()
        val batched = scratch.batcher.batch(requests, canInstance, canTint)
        val sorter = StableListSort()
        val sortable = ArrayList<GpuResolvedDraw>(DRAWS)
        val figures = linkedMapOf(
            "preparerOnly" to medianGrowth(DRAWS) { for (i in requests.indices) preparer.prepare(requests[i], i, context) },
            "signature" to medianGrowth(DRAWS) { scratch.signature.record(moved(), requests, light, environment, ClipSpace.WebGpu, 1f, null, emptyList(), ImagePassChain.Empty) },
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
            "inIndices" to medianGrowth(DRAWS) { for (i in 0 until DRAWS) if ((i and 7) in maskLayers.indices) sink++ },
            "compareFloats" to medianGrowth(DRAWS) { for (i in 0 until DRAWS) sink += resolved[i].depthSortKey.compareTo(resolved[DRAWS - 1 - i].depthSortKey) },
            "compileMoving" to medianGrowth(DRAWS) { compile(scratch, null) },
            "compileMovingWithStats" to medianGrowth(DRAWS) { compile(scratch, stats) },
        )
        val line = figures.entries.joinToString { (name, bytes) -> "$name=${bytes.toInt()}" }
        println("CompileHeapBreakdown: $DRAWS draws, bytes a draw: $line")
    }

    private var sink = 0
    private val maskLayers = List(3) { FloatArray(4) }

    /** Refills [into] from the draws in their made order, then sorts it as preparation does. */
    private fun sortCopy(sorter: StableListSort, into: ArrayList<GpuResolvedDraw>, order: Comparator<in GpuResolvedDraw>) {
        into.clear()
        into.addAll(resolved)
        sorter.sort(into, order)
    }

    private fun moved(): Lens = lens.also { it.eye = Vec3f(step++ * 0.01f, 30f, 60f) }

    private fun prepare(scratch: ScenePassScratch, batched: List<RenderDrawCommand>, context: GpuDrawPreparationContext) {
        scratch.casters.fold(batched, true, scratch.batcher.canInstanceWith(preparer))
        scratch.draws.reset(emptyList())
        scratch.draws.prepare(batched, preparer, context, scratch.casters)
    }

    private fun compile(scratch: ScenePassScratch, stats: SceneDrawStats?) = ScenePassCompiler.compile(
        moved(),
        requests,
        light,
        environment,
        ClipSpace.WebGpu,
        1f,
        null,
        preparer,
        emptyList(),
        ImagePassChain.Empty,
        stats,
        scratch,
    )

    private companion object {
        val BatchKeyOrder: Comparator<GpuResolvedDraw> = Comparator { a, b -> a.batchKey.compareTo(b.batchKey) }
        const val DRAWS = 3_000
        const val MESHES = 190
        const val PIPELINES = 8
        const val ROW = 60
        const val SPACING = 4f
    }
}
