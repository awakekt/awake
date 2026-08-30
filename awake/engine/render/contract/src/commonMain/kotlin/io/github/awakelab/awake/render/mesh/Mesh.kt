/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.mesh

import io.github.awakelab.awake.core.geometry.VertexFormat

/**
 * Module restructuring slice 1 (see docs/mvp-plan.md): the backend-neutral surface
 * `RenderSystem`/`DrawCall`/`Renderer.draw()` actually need across the module boundary --
 * deliberately narrow, not a 1:1 port of every Vulkan-backend `Mesh` member. Usage analysis
 * this session confirmed no caller outside `awake-vulkan` ever reads `vertexBuffer`/
 * `indexBuffer`/`indexCount`/etc. directly; only `bind()`/`draw()` are invoked generically
 * (by a `Renderer` implementation iterating `List<DrawCall>`). `awake-vulkan`'s real
 * `expect class Mesh` implements this interface (`expect class Mesh(...) :
 * io.github.awakelab.awake.render.mesh.Mesh`) -- an `expect` class can implement an
 * interface declared in a different module, so `VulkanApplication.kt`'s existing
 * `Mesh(graphicsDevice, ...)` construction pattern needs no changes.
 */
interface Mesh {
    /** The vertex layout this mesh's GPU buffer was built with -- lets a [Renderer]
     * implementation pick the correct pipeline for a [io.github.awakelab.awake.render
     * .renderer.DrawCall] by [format] instead of assuming every mesh shares the one pipeline
     * the renderer happens to have bound. Set once at [Renderer.createMesh] time from the
     * [MeshGeometry.format] the mesh was created from. */
    val format: VertexFormat

    /**
     * Bytes this mesh occupies on the GPU: its vertex buffer plus its index buffer.
     *
     * Added for `SceneAssetLibrary`'s retained-asset budget, which cannot bound what it cannot
     * measure. Not backend vocabulary -- both backends already compute these sizes to allocate
     * the buffers, so this reports a number each one already had rather than asking either to
     * learn something new.
     *
     * An estimate is acceptable and expected: driver padding and alignment mean the real
     * allocation is at least this, never less, which is the safe direction for a budget.
     */
    val sizeBytes: Long
    // No bind/draw here. Those took a raw `VkCommandBuffer` as a Long -- a Vulkan-shaped API in
    // the shared core tier, which WebGPU could only answer with TODO(). Recording is
    // `CommandRecorder`'s job, and Vulkan keeps its own bind/draw on its concrete Mesh, where
    // the handle type is honest. See docs/reference/render-hardware-interface.md.

    fun destroy()
}
