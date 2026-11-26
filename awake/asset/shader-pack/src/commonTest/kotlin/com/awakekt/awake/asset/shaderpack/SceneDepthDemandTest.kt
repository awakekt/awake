/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.AttachedContentSlot
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.GpuDebugView
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.LinePass
import com.awakekt.awake.render.passes.OpaqueRenderFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.renderer.UniformWriter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies dynamic [RenderFeature.needsSceneDepth] reporting across Core's scene-depth consumers
 * and container slots so backends skip the full-scene camera depth pre-pass whenever depth readers
 * are disabled, hidden, or running on a depth-free fast tier.
 */
class SceneDepthDemandTest {

    @Test
    fun depthFogRequestsSceneDepthOnlyWhenVisibleAndNotInDebugView() {
        var visible = true
        val fog = DepthFogRenderFeature(DummyPipeline, DummyUniforms, Color(0.5f, 0.6f, 0.7f, 0.02f)) { visible }
        assertTrue(fog.needsSceneDepth(GpuEnvironmentState.Default))
        assertFalse(fog.needsSceneDepth(GpuEnvironmentState(debugView = GpuDebugView(code = 1))))

        visible = false
        assertFalse(fog.needsSceneDepth(GpuEnvironmentState.Default))
    }

    @Test
    fun stylizedWaterSkipsSceneDepthOnFastQualityOrWhenDisabled() {
        val look = StylizedWaterLook(enabled = true, quality = StylizedWaterQuality.Standard)
        val waterWithFast = StylizedWaterRenderFeature(
            pipeline = DummyPipeline,
            uniforms = DummyUniforms,
            geometry = ContentGeometry(DummyBuffer, null, 6),
            look = look,
            samplesDepth = true,
            fastPipeline = DummyFastPipeline,
        )
        assertTrue(waterWithFast.needsSceneDepth(GpuEnvironmentState.Default))
        assertFalse(waterWithFast.needsSceneDepth(GpuEnvironmentState(debugView = GpuDebugView(code = 1))))

        look.quality = StylizedWaterQuality.Fast
        assertFalse(waterWithFast.needsSceneDepth(GpuEnvironmentState.Default))

        look.quality = StylizedWaterQuality.Standard
        look.enabled = false
        assertFalse(waterWithFast.needsSceneDepth(GpuEnvironmentState.Default))

        val waterWithoutDepth = StylizedWaterRenderFeature(
            pipeline = DummyPipeline,
            uniforms = DummyUniforms,
            geometry = ContentGeometry(DummyBuffer, null, 6),
            look = StylizedWaterLook(enabled = true, quality = StylizedWaterQuality.Standard),
            samplesDepth = false,
            fastPipeline = DummyFastPipeline,
        )
        assertFalse(waterWithoutDepth.needsSceneDepth(GpuEnvironmentState.Default))
    }

    @Test
    fun sunFlareRequestsSceneDepthOnlyWhenOccludedAndDrawable() {
        val look = SunFlareLook(enabled = true, intensity = 1f, sunVisibility = 1f)
        val occluded = SunFlareRenderFeature(DummyPipeline, DummyUniforms, look, occludeByDepth = true)
        assertTrue(occluded.needsSceneDepth(GpuEnvironmentState(showSky = true)))
        assertFalse(occluded.needsSceneDepth(GpuEnvironmentState(showSky = false)))
        assertFalse(occluded.needsSceneDepth(GpuEnvironmentState(showSky = true, debugView = GpuDebugView(code = 1))))

        look.intensity = 0f
        assertFalse(occluded.needsSceneDepth(GpuEnvironmentState(showSky = true)))

        look.intensity = 1f
        look.enabled = false
        assertFalse(occluded.needsSceneDepth(GpuEnvironmentState(showSky = true)))

        val unoccluded = SunFlareRenderFeature(
            DummyPipeline,
            DummyUniforms,
            SunFlareLook(enabled = true, intensity = 1f, sunVisibility = 1f),
            occludeByDepth = false,
        )
        assertFalse(unoccluded.needsSceneDepth(GpuEnvironmentState(showSky = true)))
    }

    @Test
    fun opaqueFeatureAndAttachedSlotPropagateSceneDepthDemand() {
        var needs = false
        val probe = object : RenderFeature<RenderFrameContext> {
            override val pass = RenderPassSlot.Scene
            override fun needsSceneDepth(environment: GpuEnvironmentState): Boolean = needs
            override fun recordCommands(context: RenderFrameContext) = Unit
            override fun destroy() = Unit
        }
        val slot = AttachedContentSlot()
        val opaque = OpaqueRenderFeature(
            lines = NoopLinePass,
            beforeTransparent = listOf(probe),
            opaqueGeometry = listOf(slot),
        )
        assertFalse(opaque.needsSceneDepth(GpuEnvironmentState.Default))
        needs = true
        assertTrue(opaque.needsSceneDepth(GpuEnvironmentState.Default))
        needs = false
        assertFalse(opaque.needsSceneDepth(GpuEnvironmentState.Default))
    }

    private object DummyPipeline : PipelineHandle
    private object DummyFastPipeline : PipelineHandle
    private object DummyBuffer : BufferHandle
    private object DummyBinding : MaterialBinding

    private object DummyUniforms : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = DummyBinding
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private object NoopLinePass : LinePass<RenderFrameContext> {
        override fun writeMvp(frameIndex: Int, mvp: FloatArray) = Unit
        override fun lineDraw(context: RenderFrameContext): PreparedDraw? = null
        override fun destroy() = Unit
    }
}
