/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.webgpu.debug.LineMesh
import com.awakekt.awake.webgpu.pipeline.WebGpuCommandRecorder
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuRenderFrameContext
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineSet
import com.awakekt.awake.webgpu.pipeline.hasBindingGroup
import com.awakekt.awake.webgpu.ui.DynamicMesh
import io.ygdrasil.webgpu.GPURenderPassEncoder

/**
 * The one adapter bridging `Renderer`'s internals to the feature port, mirroring Vulkan's
 * `RendererFrameContext`. Built once per pass rather than once per frame -- see
 * [WebGpuRenderFrameContext] for why.
 */
@Suppress("LongParameterList")
internal class WebGpuFrameContext(
    private val renderer: Renderer,
    override val encoder: GPURenderPassEncoder,
    override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>>,
    override val transparentDrawCalls: List<PreparedDraw> = emptyList(),
    override val primaryPipeline: PipelineHandle,
    override val viewProjection: Mat4,
    override val cameraEye: Vec3f,
    override val environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    override val surfaceWidth: Int,
    override val surfaceHeight: Int,
    override val passInput: GpuPassInput? = null,
) : WebGpuRenderFrameContext {

    /** Single-buffered on this backend; the ports still take it because Vulkan's resources are
     * per-frame-in-flight. */
    override val frameIndex: Int get() = 0

    override val stats: RenderStatsCounter get() = renderer.statsCounter

    override val recorder: CommandRecorder = WebGpuCommandRecorder(encoder, renderer.statsCounter)

    /**
     * Built against [pipeline]'s own group-2 layout, and cached there -- a bind group belongs to
     * one pipeline layout on this backend, unlike Vulkan's set, which is why this takes a
     * pipeline at all. Null when the app opted into no scene-depth pass.
     */
    override fun sceneDepthBinding(pipeline: PipelineHandle): MaterialBinding? {
        val depthTarget = renderer.sceneDepthPass?.depthTarget ?: return null
        return renderer.bufferPools.sceneDepthBindingFor(pipeline as WebGpuPipelineHandle, depthTarget)
    }

    /** The engine's target for [semantic] as a group built against [pipeline]'s layout, or null
     * when the pipeline declares no such group. The depth pre-pass target falls back to
     * [Renderer.depthPrePassPlaceholder] when the plan has no pre-pass. */
    override fun engineBinding(pipeline: PipelineHandle, semantic: BindingSemantic): MaterialBinding? {
        val handle = pipeline as WebGpuPipelineHandle
        if (!handle.hasBindingGroup(handle.bindingLayout.slot(semantic))) return null
        return when (semantic) {
            BindingSemantic.ShadowDepth -> (renderer.depthPrePass?.depthTarget ?: renderer.depthPrePassPlaceholder)
                ?.let { renderer.bufferPools.shadowBindingFor(handle, it) }
            BindingSemantic.SceneDepth -> renderer.sceneDepthPass?.depthTarget
                ?.let { renderer.bufferPools.sceneDepthBindingFor(handle, it) }
            else -> null
        }
    }

    override val lineMesh: LineMesh get() = renderer.lineMesh
    override val uiRuns: List<UiRun<DynamicMesh>> get() = renderer.uiRuns

    override fun uiPipelines(): WebGpuUiPipelineSet? {
        val quad = renderer.uiRenderPipeline ?: return null
        return WebGpuUiPipelineSet(
            quad = quad,
            glyph = renderer.uiGlyphRenderPipeline,
            textures = renderer.uiTextureRenderPipelines,
            roundedQuad = renderer.uiRoundedQuadRenderPipeline,
        )
    }

    override fun textureMeshForPrimitive(index: Int): DynamicMesh =
        renderer.textureMeshForPrimitive(index)
}
