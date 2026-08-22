// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.pipeline

import io.github.ronjunevaldoz.awake.render.passes.RenderFrameContext
import io.github.ronjunevaldoz.awake.render.passes2d.UiRun
import io.github.ronjunevaldoz.awake.vulkan.debug.LineMesh
import io.github.ronjunevaldoz.awake.vulkan.renderer.PreparedDrawCall
import io.github.ronjunevaldoz.awake.vulkan.ui.DynamicMesh
import io.github.ronjunevaldoz.awake.vulkan.ui.UiRenderPipeline

/**
 * What a Vulkan feature sees on top of the shared [RenderFrameContext] -- still deliberately
 * narrow, and still carrying no `Renderer` *behavior*: nothing here can drive a frame, only read
 * what one already staged. `Renderer` supplies one of these per frame through the single adapter
 * that bridges the two (`RendererFrameContext`).
 *
 * The two draw-call members narrow the shared declarations to this backend's own types, which is
 * what lets a Vulkan feature reach `PreparedDrawCall`'s specifics while a shared feature body
 * still sees only `PreparedDraw`.
 */
internal interface VulkanRenderFrameContext : RenderFrameContext {
    val commandBuffer: Long

    override val groupedDrawCalls: Map<RenderPipeline, List<PreparedDrawCall>>
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
    val texture: UiRenderPipeline?,
    val roundedQuad: UiRenderPipeline?,
)
