/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.webgpu.debug.LineMesh
import com.awakekt.awake.webgpu.ui.DynamicMesh
import com.awakekt.awake.webgpu.ui.UiRenderPipeline
import io.ygdrasil.webgpu.GPURenderPassEncoder

/**
 * What a WebGPU feature sees on top of the shared [RenderFrameContext] -- the mirror of Vulkan's
 * `VulkanRenderFrameContext`.
 *
 * Built once per *pass* rather than once per frame, unlike Vulkan's. A WebGPU render pass encoder
 * is created by `beginRenderPass` and is only valid inside it, so the scene pass and the UI pass
 * cannot share one; Vulkan records both into the same command buffer and can. Both are still a
 * read-only snapshot of already-staged frame state, which is what the port actually promises.
 */
internal interface WebGpuRenderFrameContext : RenderFrameContext {
    /** This pass's encoder. [recorder] wraps it for anything the shared port already covers;
     * the UI features need it directly for bind groups the port does not model. */
    val encoder: GPURenderPassEncoder

    /** Staged before the frame by `drawDebugLines`/`drawUi` respectively -- consumed here. */
    val lineMesh: LineMesh
    val uiRuns: List<UiRun<DynamicMesh>>

    /** This frame's UI pipeline set, or `null` when nothing has ever called `drawUi`. A method,
     * not a field: the underlying pipelines are rebuilt on resize and must be read fresh. */
    fun uiPipelines(): WebGpuUiPipelineSet?

    /** Stateful pooled `DynamicMesh` allocator, one per textured primitive -- see
     * `GpuBufferPoolManager.textureMeshForPrimitive` for the contract this preserves. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh
}

/** The lazily built UI pipelines, snapshotted for one feature call -- mirrors Vulkan's
 * `UiPipelineSet`. [quad] is non-null by construction (it is what "UI exists this frame" means);
 * the other three are built only once a frame actually contains that kind of primitive. */
internal class WebGpuUiPipelineSet(
    val quad: UiRenderPipeline,
    val glyph: UiRenderPipeline?,
    val textures: Map<TextureCompositeMode, UiRenderPipeline>,
    val roundedQuad: UiRenderPipeline?,
)
