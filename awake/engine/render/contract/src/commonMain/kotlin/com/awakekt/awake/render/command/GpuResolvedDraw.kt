/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
/**
 * Fully resolved draw input for the shared command recorder.
 *
 * Unlike the transitional source draw command owned by `render:passes`, this type contains no
 * mesh, material, camera or scene objects. Pipeline selection, uniform uploads and buffer views
 * have already happened in the compiler. Backends only lower these handles and parameters to
 * their native encoder.
 */
data class GpuResolvedDraw(
    override val pipeline: PipelineHandle,
    override val vertexFormat: VertexFormat? = null,
    override val depthPipeline: PipelineHandle? = null,
    override val depthMaterialBinding: MaterialBinding? = null,
    override val depthJointPaletteBinding: MaterialBinding? = null,
    override val depthRenderKey: com.awakekt.awake.render.pipeline.DepthRenderKey? = null,
    override val alphaCutoff: Float = 0.5f,
    override val materialBinding: MaterialBinding,
    override val vertexBuffer: BufferHandle?,
    override val indexBuffer: BufferHandle?,
    override val elementCount: Int,
    override val transparent: Boolean = false,
    override val depthSortKey: Float = 0f,
    override val batchKey: Int = 0,
    override val instances: Int = 1,
    override val instanceVertexBuffer: BufferHandle? = null,
    override val jointPaletteBinding: MaterialBinding? = null,
    override val shadowBinding: MaterialBinding? = null,
    override val sceneDepthBinding: MaterialBinding? = null,
    override val instanceColorBuffer: BufferHandle? = null,
    override val instanceFrameBuffer: BufferHandle? = null,
) : PreparedDraw {
    init {
        require(elementCount >= 0) { "A resolved draw element count must be non-negative." }
        require(instances >= 1) { "A resolved draw must have at least one instance." }
    }
}
