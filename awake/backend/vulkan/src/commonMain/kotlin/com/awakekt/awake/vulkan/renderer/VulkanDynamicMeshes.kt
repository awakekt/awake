/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.vulkan.mesh.VulkanDynamicMesh

/**
 * The Vulkan [GpuDynamicMeshes]: a [VulkanDynamicMesh] with one persistently mapped vertex buffer
 * per frame in flight plus the dedicated offscreen slot (`maxFramesInFlight`), matching
 * [com.awakekt.awake.vulkan.material.Material] and [com.awakekt.awake.vulkan.debug.LineMesh], so
 * offscreen captures prepared on [Renderer.offscreenDrawPreparer] never collide with or exceed the
 * presented frame slots.
 */
internal class VulkanDynamicMeshes(private val renderer: Renderer) : GpuDynamicMeshes {
    override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh =
        renderer.trackDynamicMesh(
            VulkanDynamicMesh(renderer.graphicsDevice, format, vertexCapacity, indices, renderer.maxFramesInFlight + 1),
        )
}
