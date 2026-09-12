/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.vulkan.debug.LineMesh
import com.awakekt.awake.vulkan.ui.DynamicMesh
import com.awakekt.awake.vulkan.ui.UiRenderPipeline

/**
 * What a Vulkan feature sees on top of the shared [RenderFrameContext] -- still deliberately
 * narrow, and still carrying no `Renderer` *behavior*: nothing here can drive a frame, only read
 * what one already staged. `Renderer` supplies one of these per frame through the single adapter
 * that bridges the two ([VulkanFrameContext]).
 *
 * The two draw-call members narrow the shared declarations to this backend's own types, which is
 * what lets a Vulkan feature reach `PreparedDrawCall`'s specifics while a shared feature body
 * still sees only `PreparedDraw`.
 */
internal interface VulkanRenderFrameContext : RenderFrameContext {
    val commandBuffer: Long

    override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>>
    override val primaryPipeline: RenderPipeline

    /** Staged before the frame by `drawDebugLines`/`drawUi` respectively -- consumed here. */
    val lineMesh: LineMesh
    val uiRuns: List<UiRun<DynamicMesh>>

    /** This frame's UI pipeline set, or `null` when nothing has ever called `drawUi` (the quad
     * pipeline is built lazily there, and rebuilt on resize) -- a method, not a field, because
     * the underlying pipelines are mutable and must be read fresh on every frame. */
    fun uiPipelines(): UiPipelineSet?

    /** Stateful pooled `DynamicMesh` allocator -- see `Renderer.textureMeshForPrimitive`'s own
     * doc comment for the pooling contract this preserves. */
    fun textureMeshForPrimitive(index: Int): DynamicMesh
}

/** The lazily built UI pipelines, snapshotted for one feature call. [quad] is non-null by
 * construction (it is what "UI exists this frame" means); the other three stay nullable, each
 * built only once a frame actually contains that kind of primitive. */
internal class UiPipelineSet(
    val quad: UiRenderPipeline,
    val glyph: UiRenderPipeline?,
    val textures: Map<TextureCompositeMode, UiRenderPipeline>,
    val roundedQuad: UiRenderPipeline?,
)
