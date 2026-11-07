/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.mesh

import com.awakekt.awake.vulkan.pipeline.VulkanBufferBinding
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

/** What a scene draw binds and how many indices it submits, the same for a built mesh and a dynamic one. */
internal interface SceneMeshBuffers {
    /** The vertex buffer to bind. */
    val vertexBinding: VulkanBufferBinding

    /** The 32-bit index buffer to bind. */
    val indexBinding: VulkanBufferBinding

    /** How many indices a draw submits. */
    val indexCount: Int
}

/**
 * The buffers a scene draw of this mesh binds in frame slot [frameIndex]: a [VulkanDynamicMesh] brings
 * that slot up to date first. Allocates nothing.
 *
 * @throws ClassCastException If this mesh was not created by the Vulkan backend.
 */
internal fun RenderMesh.sceneBuffers(frameIndex: Int): SceneMeshBuffers = when (this) {
    is VulkanDynamicMesh -> apply { prepare(frameIndex) }
    else -> (this as Mesh).sceneBuffers
}
