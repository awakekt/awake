/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapConfig
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapGeometry
import com.awakekt.awake.asset.terrain.clipmap.TerrainClipmapTracker
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies per-block clipmap frustum culling, out-of-footprint skipping, contiguous range
 * coalescing, clip-space consistency, and zero per-frame allocations in [TerrainRenderFeature].
 */
class TerrainClipmapCullingAllocationTest {

    @Test
    fun frustumCullingCutsBehindCameraBlocksAndCoalescesAdjacentRunsAcrossAllClipSpaces() {
        val config = TerrainClipmapConfig.Mobile
        val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
        val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
        val totalIndices = mesh.indices.size

        for (clipSpace in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu, ClipSpace.OpenGl)) {
            val recorder = TrackingRecorder()
            val feature = makeFeature(config, totalIndices, coreIndices, footprint = 2048f)
            val lens = Lens(
                eye = Vec3f(0f, 15f, 0f),
                center = Vec3f(0f, 10f, -100f),
                fovYRadians = 1.047f,
                near = 0.5f,
                far = 1200f,
            )
            val context = StubFrameContext(
                recorder = recorder,
                viewProjection = lens.viewProjectionMatrix(aspect = 16f / 9f, clipSpace = clipSpace),
                cameraEye = lens.eye,
            )

            feature.recordCommands(context)

            assertTrue(recorder.totalIndicesDrawn > 0, "$clipSpace: visible terrain ahead must draw")
            assertTrue(
                recorder.totalIndicesDrawn < (totalIndices * 0.65).toInt(),
                "$clipSpace: horizontal perspective view must cull >35% of indices (drew ${recorder.totalIndicesDrawn} / $totalIndices)",
            )
            assertTrue(
                recorder.indexedCalls < 24,
                "$clipSpace: adjacent visible blocks should coalesce into compact draw runs (calls=${recorder.indexedCalls})",
            )
        }
    }

    @Test
    fun cameraLookingAwayFromBoundedTerrainSkipsAllDrawsAndPipelineBinds() {
        val config = TerrainClipmapConfig.Mobile
        val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
        val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
        val recorder = TrackingRecorder()
        // Small 128m island centred at origin; camera sits 300m south looking further south.
        val feature = makeFeature(config, mesh.indices.size, coreIndices, footprint = 128f)
        val lens = Lens(
            eye = Vec3f(0f, 10f, 300f),
            center = Vec3f(0f, 10f, 400f),
            fovYRadians = 1.0f,
            near = 0.5f,
            far = 500f,
        )
        val context = StubFrameContext(
            recorder = recorder,
            viewProjection = lens.viewProjectionMatrix(aspect = 16f / 9f, clipSpace = ClipSpace.Vulkan),
            cameraEye = lens.eye,
        )

        feature.recordCommands(context)

        assertEquals(0, recorder.indexedCalls, "completely behind-camera terrain must emit 0 draw calls")
        assertEquals(0, recorder.pipelineBinds, "completely culled terrain must emit 0 pipeline binds")
    }

    @Test
    fun identityProjectionFallsBackConservativelyToUnculledDraw() {
        val config = TerrainClipmapConfig.Compact
        val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
        val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
        val recorder = TrackingRecorder()
        val feature = makeFeature(config, mesh.indices.size, coreIndices, footprint = 512f)
        val context = StubFrameContext(
            recorder = recorder,
            viewProjection = Mat4(),
            cameraEye = Vec3f(0f, 20f, 0f),
        )

        feature.recordCommands(context)

        assertEquals(mesh.indices.size, recorder.totalIndicesDrawn, "identity stub viewProjection must draw full clipmap")
        assertEquals(2, recorder.indexedCalls, "splitCore unculled fallback should emit core + ring draws")
    }

    @Test
    fun culledTerrainRecordCommandsAllocatesZeroBytesPerFrame() {
        val config = TerrainClipmapConfig.Mobile
        val mesh = TerrainClipmapGeometry.buildMergedClipmapMesh(config)
        val coreIndices = TerrainClipmapGeometry.coreIndexCount(config)
        val recorder = TrackingRecorder()
        val feature = makeFeature(config, mesh.indices.size, coreIndices, footprint = 1024f)
        val lens = Lens(
            eye = Vec3f(12f, 18f, -8f),
            center = Vec3f(12f, 14f, -120f),
            fovYRadians = 1.0f,
            near = 0.5f,
            far = 800f,
        )
        val context = StubFrameContext(
            recorder = recorder,
            viewProjection = lens.viewProjectionMatrix(aspect = 16f / 9f, clipSpace = ClipSpace.Vulkan),
            cameraEye = lens.eye,
        )

        // Warm up initial ring snap before measuring steady-state zero-alloc recording.
        feature.recordCommands(context)

        val reading = readAllocation(FRAMES_PER_WINDOW) {
            repeat(FRAMES_PER_WINDOW) { feature.recordCommands(context) }
        }
        assertAllocatesNothing(reading, "TerrainRenderFeature.recordCommands(culled)")
        assertTrue(recorder.totalIndicesDrawn > 0)
    }

    private fun makeFeature(
        config: TerrainClipmapConfig,
        totalIndices: Int,
        coreIndices: Int,
        footprint: Float,
    ): TerrainRenderFeature = TerrainRenderFeature(
        pipeline = RingPipeline,
        uniforms = ReusableUniforms(TerrainUniformLayout.Draw),
        geometry = ContentGeometry(DummyBuffer, DummyBuffer, totalIndices),
        tracker = TerrainClipmapTracker(config),
        heightScale = 40f,
        heightBias = 0f,
        sampling = floatArrayOf(footprint, footprint, 1f / 256f, 1f / 256f),
        isVisible = { true },
        pagedTerrain = null,
        corePipeline = CorePipeline,
        coreIndexCount = coreIndices,
        origin = { ZERO_ORIGIN },
    )

    private class TrackingRecorder : CommandRecorder {
        var indexedCalls = 0
        var totalIndicesDrawn = 0
        var pipelineBinds = 0
        override fun bindPipeline(pipeline: PipelineHandle) {
            pipelineBinds++
        }
        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) = Unit
        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) = Unit
        override fun bindIndexBuffer(buffer: BufferHandle) = Unit
        override fun draw(vertexCount: Int, instanceCount: Int) = Unit
        override fun drawIndexed(indexCount: Int, instanceCount: Int) {
            indexedCalls++
            totalIndicesDrawn += indexCount
        }
        override fun drawIndexed(indexCount: Int, instanceCount: Int, firstIndex: Int) {
            indexedCalls++
            totalIndicesDrawn += indexCount
        }
    }

    private class ReusableUniforms(layout: UniformLayout) : UniformBlock {
        private val writer = UniformWriter(layout)
        override fun binding(frameIndex: Int): MaterialBinding = DummyBinding
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
            writer.reset().apply(fill)
            writer.build()
        }
    }

    private class StubFrameContext(
        override val recorder: CommandRecorder,
        override val viewProjection: Mat4,
        override val cameraEye: Vec3f,
    ) : RenderFrameContext {
        override val frameIndex = 0
        override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
        override val primaryPipeline: PipelineHandle = RingPipeline
        override val surfaceWidth = 1280
        override val surfaceHeight = 720
    }

    private object CorePipeline : PipelineHandle
    private object RingPipeline : PipelineHandle
    private object DummyBinding : MaterialBinding
    private object DummyBuffer : BufferHandle

    private companion object {
        const val FRAMES_PER_WINDOW = 2_000
        val ZERO_ORIGIN = Vec3f(0f, 0f, 0f)
    }
}
