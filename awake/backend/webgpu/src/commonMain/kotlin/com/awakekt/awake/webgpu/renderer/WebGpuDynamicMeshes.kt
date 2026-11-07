/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.mesh.DynamicMesh
import com.awakekt.awake.render.mesh.GpuDynamicMeshes
import com.awakekt.awake.webgpu.mesh.WebGpuDynamicMesh

/** The WebGPU [GpuDynamicMeshes]: a [WebGpuDynamicMesh], one vertex buffer written on the queue. */
internal class WebGpuDynamicMeshes(private val renderer: Renderer) : GpuDynamicMeshes {
    override fun createDynamicMesh(format: VertexFormat, vertexCapacity: Int, indices: IntArray): DynamicMesh =
        WebGpuDynamicMesh(renderer.graphicsDevice, format, vertexCapacity, indices)
}
