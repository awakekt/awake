/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.webgpu.debug.LineMesh
import com.awakekt.awake.webgpu.pipeline.WebGpuCommandRecorder
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuRenderFrameContext
import com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineSet
import com.awakekt.awake.webgpu.ui.DynamicMesh
import io.ygdrasil.webgpu.GPURenderPassEncoder

/**
 * The one adapter bridging `Renderer`'s internals to the feature port, mirroring Vulkan's
 * `RendererFrameContext`. Built once per pass rather than once per frame -- see
 * [WebGpuRenderFrameContext] for why.
 */
internal class WebGpuFrameContext(
    private val renderer: Renderer,
    override val encoder: GPURenderPassEncoder,
    override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>>,
    override val transparentDrawCalls: List<PreparedDraw> = emptyList(),
    override val primaryPipeline: PipelineHandle,
    override val viewProjection: Mat4,
    override val cameraEye: Vec3f,
    override val environmentState: GpuEnvironmentState = GpuEnvironmentState.Default,
    override val surfaceWidth: Int,
    override val surfaceHeight: Int,
) : WebGpuRenderFrameContext {

    /** Single-buffered on this backend; the ports still take it because Vulkan's resources are
     * per-frame-in-flight. */
    override val frameIndex: Int get() = 0

    override val showEnvironment: Boolean get() = environmentState.showSky
    override val horizonColor: Color get() = environmentState.horizonColor
    override val zenithColor: Color get() = environmentState.zenithColor

    override val recorder: CommandRecorder = WebGpuCommandRecorder(encoder)

    /**
     * Built against [pipeline]'s own group-2 layout, and cached there -- a bind group belongs to
     * one pipeline layout on this backend, unlike Vulkan's set, which is why this takes a
     * pipeline at all. Null when the app opted into no scene-depth pass.
     */
    override fun sceneDepthBinding(pipeline: PipelineHandle): MaterialBinding? {
        val depthTarget = renderer.sceneDepthPass?.depthTarget ?: return null
        return renderer.bufferPools.sceneDepthBindingFor(pipeline as WebGpuPipelineHandle, depthTarget)
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
