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
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.render.renderer.UniformWriter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The outline overlay draws only when a mask layer is styled to draw (#1709). A draw exempt from
 * coverings, an unlit mesh, puts a mask pass in every frame, outlined or not; the full-screen overlay
 * then read all 34 of its mask samples per pixel and wrote nothing.
 */
class OutlineOverlayDrawTest {
    @Test
    fun aCoveringExemptPassAloneDrawsNoOverlay() {
        assertEquals(0, draws(GpuSubPass(target = null, targetLayer = COVERING_EXEMPT_MASK_LAYER, viewProjection = Mat4())))
    }

    @Test
    fun aLayerStyledWithNoWidthOrNoColourDrawsNoOverlay() {
        assertEquals(0, draws(layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 0f))))
        assertEquals(0, draws(layer(1, OutlineStyle(Color(1f, 0f, 0f, 0f), widthPixels = 4f))))
    }

    @Test
    fun aStyledLayerDrawsTheOverlayOnce() {
        assertEquals(1, draws(layer(0, OutlineStyle(Color(1f, 0f, 0f, 1f), widthPixels = 4f))))
        assertEquals(
            1,
            draws(
                layer(1, OutlineStyle(Color(0f, 0f, 1f, 1f), widthPixels = 2f)),
                GpuSubPass(target = null, targetLayer = COVERING_EXEMPT_MASK_LAYER, viewProjection = Mat4()),
            ),
        )
    }

    private fun layer(layer: Int, style: OutlineStyle) =
        GpuSubPass(target = null, targetLayer = layer, viewProjection = Mat4(), passUniforms = style.packed())

    private fun draws(vararg masks: GpuSubPass): Int {
        val recorder = CountingRecorder()
        val context = FrameContext(recorder, GpuPassInput(viewProjection = Mat4(), cameraEye = Vec3f.ZERO, passUniforms = FloatArray(0), maskPasses = masks.toList()))
        OutlineRenderFeature(Pipeline, Uniforms).recordCommands(context)
        return recorder.draws
    }

    private class CountingRecorder : CommandRecorder {
        var draws = 0
        override fun bindPipeline(pipeline: PipelineHandle) = Unit
        override fun bindMaterial(semantic: BindingSemantic, binding: MaterialBinding) = Unit
        override fun bindVertexBuffer(binding: Int, buffer: BufferHandle) = Unit
        override fun bindIndexBuffer(buffer: BufferHandle) = Unit
        override fun draw(vertexCount: Int, instanceCount: Int) {
            draws++
        }
        override fun drawIndexed(indexCount: Int, instanceCount: Int) = Unit
    }

    private class FrameContext(override val recorder: CommandRecorder, override val passInput: GpuPassInput) : RenderFrameContext {
        override val frameIndex = 0
        override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>> = emptyMap()
        override val transparentDrawCalls: List<PreparedDraw> = emptyList()
        override val edgeDrawCalls: List<PreparedDraw> = emptyList()
        override val primaryPipeline: PipelineHandle = Pipeline
        override val viewProjection = Mat4()
        override val cameraEye = Vec3f.ZERO
        override val surfaceWidth = SIZE
        override val surfaceHeight = SIZE
    }

    private object Pipeline : PipelineHandle

    private object Binding : MaterialBinding

    private object Uniforms : UniformBlock {
        override fun binding(frameIndex: Int): MaterialBinding = Binding
        override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) = Unit
    }

    private companion object {
        const val SIZE = 64
    }
}
