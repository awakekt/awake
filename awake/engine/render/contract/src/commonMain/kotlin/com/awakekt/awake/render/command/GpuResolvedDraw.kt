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
    override val sortOrder: Int = 0,
    override val batchKey: Int = 0,
    override val instances: Int = 1,
    override val instanceVertexBuffer: BufferHandle? = null,
    override val jointPaletteBinding: MaterialBinding? = null,
    override val shadowBinding: MaterialBinding? = null,
    override val sceneDepthBinding: MaterialBinding? = null,
    override val instanceColorBuffer: BufferHandle? = null,
    override val instanceFrameBuffer: BufferHandle? = null,
    override val sortLayer: Int = 0,
    override val overCoverings: Boolean = false,
) : PreparedDraw {
    init {
        require(elementCount >= 0) { "A resolved draw element count must be non-negative." }
        require(instances >= 1) { "A resolved draw must have at least one instance." }
    }

    /** [GpuResolvedDraw] as it was before `overCoverings`: hidden, and kept so a consumer compiled against it still links. */
    @Suppress("LongParameterList")
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        pipeline: PipelineHandle,
        vertexFormat: VertexFormat? = null,
        depthPipeline: PipelineHandle? = null,
        depthMaterialBinding: MaterialBinding? = null,
        depthJointPaletteBinding: MaterialBinding? = null,
        depthRenderKey: com.awakekt.awake.render.pipeline.DepthRenderKey? = null,
        alphaCutoff: Float = 0.5f,
        materialBinding: MaterialBinding,
        vertexBuffer: BufferHandle?,
        indexBuffer: BufferHandle?,
        elementCount: Int,
        transparent: Boolean = false,
        depthSortKey: Float = 0f,
        sortOrder: Int = 0,
        batchKey: Int = 0,
        instances: Int = 1,
        instanceVertexBuffer: BufferHandle? = null,
        jointPaletteBinding: MaterialBinding? = null,
        shadowBinding: MaterialBinding? = null,
        sceneDepthBinding: MaterialBinding? = null,
        instanceColorBuffer: BufferHandle? = null,
        instanceFrameBuffer: BufferHandle? = null,
        sortLayer: Int = 0,
    ) : this(
        pipeline, vertexFormat, depthPipeline, depthMaterialBinding, depthJointPaletteBinding, depthRenderKey, alphaCutoff,
        materialBinding, vertexBuffer, indexBuffer, elementCount, transparent, depthSortKey, sortOrder, batchKey, instances,
        instanceVertexBuffer, jointPaletteBinding, shadowBinding, sceneDepthBinding, instanceColorBuffer, instanceFrameBuffer,
        sortLayer, overCoverings = false,
    )

    /** [copy] as it was before `overCoverings`: hidden, and kept so a consumer compiled against it still links. */
    @Suppress("LongParameterList")
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    fun copy(
        pipeline: PipelineHandle = this.pipeline,
        vertexFormat: VertexFormat? = this.vertexFormat,
        depthPipeline: PipelineHandle? = this.depthPipeline,
        depthMaterialBinding: MaterialBinding? = this.depthMaterialBinding,
        depthJointPaletteBinding: MaterialBinding? = this.depthJointPaletteBinding,
        depthRenderKey: com.awakekt.awake.render.pipeline.DepthRenderKey? = this.depthRenderKey,
        alphaCutoff: Float = this.alphaCutoff,
        materialBinding: MaterialBinding = this.materialBinding,
        vertexBuffer: BufferHandle? = this.vertexBuffer,
        indexBuffer: BufferHandle? = this.indexBuffer,
        elementCount: Int = this.elementCount,
        transparent: Boolean = this.transparent,
        depthSortKey: Float = this.depthSortKey,
        sortOrder: Int = this.sortOrder,
        batchKey: Int = this.batchKey,
        instances: Int = this.instances,
        instanceVertexBuffer: BufferHandle? = this.instanceVertexBuffer,
        jointPaletteBinding: MaterialBinding? = this.jointPaletteBinding,
        shadowBinding: MaterialBinding? = this.shadowBinding,
        sceneDepthBinding: MaterialBinding? = this.sceneDepthBinding,
        instanceColorBuffer: BufferHandle? = this.instanceColorBuffer,
        instanceFrameBuffer: BufferHandle? = this.instanceFrameBuffer,
        sortLayer: Int = this.sortLayer,
    ): GpuResolvedDraw = copy(
        pipeline, vertexFormat, depthPipeline, depthMaterialBinding, depthJointPaletteBinding, depthRenderKey, alphaCutoff,
        materialBinding, vertexBuffer, indexBuffer, elementCount, transparent, depthSortKey, sortOrder, batchKey, instances,
        instanceVertexBuffer, jointPaletteBinding, shadowBinding, sceneDepthBinding, instanceColorBuffer, instanceFrameBuffer,
        sortLayer, overCoverings = overCoverings,
    )
}
