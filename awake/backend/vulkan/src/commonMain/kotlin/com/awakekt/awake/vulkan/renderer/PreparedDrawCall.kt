/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.mesh.InstanceBuffer
import com.awakekt.awake.vulkan.mesh.SkinnedInstanceBuffer
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.vulkan.pipeline.VulkanMaterialBinding
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/**
 * One draw `prepareGpuDraw` prepared: also this backend's [PreparedDraw], whose members are computed
 * off the fields here. Every getter resolves to an object made when the resource was, such as a
 * material's uniform slot or a mesh's buffer binding, so reading one allocates nothing.
 *
 * Filled in place by [set], not built per draw. `VulkanDrawPreparer` keeps one and copies each draw
 * out into its `GpuResolvedDraw` before preparing the next: built per draw per frame, this was the
 * largest Vulkan-backend share of a populated frame's garbage (#1742).
 */
internal class PreparedDrawCall : PreparedDraw {
    lateinit var mesh: RenderMesh
        private set

    /** The mesh's vertex buffer this frame: a dynamic mesh's is its frame slot's. */
    override lateinit var vertexBuffer: VulkanBufferBinding
        private set
    override lateinit var indexBuffer: VulkanBufferBinding
        private set

    /** How many indices a draw submits: a dynamic mesh's drawn count, else every index. */
    override var elementCount: Int = 0
        private set
    var isTransparent: Boolean = false
        private set
    override lateinit var pipeline: RenderPipeline
        private set
    lateinit var material: Material
        private set
    var frameIndex: Int = 0
        private set
    var uniformSlotIndex: Int = 0
        private set

    /** The descriptor set `prepareGpuDraw` just wrote this draw's uniforms into. */
    override lateinit var materialBinding: VulkanMaterialBinding
        private set

    /** Non-null only for an instanced draw resolved to an instanced pipeline, which then binds it and
     * issues one `drawInstanced` instead of `draw`. */
    var instanceBuffer: InstanceBuffer? = null
        private set
    var instanceCount: Int = 0
        private set

    /** Non-null only for an ANIMATED instanced draw, where it accompanies [instanceBuffer]:
     * per-instance model matrices still come through that. */
    var jointPaletteBuffer: SkinnedInstanceBuffer? = null
        private set

    /** Non-null only for a tinted or particle instanced draw, bound at binding 2 beside [instanceBuffer]'s binding 1. */
    override var instanceColorBuffer: VulkanBufferBinding? = null
        private set

    /** Non-null only for a billboard-particle instanced draw, bound at binding 3 beside [instanceColorBuffer]'s binding 2. */
    override var instanceFrameBuffer: VulkanBufferBinding? = null
        private set

    /** Squared distance to the camera eye, computed once at preparation rather than inside the sort comparator. */
    override var depthSortKey: Float = 0f
        private set
    override var sortOrder: Int = 0
        private set
    override var sortLayer: Int = 0
        private set

    /** Replaces every field, so nothing of the draw prepared before survives into this one. */
    @Suppress("LongParameterList")
    fun set(
        mesh: RenderMesh,
        vertexBuffer: VulkanBufferBinding,
        indexBuffer: VulkanBufferBinding,
        elementCount: Int,
        isTransparent: Boolean,
        pipeline: RenderPipeline,
        material: Material,
        frameIndex: Int,
        uniformSlotIndex: Int,
        materialBinding: VulkanMaterialBinding,
        instanceBuffer: InstanceBuffer? = null,
        instanceCount: Int = 0,
        jointPaletteBuffer: SkinnedInstanceBuffer? = null,
        instanceColorBuffer: VulkanBufferBinding? = null,
        instanceFrameBuffer: VulkanBufferBinding? = null,
        depthSortKey: Float = 0f,
        sortOrder: Int = 0,
        sortLayer: Int = 0,
    ): PreparedDrawCall {
        this.mesh = mesh
        this.vertexBuffer = vertexBuffer
        this.indexBuffer = indexBuffer
        this.elementCount = elementCount
        this.isTransparent = isTransparent
        this.pipeline = pipeline
        this.material = material
        this.frameIndex = frameIndex
        this.uniformSlotIndex = uniformSlotIndex
        this.materialBinding = materialBinding
        this.instanceBuffer = instanceBuffer
        this.instanceCount = instanceCount
        this.jointPaletteBuffer = jointPaletteBuffer
        this.instanceColorBuffer = instanceColorBuffer
        this.instanceFrameBuffer = instanceFrameBuffer
        this.depthSortKey = depthSortKey
        this.sortOrder = sortOrder
        this.sortLayer = sortLayer
        return this
    }

    override val transparent: Boolean get() = isTransparent
    override val vertexFormat: VertexFormat get() = mesh.format

    /** Mesh identity: clusters draws sharing a mesh so consecutive calls reuse its vertex and index buffer bindings. */
    override val batchKey: Int get() = mesh.hashCode()

    /** [instanceCount] is 0 for a non-instanced draw; the port's count is the real one Vulkan issues, which is 1 there. */
    override val instances get() = if (instanceBuffer == null) 1 else instanceCount
    override val instanceVertexBuffer get() = instanceBuffer?.binding(frameIndex)
    override val jointPaletteBinding get() = jointPaletteBuffer?.binding(frameIndex)
    override val depthMaterialBinding get() = materialBinding
    override val depthJointPaletteBinding get() = jointPaletteBinding
}
