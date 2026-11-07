/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.vulkan.mesh.PowerOfTwoFloats
import com.awakekt.awake.vulkan.mesh.VulkanDynamicMesh

/**
 * The Vulkan [GpuDynamicMeshes]: a [VulkanDynamicMesh] with one vertex buffer per frame in flight,
 * each brought up to date when a frame that draws it is prepared. The meshes share one set of
 * power-of-two upload windows.
 */
internal class VulkanDynamicMeshes(private val renderer: Renderer) : GpuDynamicMeshes {
    private val windows = PowerOfTwoFloats()

    override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh =
        VulkanDynamicMesh(renderer.graphicsDevice, format, vertexCapacity, indices, renderer.maxFramesInFlight, windows)
}
