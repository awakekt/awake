/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.COVERING_EXEMPT_MASK_LAYER
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.render.renderer.OutlineUniformLayout
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Per-frame zero-allocation and CPU timing gates for [OutlineRenderFeature] and
 * [CloudLayerRenderFeature] under `-XX:-DoEscapeAnalysis`.
 */
class OutlineAndCloudAllocationTest {
    @Test
    fun outlineRenderFeatureAllocatesNothingAcrossEffectQualities() {
        val recorder = CountingRecorder()
        val uniforms = ReusableUniforms(OutlineUniformLayout)
        val feature = OutlineRenderFeature(StandardPipeline, uniforms)
        val context = FrameContext(
            recorder = recorder,
            passInput = GpuPassInput(
                viewProjection = Mat4(),
                cameraEye = Vec3f.ZERO,
                passUniforms = FloatArray(0),
                maskPasses = sampleMaskPasses(),
            ),
        )

        for (quality in listOf(EffectQuality.High, EffectQuality.Medium, EffectQuality.Low, EffectQuality.Off)) {
            context.effectQuality = quality
            recorder.draws = 0
            var elapsedNs = 0L
            val reading = readAllocation(FRAMES_PER_WINDOW) {
                val start = System.nanoTime()
                repeat(FRAMES_PER_WINDOW) { feature.recordCommands(context) }
                elapsedNs = System.nanoTime() - start
            }
            val nsPerFrame = elapsedNs / FRAMES_PER_WINDOW
            println("OutlineRenderFeature[$quality]: $reading | $nsPerFrame ns/frame")
            assertAllocatesNothing(reading, "OutlineRenderFeature.recordCommands($quality)")
            if (quality == EffectQuality.Off) {
                assertEquals(0, recorder.draws, "EffectQuality.Off must record zero outline draws")
            } else {
                assertTrue(recorder.draws > 0, "EffectQuality.$quality must record outline draws")
            }
        }
    }

    private fun sampleMaskPasses(): List<GpuSubPass> {
        val bounds = ImagePassRect(0.25f, 0.25f, 0.25f, 0.25f)
        val clusterRects = floatArrayOf(0.05f, 0.35f, 0.15f, 0.55f, 0.80f, 0.35f, 0.90f, 0.55f)
        return listOf(
            GpuSubPass(
                target = null,
                targetLayer = 0,
                viewProjection = Mat4(),
                passUniforms = OutlineStyle(Color(1f, 0.85f, 0.4f, 0.95f), widthPixels = 10f, softness = 1f).packed(),
            ).also {
                it.screenBounds = ImagePassRect(0.05f, 0.35f, 0.85f, 0.20f)
                it.screenBoundRects = clusterRects
                it.screenBoundRectCount = 2
            },
            GpuSubPass(
                target = null,
                targetLayer = 1,
                viewProjection = Mat4(),
                passUniforms = OutlineStyle(Color(0.2f, 0.6f, 1f, 1f), widthPixels = 4f, softness = 0f).packed(),
            ).also { it.screenBounds = bounds },
            GpuSubPass(target = null, targetLayer = COVERING_EXEMPT_MASK_LAYER, viewProjection = Mat4()),
        )
    }

    @Test
    fun cloudLayerRenderFeatureAllocatesNothingInStandardAndFastTiers() {
        val recorder = CountingRecorder()
        val uniforms = ReusableUniforms(CloudLayerUniformLayout)
        val look = CloudLayerLook()
        val geometry = ContentGeometry(vertexBuffer = DummyBuffer, indexBuffer = DummyBuffer, elementCount = CLOUD_INDICES)
        val feature = CloudLayerRenderFeature(
            pipeline = StandardPipeline,
            uniforms = uniforms,
            geometry = geometry,
            look = look,
            fastPipeline = FastPipeline,
        )
        val context = FrameContext(recorder = recorder, passInput = null)

        for (quality in listOf(CloudLayerQuality.Standard, CloudLayerQuality.Fast)) {
            look.quality = quality
            recorder.indexedDraws = 0
            var elapsedNs = 0L
            val reading = readAllocation(FRAMES_PER_WINDOW) {
                val start = System.nanoTime()
                repeat(FRAMES_PER_WINDOW) {
                    look.timeSeconds += FRAME_DT
                    feature.recordCommands(context)
                }
                elapsedNs = System.nanoTime() - start
            }
            val nsPerFrame = elapsedNs / FRAMES_PER_WINDOW
            println("CloudLayerRenderFeature[$quality]: $reading | $nsPerFrame ns/frame")
            assertAllocatesNothing(reading, "CloudLayerRenderFeature.recordCommands($quality)")
            assertTrue(recorder.indexedDraws > 0, "CloudLayerRenderFeature.$quality must record draws")
            val expectedPipeline = if (quality == CloudLayerQuality.Fast) FastPipeline else StandardPipeline
            assertEquals(expectedPipeline, recorder.lastPipeline, "CloudLayerRenderFeature must bind $quality pipeline")
        }
    }

    private class CountingRecorder : CommandRecorder {
        var draws = 0
        var indexedDraws = 0
        var lastPipeline: PipelineHandle? = null
        override fun bindPipeline(pipeline: PipelineHandle) {
            lastPipeline = pipeline
        }
        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) = Unit
        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) = Unit
        override fun bindIndexBuffer(buffer: BufferHandle) = Unit
        override fun draw(vertexCount: Int, instanceCount: Int) {
            draws++
        }
        override fun drawIndexed(indexCount: Int, instanceCount: Int) {
            indexedDraws++
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

    private class FrameContext(
        override val recorder: CommandRecorder,
        override val passInput: GpuPassInput?,
    ) : RenderFrameContext {
        override var effectQuality: EffectQuality = EffectQuality.High
        override val frameIndex = 0
        override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
        override val transparentDrawCalls: List<PreparedDraw> = emptyList()
        override val edgeDrawCalls: List<PreparedDraw> = emptyList()
        override val primaryPipeline: PipelineHandle = StandardPipeline
        override val viewProjection = Mat4()
        override val cameraEye = Vec3f(0f, 12f, 24f)
        override val surfaceWidth = SURFACE_WIDTH
        override val surfaceHeight = SURFACE_HEIGHT
    }

    private object StandardPipeline : PipelineHandle

    private object FastPipeline : PipelineHandle

    private object DummyBinding : MaterialBinding

    private object DummyBuffer : BufferHandle

    private companion object {
        const val FRAMES_PER_WINDOW = 2_000
        const val CLOUD_INDICES = 96
        const val FRAME_DT = 0.016f
        const val SURFACE_WIDTH = 1280
        const val SURFACE_HEIGHT = 720
    }
}
