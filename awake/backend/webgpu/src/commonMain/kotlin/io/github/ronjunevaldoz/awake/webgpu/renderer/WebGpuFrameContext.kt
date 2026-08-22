// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.core.math.Mat4
import io.github.ronjunevaldoz.awake.core.math.Vec3f
import io.github.ronjunevaldoz.awake.render.command.CommandRecorder
import io.github.ronjunevaldoz.awake.render.command.PipelineHandle
import io.github.ronjunevaldoz.awake.render.command.PreparedDraw
import io.github.ronjunevaldoz.awake.render.passes2d.UiRun
import io.github.ronjunevaldoz.awake.render.renderer.SceneLight
import io.github.ronjunevaldoz.awake.webgpu.debug.LineMesh
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuCommandRecorder
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuRenderFrameContext
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuUiPipelineSet
import io.github.ronjunevaldoz.awake.webgpu.ui.DynamicMesh
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
    override val light: SceneLight,
    override val surfaceWidth: Int,
    override val surfaceHeight: Int,
) : WebGpuRenderFrameContext {

    /** Single-buffered on this backend; the ports still take it because Vulkan's resources are
     * per-frame-in-flight. */
    override val frameIndex: Int get() = 0

    override val showEnvironment: Boolean get() = renderer.showEnvironment
    override val horizonColor: Color get() = renderer.horizonColor
    override val zenithColor: Color get() = renderer.zenithColor

    override val recorder: CommandRecorder = WebGpuCommandRecorder(encoder)

    override val lineMesh: LineMesh get() = renderer.lineMesh
    override val uiRuns: List<UiRun<DynamicMesh>> get() = renderer.uiRuns

    override fun uiPipelines(): WebGpuUiPipelineSet? {
        val quad = renderer.uiRenderPipeline ?: return null
        return WebGpuUiPipelineSet(
            quad = quad,
            glyph = renderer.uiGlyphRenderPipeline,
            texture = renderer.uiTextureRenderPipeline,
            roundedQuad = renderer.uiRoundedQuadRenderPipeline,
        )
    }

    override fun textureMeshForPrimitive(index: Int): DynamicMesh =
        renderer.textureMeshForPrimitive(index)
}
